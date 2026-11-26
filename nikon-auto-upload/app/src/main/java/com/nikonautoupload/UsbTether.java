package com.nikonautoupload;

import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;

import java.io.*;
import java.util.*;

/**
 * Automatic wired tethering, owned by DirectTransferService so it keeps running with the screen off
 * and without any screen of the app being open.
 *
 *   camera plugged in  ->  USB permission (asked once; "always" when opened via the plug-in prompt)
 *   -> PTP session  ->  every photo shot from then on is pulled at once and handed to the normal
 *   save / Flickr pipeline (same path as FTP and the manual USB screen).
 *
 * New photos are found two ways so a burst can't be missed: the camera's ObjectAdded events on the
 * interrupt pipe, and a fast handle-list diff as a safety net (some bodies don't send events).
 * Handles are queued, never dropped; each is retried a few times before it is given up.
 */
final class UsbTether {
    interface Host {
        void onTetherPhoto(File file,String originalName);
        void onTetherEvent(String text);
        void onTetherState(boolean connected);
    }

    static final String ACTION_PERMISSION="com.nikonautoupload.USB_TETHER_PERMISSION";
    private static final int NIKON_VENDOR_ID=0x04B0;
    private static final long POLL_MS=1000;

    private final Context ctx;
    private final SharedPreferences p;
    private final Host host;
    private final UsbManager usb;

    private volatile boolean started,paused;
    private volatile Thread worker;
    private volatile UsbDevice current;
    private volatile UsbPtpCamera ptp;
    private volatile boolean connected;
    private String permissionAskedFor="";
    private boolean receiverRegistered;

    UsbTether(Context ctx,SharedPreferences p,Host host){
        this.ctx=ctx;this.p=p;this.host=host;this.usb=(UsbManager)ctx.getSystemService(Context.USB_SERVICE);
    }

    boolean isConnected(){return connected;}

    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            String a=i.getAction();UsbDevice d=device(i);
            if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(a)){
                UsbDevice cur=current;
                if(cur!=null&&(d==null||d.getDeviceName().equals(cur.getDeviceName())))stopWorker("Camera unplugged");
                else if(findCamera()==null)p.edit().putString("usb_state","").apply();
                permissionAskedFor="";
            }else if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(a)){
                permissionAskedFor="";check();
            }else if(ACTION_PERMISSION.equals(a)){
                if(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))check();
                else{setState("USB access was not allowed — tap DETECT on the USB-C screen to ask again");host.onTetherEvent("USB access to the camera was declined");}
            }
        }
    };

    synchronized void start(){
        if(started)return;started=true;
        p.edit().putString("usb_state","").putBoolean("usb_tether_active",false).apply();
        IntentFilter f=new IntentFilter(ACTION_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        try{
            if(Build.VERSION.SDK_INT>=33)ctx.registerReceiver(receiver,f,Context.RECEIVER_EXPORTED);else ctx.registerReceiver(receiver,f);
            receiverRegistered=true;
        }catch(Exception ignored){}
        check();
    }

    synchronized void shutdown(){
        started=false;
        if(receiverRegistered){try{ctx.unregisterReceiver(receiver);}catch(Exception ignored){}receiverRegistered=false;}
        stopWorker(null);
    }

    /** The manual USB screen takes the camera over while it is open. */
    void setPaused(boolean pause){
        paused=pause;
        if(pause)stopWorker(null);else{permissionAskedFor="";check();}
    }

    /** Looks for a camera and connects (or asks for permission). Safe to call often. */
    synchronized void check(){
        if(!started||paused||usb==null)return;
        if(worker!=null&&worker.isAlive())return;
        UsbDevice d=findCamera();
        recordSeenDevices();
        if(d==null){if(!connected)p.edit().putString("usb_state","").apply();return;}
        if(!usb.hasPermission(d)){
            if(!d.getDeviceName().equals(permissionAskedFor)){
                permissionAskedFor=d.getDeviceName();
                setState("Camera found on USB-C — tap ALLOW on the phone");
                host.onTetherEvent("USB camera detected ("+vidPid(d)+") — asking for access");
                try{
                    Intent intent=new Intent(ACTION_PERMISSION).setPackage(ctx.getPackageName());
                    int flags=Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0;
                    usb.requestPermission(d,PendingIntent.getBroadcast(ctx,73,intent,flags|PendingIntent.FLAG_UPDATE_CURRENT));
                }catch(Exception e){host.onTetherEvent("Could not ask for USB access: "+e.getMessage());}
            }
            return;
        }
        current=d;
        Thread t=new Thread(()->run(d),"NAU-usb-tether");t.setDaemon(true);worker=t;t.start();
    }

    private void stopWorker(String reason){
        Thread t=worker;worker=null;
        UsbPtpCamera c=ptp;ptp=null;
        if(t!=null)t.interrupt();
        // close() waits for an in-progress transfer; never block the caller (service main thread) on it.
        if(c!=null)new Thread(()->{try{c.close();}catch(Exception ignored){}p.edit().putBoolean("usb_tether_active",false).apply();},"NAU-usb-close").start();
        current=null;
        if(connected||reason!=null){
            connected=false;host.onTetherState(false);
            p.edit().putString("usb_state","").apply();
            if(c==null)p.edit().putBoolean("usb_tether_active",false).apply();
            AppStatusHub.clearCameraReport(p);AppStatusHub.clearTransfer(p);
            if(reason!=null)host.onTetherEvent(reason);
        }
    }

    // ------------------------------------------------------------------ worker

    private void run(UsbDevice d){
        Thread self=Thread.currentThread();
        int openFailures=0;
        // Kept across session reopens: a photo queued (or shot) during a hiccup is still imported.
        TetherState st=new TetherState();
        while(worker==self&&started&&!paused){
            UsbPtpCamera camera=new UsbPtpCamera(usb);
            try{
                camera.open(d);ptp=camera;openFailures=0;
                String name=cameraName(d);
                p.edit().putString("usb_camera_name",name).putString("usb_vid_pid",vidPid(d)).putBoolean("usb_tether_active",true).apply();
                setState("USB-C connected • auto-import on");
                AppStatusHub.reportCamera(p,name,"USB-C",true);AppStatusHub.reportTransfer(p,"Idle","",0,0,0);
                connected=true;host.onTetherState(true);
                host.onTetherEvent(name+" connected by USB-C — new photos import automatically");
                tether(camera,self,st);
            }catch(Exception e){
                if(worker!=self)break;
                openFailures++;
                setState("USB-C camera not responding — retrying ("+shortMsg(e)+")");
                if(openFailures==1||openFailures%10==0)host.onTetherEvent("USB-C camera: "+shortMsg(e));
            }finally{
                if(ptp==camera)ptp=null;
                try{camera.close();}catch(Exception ignored){}
                if(connected&&worker==self){connected=false;host.onTetherState(false);AppStatusHub.clearTransfer(p);}
            }
            if(worker!=self||!stillAttached(d))break;
            try{Thread.sleep(Math.min(10000,1500L*Math.max(1,openFailures)));}catch(InterruptedException ie){break;}
        }
        if(worker==self){worker=null;current=null;if(!stillAttached(d)){p.edit().putString("usb_state","").putBoolean("usb_tether_active",false).apply();AppStatusHub.clearCameraReport(p);}}
    }

    private static final class TetherState {
        final HashSet<Integer> seen=new HashSet<>();
        final ArrayDeque<Integer> queue=new ArrayDeque<>();
        final HashMap<Integer,Integer> attempts=new HashMap<>();
        boolean snapshotDone;
    }

    private void tether(UsbPtpCamera camera,Thread self,TetherState st) throws Exception {
        boolean raw=p.getBoolean("usb_import_raw",false);
        HashSet<Integer> seen=st.seen;ArrayDeque<Integer> queue=st.queue;HashMap<Integer,Integer> attempts=st.attempts;
        if(!st.snapshotDone){
            // Photos already on the card when the cable goes in are left alone (tethering = new shots),
            // unless "also import photos already on the card" is on. Already-imported files are never repeated.
            boolean catchUp=p.getBoolean("usb_import_existing",false);
            ArrayList<Integer> all=new ArrayList<>(camera.listHandles());Collections.sort(all,Integer::compareUnsigned);
            for(int h:all){seen.add(h);if(catchUp)queue.add(h);}
            st.snapshotDone=true;
        }
        long nextPoll=0;
        while(worker==self&&!paused&&camera.isOpen()){
            if(queue.isEmpty()){
                int[] ev=camera.readEvent(250);
                if(ev!=null&&(ev[0]==UsbPtpCamera.EVENT_OBJECT_ADDED)&&ev[1]!=0&&seen.add(ev[1]))queue.add(ev[1]);
                if(ev!=null)nextPoll=0; // any event (capture complete, store change) -> re-list right away
            }
            long now=SystemClock.elapsedRealtime();
            if(now>=nextPoll){
                nextPoll=now+POLL_MS;
                ArrayList<Integer> fresh=new ArrayList<>();
                for(int h:camera.listHandles())if(seen.add(h))fresh.add(h);
                Collections.sort(fresh,Integer::compareUnsigned); // oldest shot first
                queue.addAll(fresh);
            }
            Integer h=queue.peek();
            if(h==null)continue;
            try{
                UsbPtpCamera.PhotoObject x=camera.photoInfo(h);
                queue.poll();
                if(x==null)continue;                         // folder, movie, etc.
                if(x.isRaw()&&!raw)continue;                 // Flickr takes JPEG; RAW stays on the card
                if(wasImported(x))continue;
                download(camera,x,queue.size()+1);
            }catch(IOException e){
                if(!camera.isOpen()||!stillAttached(current))throw e;
                int n=attempts.containsKey(h)?attempts.get(h)+1:1;attempts.put(h,n);
                if(n>=3){queue.poll();host.onTetherEvent("USB import gave up on one photo after 3 tries: "+shortMsg(e));}
                else{queue.poll();queue.add(h);}             // retry later, don't block the burst behind it
                // A failed transfer can leave the PTP stream out of step; reopen the session.
                throw e;
            }
        }
    }

    private void download(UsbPtpCamera camera,UsbPtpCamera.PhotoObject x,int remaining) throws IOException {
        File dir=new File(ctx.getFilesDir(),"usb_import");if(!dir.exists())dir.mkdirs();
        String unique=UUID.randomUUID().toString()+"__"+safeName(x.name);
        File part=new File(dir,unique+".part"),done=new File(dir,unique);
        AppStatusHub.reportTransfer(p,"Transferring",x.name,0,1,remaining);
        final long[] mark={SystemClock.elapsedRealtime(),0};
        camera.download(x,part,(bytes,max)->{
            long now=SystemClock.elapsedRealtime(),dt=now-mark[0];
            if(dt>=500){long bps=(Math.max(0,bytes-mark[1])*1000L)/Math.max(1,dt);mark[0]=now;mark[1]=bytes;AppStatusHub.reportTransfer(p,"Transferring",x.name,bps,1,remaining);}
        });
        if(x.size>0&&x.size!=0xffffffffL&&part.length()!=x.size){part.delete();throw new IOException("Size mismatch ("+part.length()+" of "+x.size+" bytes)");}
        if(!part.renameTo(done)){part.delete();throw new IOException("Could not stage "+x.name);}
        markImported(x);
        p.edit().putString("usb_last_status","Imported "+x.name).putString("usb_last_photo",x.name).putLong("usb_last_photo_time",System.currentTimeMillis()).apply();
        AppStatusHub.reportTransfer(p,"Complete",x.name,0,1,1);
        host.onTetherPhoto(done,x.name);
    }

    // ------------------------------------------------------------------ helpers

    UsbDevice findCamera(){
        if(usb==null)return null;
        UsbDevice nikon=null;
        for(UsbDevice d:usb.getDeviceList().values()){
            if(UsbPtpCamera.isPtpDevice(d))return d;
            if(d.getVendorId()==NIKON_VENDOR_ID)nikon=d;
        }
        return nikon;
    }

    private void recordSeenDevices(){
        StringBuilder b=new StringBuilder();
        if(usb!=null)for(UsbDevice d:usb.getDeviceList().values()){
            if(b.length()>0)b.append("; ");
            b.append(vidPid(d)).append(" class ").append(d.getDeviceClass());
            for(int i=0;i<d.getInterfaceCount();i++)b.append(i==0?" if ":",").append(d.getInterface(i).getInterfaceClass());
        }
        p.edit().putString("usb_devices_seen",b.length()==0?"none":b.toString()).apply();
    }

    private boolean stillAttached(UsbDevice d){
        if(d==null||usb==null)return false;
        for(UsbDevice x:usb.getDeviceList().values())if(x.getDeviceName().equals(d.getDeviceName()))return true;
        return false;
    }

    private boolean wasImported(UsbPtpCamera.PhotoObject x){Set<String>s=p.getStringSet("usb_imported_signatures",Collections.emptySet());return s.contains(x.signature())||s.contains(x.legacySignature());}
    private static final Object MARK_LOCK=new Object();
    private void markImported(UsbPtpCamera.PhotoObject x){
        synchronized(MARK_LOCK){
            Set<String>s=new LinkedHashSet<>(p.getStringSet("usb_imported_signatures",Collections.emptySet()));
            s.add(x.signature());
            // Keep the list bounded for long seasons of shooting.
            if(s.size()>20000){Iterator<String> it=s.iterator();while(s.size()>15000&&it.hasNext()){it.next();it.remove();}}
            p.edit().putStringSet("usb_imported_signatures",s).apply();
        }
    }

    private void setState(String s){p.edit().putString("usb_state",s).apply();}
    private String cameraName(UsbDevice d){String m="",n="";try{m=d.getManufacturerName();n=d.getProductName();}catch(Exception ignored){}String r=((m==null?"":m)+" "+(n==null?"":n)).trim();return r.isEmpty()?"USB camera":r;}
    private static String vidPid(UsbDevice d){return String.format(Locale.US,"%04X:%04X",d.getVendorId(),d.getProductId());}
    private static String safeName(String s){String x=s==null?"camera-photo":s.replaceAll("[\\\\/:*?\"<>|]","_").trim();return x.isEmpty()?"camera-photo":x;}
    private static String shortMsg(Throwable e){String s=e==null?null:e.getMessage();return s==null||s.trim().isEmpty()?(e==null?"error":e.getClass().getSimpleName()):s;}
    private static UsbDevice device(Intent i){if(Build.VERSION.SDK_INT>=33)return i.getParcelableExtra(UsbManager.EXTRA_DEVICE,UsbDevice.class);return i.getParcelableExtra(UsbManager.EXTRA_DEVICE);}
}
