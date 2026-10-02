package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.usb.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Modular USB-C/OTG camera import screen.
 * Uses UsbPtpCamera for the protocol and routes finished files into the same
 * DirectTransferService import pipeline used by wireless handoff.
 */
public class UsbCameraActivity extends Activity {
    private static final String ACTION_USB_PERMISSION="com.nikonautoupload.USB_PERMISSION";
    private final int bg=Color.rgb(7,11,15),panel=Color.rgb(16,23,31),line=Color.rgb(42,54,66),white=Color.rgb(245,247,250),muted=Color.rgb(155,166,178),blue=Color.rgb(33,150,243),green=Color.rgb(55,206,108),amber=Color.rgb(255,193,7),red=Color.rgb(239,83,80);

    private UsbManager usb;
    private UsbDevice device;
    private UsbPtpCamera ptp;
    private SharedPreferences p;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,deviceInfo,summary;
    private LinearLayout photoList;
    private ProgressBar progress;
    private Switch autoImport;
    private final ArrayList<UsbPtpCamera.PhotoObject> photos=new ArrayList<>();
    private final HashSet<Integer> selectedHandles=new HashSet<>();
    private final HashSet<Integer> sessionKnownHandles=new HashSet<>();
    private boolean receiverRegistered=false;
    private boolean refreshing=false;
    private boolean firstConnectedRefresh=true;

    private final Runnable autoPoll=new Runnable(){@Override public void run(){
        if(ptp!=null&&ptp.isOpen()&&autoImport!=null&&autoImport.isChecked())refreshPhotos(true);
        handler.postDelayed(this,4000);
    }};

    private final BroadcastReceiver usbReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            String a=i.getAction();UsbDevice d;
            if(Build.VERSION.SDK_INT>=33)d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE,UsbDevice.class);else d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if(ACTION_USB_PERMISSION.equals(a)){
                if(d!=null&&i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)){device=d;connectDevice();}
                else setStatus("USB permission was not granted",true);
            }else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(a)&&d!=null&&device!=null&&d.getDeviceId()==device.getDeviceId()){
                disconnect("Camera unplugged");
            }else if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(a)&&d!=null&&UsbPtpCamera.isPtpDevice(d)){
                device=d;requestPermissionOrConnect();
            }
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);p=getSharedPreferences("settings",MODE_PRIVATE);usb=(UsbManager)getSystemService(USB_SERVICE);registerUsbReceiver();draw();
        if(!p.contains("usb_auto_import"))p.edit().putBoolean("usb_auto_import",false).apply();
        UsbDevice fromIntent=getUsbDevice(getIntent());if(fromIntent!=null)device=fromIntent;else device=findFirstCamera();
        if(device!=null)requestPermissionOrConnect();else setStatus("Plug a camera into the phone with USB-C / OTG",false);
        handler.postDelayed(autoPoll,4000);
    }

    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);UsbDevice d=getUsbDevice(i);if(d!=null){device=d;requestPermissionOrConnect();}}
    @Override protected void onDestroy(){handler.removeCallbacks(autoPoll);disconnect(null);if(receiverRegistered)try{unregisterReceiver(usbReceiver);}catch(Exception ignored){}io.shutdownNow();super.onDestroy();}

    private UsbDevice getUsbDevice(Intent i){if(i==null)return null;if(Build.VERSION.SDK_INT>=33)return i.getParcelableExtra(UsbManager.EXTRA_DEVICE,UsbDevice.class);return i.getParcelableExtra(UsbManager.EXTRA_DEVICE);}

    private void registerUsbReceiver(){IntentFilter f=new IntentFilter(ACTION_USB_PERMISSION);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);if(Build.VERSION.SDK_INT>=33)registerReceiver(usbReceiver,f,Context.RECEIVER_EXPORTED);else registerReceiver(usbReceiver,f);receiverRegistered=true;}

    private UsbDevice findFirstCamera(){if(usb==null)return null;for(UsbDevice d:usb.getDeviceList().values())if(UsbPtpCamera.isPtpDevice(d))return d;return null;}

    private void requestPermissionOrConnect(){
        if(device==null){setStatus("No USB camera detected",true);return;}
        updateDeviceText();
        if(usb.hasPermission(device)){connectDevice();return;}
        setStatus("Camera detected — asking Android for USB permission…",false);
        Intent intent=new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
        usb.requestPermission(device,PendingIntent.getBroadcast(this,71,intent,flags));
    }

    private void connectDevice(){
        final UsbDevice d=device;if(d==null)return;setStatus("Opening USB-C camera…",false);
        io.submit(()->{
            try{
                UsbPtpCamera camera=new UsbPtpCamera(usb);camera.open(d);ptp=camera;
                String name=cameraName(d);p.edit().putString("usb_state","Connected").putString("usb_camera_name",name).putString("usb_vid_pid",String.format(Locale.US,"%04X:%04X",d.getVendorId(),d.getProductId())).apply();
                runOnUiThread(()->{setStatus("USB-C camera connected",false);updateDeviceText();});
                refreshPhotos(false);
            }catch(Exception e){p.edit().putString("usb_state","Error: "+shortMsg(e)).apply();runOnUiThread(()->setStatus("USB connection failed: "+shortMsg(e),true));}
        });
    }

    private void disconnect(String message){
        UsbPtpCamera c=ptp;ptp=null;if(c!=null)try{c.close();}catch(Exception ignored){}
        p.edit().putString("usb_state",message==null?"Disconnected":message).apply();
        if(message!=null)runOnUiThread(()->{setStatus(message,false);photos.clear();selectedHandles.clear();renderPhotos();});
    }

    private void refreshPhotos(boolean fromAuto){
        if(refreshing||ptp==null||!ptp.isOpen())return;refreshing=true;
        io.submit(()->{
            try{
                List<UsbPtpCamera.PhotoObject> list=ptp.listPhotos();
                ArrayList<UsbPtpCamera.PhotoObject> autoNew=new ArrayList<>();
                synchronized(photos){photos.clear();photos.addAll(list);}
                if(firstConnectedRefresh){
                    sessionKnownHandles.clear();for(UsbPtpCamera.PhotoObject x:list)sessionKnownHandles.add(x.handle);firstConnectedRefresh=false;
                }else if(fromAuto&&p.getBoolean("usb_auto_import",false)){
                    for(UsbPtpCamera.PhotoObject x:list)if(!sessionKnownHandles.contains(x.handle)){sessionKnownHandles.add(x.handle);if(!wasImported(x))autoNew.add(x);}
                }else for(UsbPtpCamera.PhotoObject x:list)sessionKnownHandles.add(x.handle);
                runOnUiThread(()->{renderPhotos();setStatus("USB-C ready • "+list.size()+" photos found",false);});
                if(!autoNew.isEmpty())importObjects(autoNew,"Automatic USB import");
            }catch(Exception e){runOnUiThread(()->setStatus("Could not read camera photos: "+shortMsg(e),true));}
            finally{refreshing=false;}
        });
    }

    private void importSelected(){ArrayList<UsbPtpCamera.PhotoObject> list=new ArrayList<>();synchronized(photos){for(UsbPtpCamera.PhotoObject x:photos)if(selectedHandles.contains(x.handle))list.add(x);}if(list.isEmpty()){toast("Select at least one photo");return;}importObjects(list,"USB import");}
    private void importNew(){ArrayList<UsbPtpCamera.PhotoObject> list=new ArrayList<>();synchronized(photos){for(UsbPtpCamera.PhotoObject x:photos)if(!wasImported(x))list.add(x);}if(list.isEmpty()){toast("No new unimported photos found");return;}importObjects(list,"Import new photos");}

    private void importObjects(List<UsbPtpCamera.PhotoObject> list,String label){
        if(ptp==null||!ptp.isOpen()){runOnUiThread(()->setStatus("USB camera is not connected",true));return;}
        io.submit(()->{
            int totalFiles=list.size(),index=0;
            for(UsbPtpCamera.PhotoObject x:list){
                if(ptp==null||!ptp.isOpen())break;index++;final int fileIndex=index;
                try{
                    File dir=new File(getCacheDir(),"usb_import");if(!dir.exists())dir.mkdirs();String safe=safeName(x.name);File part=new File(dir,safe+".part"),done=new File(dir,safe);if(part.exists())part.delete();if(done.exists())done.delete();
                    runOnUiThread(()->{setStatus(label+" • "+fileIndex+"/"+totalFiles+" • "+x.name,false);progress.setProgress(0);progress.setIndeterminate(false);});
                    ptp.download(x,part,(bytes,max)->runOnUiThread(()->{int pct=max<=0?0:(int)Math.min(100,(bytes*100L)/max);progress.setProgress(pct);summary.setText("Transferring "+x.name+" • "+pct+"% • "+formatBytes(bytes)+" / "+formatBytes(max));}));
                    if(!part.renameTo(done)){copy(part,done);part.delete();}
                    markImported(x);sendIntoApp(done,x.name);p.edit().putString("usb_last_status","Imported "+x.name).putString("usb_last_photo",x.name).putLong("usb_last_photo_time",System.currentTimeMillis()).apply();
                    runOnUiThread(()->setStatus("USB transfer complete: "+x.name,false));
                }catch(Exception e){p.edit().putString("usb_last_status","Failed: "+shortMsg(e)).apply();runOnUiThread(()->setStatus("USB transfer failed for "+x.name+": "+shortMsg(e),true));}
            }
            runOnUiThread(()->{progress.setProgress(0);summary.setText("USB import finished");selectedHandles.clear();renderPhotos();});
        });
    }

    private void sendIntoApp(File file,String name){Intent i=new Intent(this,DirectTransferService.class);i.putExtra("command","import_file");i.putExtra("file_path",file.getAbsolutePath());i.putExtra("file_name",name);i.putExtra("source_label","USB-C");if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}

    private boolean wasImported(UsbPtpCamera.PhotoObject x){return p.getStringSet("usb_imported_signatures",Collections.emptySet()).contains(x.signature());}
    private synchronized void markImported(UsbPtpCamera.PhotoObject x){Set<String>s=new HashSet<>(p.getStringSet("usb_imported_signatures",Collections.emptySet()));s.add(x.signature());p.edit().putStringSet("usb_imported_signatures",s).apply();}

    private void deleteSelected(){
        ArrayList<UsbPtpCamera.PhotoObject> list=new ArrayList<>();synchronized(photos){for(UsbPtpCamera.PhotoObject x:photos)if(selectedHandles.contains(x.handle))list.add(x);}if(list.isEmpty()){toast("Select photos first");return;}
        new AlertDialog.Builder(this).setTitle("Delete from camera?").setMessage("This permanently deletes "+list.size()+" selected file(s) from the camera card. Nothing is deleted unless you confirm here.").setNegativeButton("Cancel",null).setPositiveButton("DELETE FROM CAMERA",(d,w)->io.submit(()->{
            for(UsbPtpCamera.PhotoObject x:list){try{if(ptp!=null)ptp.delete(x);}catch(Exception e){runOnUiThread(()->setStatus("Camera delete failed: "+shortMsg(e),true));break;}}
            selectedHandles.clear();runOnUiThread(()->refreshPhotos(false));
        })).show();
    }

    private void draw(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(14),dp(8),dp(14),dp(12));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=new Button(this);back.setText("‹");back.setTextSize(28);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(54),dp(52)));TextView title=text("USB-C Wired Camera",22,white,true);top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(top);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout info=card();info.addView(text("OFFLINE CAMERA IMPORT",13,blue,true));info.addView(text("Plug the camera directly into the phone with a data-capable USB-C cable. No Wi-Fi, hotspot, Bluetooth, or internet is required for importing.",13,muted,false));body.addView(info);
        status=text("Looking for a USB camera…",15,muted,true);status.setPadding(dp(12),dp(12),dp(12),dp(12));status.setBackgroundColor(panel);body.addView(status,new LinearLayout.LayoutParams(-1,dp(58)));
        deviceInfo=text("No camera connected",12,muted,false);deviceInfo.setPadding(dp(10),dp(8),dp(10),dp(8));body.addView(deviceInfo);

        Button scan=button("DETECT / RECONNECT USB CAMERA",blue);scan.setOnClickListener(v->{device=findFirstCamera();if(device!=null)requestPermissionOrConnect();else toast("No PTP/MTP camera detected");});body.addView(scan);
        autoImport=new Switch(this);autoImport.setText("Automatically import newly-shot photos while connected");autoImport.setTextColor(white);autoImport.setChecked(p.getBoolean("usb_auto_import",false));autoImport.setPadding(dp(8),dp(8),dp(8),dp(8));autoImport.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("usb_auto_import",on).apply());body.addView(autoImport,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);Button newest=button("IMPORT NEW",green);newest.setOnClickListener(v->importNew());actions.addView(newest,new LinearLayout.LayoutParams(0,dp(54),1));Button selected=button("IMPORT SELECTED",blue);selected.setOnClickListener(v->importSelected());actions.addView(selected,new LinearLayout.LayoutParams(0,dp(54),1));body.addView(actions);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(ColorStateList.valueOf(green));body.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));summary=text("Waiting",12,muted,false);summary.setPadding(dp(8),dp(6),dp(8),dp(8));body.addView(summary);

        LinearLayout selection=new LinearLayout(this);selection.setOrientation(LinearLayout.HORIZONTAL);Button all=button("SELECT ALL",Color.rgb(70,80,90));all.setOnClickListener(v->{synchronized(photos){for(UsbPtpCamera.PhotoObject x:photos)selectedHandles.add(x.handle);}renderPhotos();});selection.addView(all,new LinearLayout.LayoutParams(0,dp(50),1));Button clear=button("CLEAR",Color.rgb(70,80,90));clear.setOnClickListener(v->{selectedHandles.clear();renderPhotos();});selection.addView(clear,new LinearLayout.LayoutParams(0,dp(50),1));body.addView(selection);

        photoList=new LinearLayout(this);photoList.setOrientation(LinearLayout.VERTICAL);body.addView(photoList);
        Button del=button("DELETE SELECTED FROM CAMERA",red);del.setOnClickListener(v->deleteSelected());body.addView(del);
        setContentView(root);
        if(Build.VERSION.SDK_INT>=20){root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(14),dp(8),dp(14),Math.max(dp(12),insets.getSystemWindowInsetBottom()+dp(6)));return insets;});root.requestApplyInsets();}
    }

    private void renderPhotos(){if(photoList==null)return;photoList.removeAllViews();ArrayList<UsbPtpCamera.PhotoObject> list; synchronized(photos){list=new ArrayList<>(photos);}if(list.isEmpty()){TextView empty=text("No photos loaded yet.",13,muted,false);empty.setPadding(dp(10),dp(16),dp(10),dp(16));photoList.addView(empty);return;}int shown=0;for(UsbPtpCamera.PhotoObject x:list){if(shown++>=300)break;CheckBox cb=new CheckBox(this);cb.setTextColor(white);String kind=x.isRaw()?"RAW":"JPEG";String imported=wasImported(x)?" • already imported":"";cb.setText(x.name+"\n"+kind+" • "+formatBytes(x.size)+imported);cb.setChecked(selectedHandles.contains(x.handle));cb.setPadding(dp(8),dp(7),dp(8),dp(7));cb.setOnCheckedChangeListener((v,on)->{if(on)selectedHandles.add(x.handle);else selectedHandles.remove(x.handle);});photoList.addView(cb,new LinearLayout.LayoutParams(-1,dp(68)));}
        summary.setText(list.size()+" photo files available • "+selectedHandles.size()+" selected");}

    private void updateDeviceText(){if(deviceInfo==null)return;if(device==null){deviceInfo.setText("No camera connected");return;}String name=cameraName(device);deviceInfo.setText(name+"\nUSB VID:PID "+String.format(Locale.US,"%04X:%04X",device.getVendorId(),device.getProductId())+"\nConnection: direct USB-C / PTP • IP address not required");}
    private String cameraName(UsbDevice d){String maker="",product="";if(usb!=null&&usb.hasPermission(d)){try{maker=d.getManufacturerName();product=d.getProductName();}catch(Exception ignored){}}String n=((maker==null?"":maker)+" "+(product==null?"":product)).trim();if(n.isEmpty())n="USB PTP Camera";return n;}
    private void setStatus(String s,boolean error){runOnUiThread(()->{if(status!=null){status.setText(s);status.setTextColor(error?red:green);}p.edit().putString("usb_state",s).apply();});}
    private String safeName(String s){String x=s==null?"camera-photo":s.replaceAll("[\\\\/:*?\"<>|]","_").trim();return x.isEmpty()?"camera-photo":x;}
    private void copy(File a,File b)throws IOException{try(InputStream in=new FileInputStream(a);OutputStream out=new FileOutputStream(b)){byte[] buf=new byte[128*1024];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}}
    private String shortMsg(Throwable e){String s=e==null?"unknown error":e.getMessage();return s==null||s.trim().isEmpty()?e.getClass().getSimpleName():s;}
    private String formatBytes(long n){if(n<1024)return n+" B";double k=n/1024d;if(k<1024)return String.format(Locale.US,"%.1f KB",k);double m=k/1024d;if(m<1024)return String.format(Locale.US,"%.1f MB",m);return String.format(Locale.US,"%.1f GB",m/1024d);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(12),dp(14),dp(12));l.setBackgroundColor(panel);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));l.setLayoutParams(lp);return l;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setBackgroundTintList(ColorStateList.valueOf(color));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(dp(3),dp(4),dp(3),dp(4));b.setLayoutParams(lp);return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);t.setLineSpacing(0,1.08f);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
