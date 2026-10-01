package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.content.ContentValues;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class DirectTransferService extends Service implements SimpleFtpServer.Listener {
    public static final String ACTION_STATUS="com.nikonautoupload.DIRECT_STATUS";
    public static final String CH="nikon_direct";
    public static final int FTP_PORT=2121;
    public static final String FTP_USER="nikon";
    public static final String DEFAULT_FTP_PASSWORD="47250558";

    private SharedPreferences p;
    private SimpleFtpServer ftp;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService retry=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean processing;
    private volatile Network cellularNetwork;
    private volatile boolean cellularRequestActive;
    private String cellularState="not needed — Flickr uploads off";
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback cellularCallback;
    private String mode="z8ap";

    @Override public void onCreate(){
        super.onCreate();
        p=getSharedPreferences("settings",MODE_PRIVATE);
        migrateStableSettings();
        createChannel();
        p.edit().putBoolean("receiver_running",true).putString("ftp_state","Waiting for Z8").apply();
        cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        startForeground(71,mainNotification("Z8 direct receiver is starting"));
        if(flickrEnabled()) requestCellular();
        retry.scheduleWithFixedDelay(()->{
            if(flickrEnabled()){
                if(cellularNetwork==null&&!cellularRequestActive)requestCellular();
                processPending();
            }
            String ip=findBestLocalIp();
            p.edit().putString("last_ip",ip).apply();
        },20,30,TimeUnit.SECONDS);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String requested=intent==null?"z8ap":intent.getStringExtra("mode");
        mode=requested==null?"z8ap":requested;
        startServerOnly();
        if(flickrEnabled()){
            if(cellularNetwork==null&&!cellularRequestActive)requestCellular();
            io.submit(this::processPending);
        }else{
            cellularState="not needed — Flickr uploads off";
            p.edit().putString("cellular_state",cellularState).apply();
        }
        return START_STICKY;
    }

    private void migrateStableSettings(){
        SharedPreferences.Editor e=p.edit();
        if(!p.getBoolean("ftp_fixed_password_v031",false)){
            e.putString("ftp_password",DEFAULT_FTP_PASSWORD);
            e.putBoolean("ftp_fixed_password_v031",true);
        }else if(p.getString("ftp_password","").trim().isEmpty()){
            e.putString("ftp_password",DEFAULT_FTP_PASSWORD);
        }
        if(!p.contains("flickr_upload_enabled"))e.putBoolean("flickr_upload_enabled",true);
        e.apply();
    }

    private boolean flickrEnabled(){return p.getBoolean("flickr_upload_enabled",true);}

    private void startServerOnly(){
        io.submit(()->{
            try{
                startFtp();
                broadcast("FTP receiver ready — waiting for Nikon Z8",findBestLocalIp());
            }catch(Exception e){
                p.edit().putString("ftp_state","Receiver error").apply();
                broadcast("FTP receiver error: "+e.getMessage(),findBestLocalIp());
            }
        });
    }

    private synchronized void startFtp() throws IOException {
        if(ftp!=null)return;
        File temp=new File(getCacheDir(),"nikon_ftp");
        ftp=new SimpleFtpServer(FTP_PORT,FTP_USER,p.getString("ftp_password",DEFAULT_FTP_PASSWORD),temp,this);
        ftp.start();
    }

    private synchronized void requestCellular(){
        if(!flickrEnabled())return;
        if(cm==null||cellularRequestActive||cellularNetwork!=null)return;
        cellularRequestActive=true;
        cellularState="requesting 5G/LTE";
        p.edit().putString("cellular_state",cellularState).apply();
        broadcast("Requesting cellular data for Flickr…",findBestLocalIp());
        NetworkRequest req=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        cellularCallback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network network){
                cellularNetwork=network;cellularRequestActive=false;cellularState="5G/LTE ready";
                p.edit().putString("cellular_state",cellularState).apply();
                addEvent("Cellular Flickr path ready");
                broadcast("Cellular Flickr path ready — Z8 Wi-Fi can stay connected",findBestLocalIp());
                io.submit(DirectTransferService.this::processPending);
            }
            @Override public void onLost(Network network){
                if(network.equals(cellularNetwork))cellularNetwork=null;
                cellularRequestActive=false;cellularState="cellular temporarily unavailable";
                p.edit().putString("cellular_state",cellularState).apply();
                if(flickrEnabled())addEvent("Cellular lost — uploads queued");
                broadcast(flickrEnabled()?"Cellular data lost — photos will stay queued":"Flickr uploads off — photos still save to phone",findBestLocalIp());
            }
            @Override public void onUnavailable(){
                cellularNetwork=null;cellularRequestActive=false;cellularState="cellular not ready — check Mobile data";
                p.edit().putString("cellular_state",cellularState).apply();
                if(flickrEnabled())broadcast("Cellular data not ready — photos will stay queued",findBestLocalIp());
            }
        };
        try{cm.requestNetwork(req,cellularCallback,30000);}
        catch(Exception e){cellularRequestActive=false;cellularState="cellular request failed";p.edit().putString("cellular_state",cellularState).apply();broadcast("Could not request cellular data: "+e.getMessage(),findBestLocalIp());}
    }

    @Override public void onPhotoReceived(File file,String originalName){
        io.submit(()->{
            try{
                Uri uri=saveToGallery(file,originalName);
                boolean send=flickrEnabled();
                if(send)addPending(uri);
                file.delete();
                long now=System.currentTimeMillis();
                p.edit().putInt("total_received",p.getInt("total_received",0)+1).putString("last_received_name",originalName).putLong("last_received_time",now).putString("ftp_state","Photo received").apply();
                addEvent(send?"Received from Z8: "+originalName+" — queued for Flickr":"Received from Z8: "+originalName+" — phone only");
                notifyEvent("Photo received from Nikon Z8",originalName);
                if(send){
                    broadcast("Received "+originalName+" — saved on phone and queued for Flickr",findBestLocalIp());
                    processPending();
                }else{
                    broadcast("Received "+originalName+" — saved on phone; Flickr upload is OFF",findBestLocalIp());
                }
            }catch(Exception e){
                notifyEvent("Photo receive error",e.getMessage());addEvent("Receive error: "+e.getMessage());broadcast("Photo receive error: "+e.getMessage(),findBestLocalIp());
            }
        });
    }

    @Override public void onStatus(String text){
        String low=text==null?"":text.toLowerCase(Locale.US);
        if(low.contains("tcp connected")){p.edit().putString("ftp_state","Z8 connected").apply();addEvent("Z8 connected to FTP receiver");}
        else if(low.contains("login accepted")){p.edit().putString("ftp_state","Z8 logged in").apply();addEvent("Z8 FTP login accepted");}
        else if(low.contains("receiving from z8"))p.edit().putString("ftp_state","Receiving photo").apply();
        else if(low.contains("transfer complete"))p.edit().putString("ftp_state","Transfer complete").apply();
        else if(low.contains("waiting for z8"))p.edit().putString("ftp_state","Waiting for Z8").apply();
        else if(low.contains("login rejected")||low.contains("session ended")){p.edit().putString("ftp_state",text).apply();addEvent(text);}
        broadcast(text,findBestLocalIp());
    }

    private Uri saveToGallery(File file,String name) throws Exception {
        String low=name.toLowerCase(Locale.US);
        String mime=(low.endsWith(".jpeg")||low.endsWith(".jpg"))?"image/jpeg":"application/octet-stream";
        ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,mime);
        if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Nikon Auto Upload");v.put(MediaStore.Images.Media.IS_PENDING,1);}
        Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Could not create phone photo");
        try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Could not open phone photo");byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}return u;
    }

    private synchronized void addPending(Uri u){Set<String>s=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));s.add(u.toString());p.edit().putStringSet("pending_uploads",s).apply();}
    private synchronized void removePending(String u){Set<String>s=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));s.remove(u);p.edit().putStringSet("pending_uploads",s).apply();}

    private void processPending(){
        if(processing||!flickrEnabled())return;
        Set<String> items=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));
        if(items.isEmpty())return;
        if(p.getString("access_token","").isEmpty()){broadcast("Flickr is not connected — pending photos are safely saved",findBestLocalIp());return;}
        Network cell=cellularNetwork;if(cell==null){if(!cellularRequestActive)requestCellular();return;}
        processing=true;
        try{
            for(String s:items){
                if(!flickrEnabled())break;
                try{
                    Uri u=Uri.parse(s);String name=displayName(u);broadcast("Uploading "+name+" to Flickr over cellular…",findBestLocalIp());
                    new FlickrClient(this).upload(u,name,p.getString("tags","nikon z8"),p.getBoolean("public",true),cell);
                    removePending(s);long now=System.currentTimeMillis();
                    p.edit().putInt("total_uploaded",p.getInt("total_uploaded",0)+1).putString("last_uploaded_name",name).putLong("last_uploaded_time",now).apply();
                    addEvent("Uploaded to Flickr: "+name);notifyEvent("Uploaded to Flickr",name);broadcast("Uploaded "+name+" to Flickr over cellular data",findBestLocalIp());
                }catch(Exception e){addEvent("Flickr retry queued: "+shortMessage(e));broadcast("Photo is saved; Flickr upload will retry over cellular",findBestLocalIp());break;}
            }
        }finally{processing=false;}
    }

    private String displayName(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "Nikon Z8 photo";}

    private void broadcast(String status,String ip){
        if(status==null)status="Receiver running";
        if(!flickrEnabled())cellularState="not needed — Flickr uploads off";
        SharedPreferences.Editor ed=p.edit().putString("last_status",status).putBoolean("receiver_running",true).putString("cellular_state",cellularState);if(ip!=null)ed.putString("last_ip",ip);ed.apply();
        Intent i=new Intent(ACTION_STATUS).setPackage(getPackageName());i.putExtra("status",status);i.putExtra("mode",mode);i.putExtra("ftp_user",FTP_USER);i.putExtra("ftp_pass",p.getString("ftp_password",DEFAULT_FTP_PASSWORD));i.putExtra("ftp_port",FTP_PORT);i.putExtra("cellular",cellularState);if(ip!=null)i.putExtra("ip",ip);sendBroadcast(i);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(71,mainNotification(status));
    }

    private String findBestLocalIp(){
        try{Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();while(en.hasMoreElements()){NetworkInterface ni=en.nextElement();String nn=ni.getName().toLowerCase(Locale.US);if(!(nn.contains("wlan")||nn.contains("wifi")))continue;Enumeration<InetAddress> aa=ni.getInetAddresses();while(aa.hasMoreElements()){InetAddress a=aa.nextElement();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())return a.getHostAddress();}}}catch(Exception ignored){}return "waiting for Z8 Wi-Fi";
    }

    private synchronized void addEvent(String text){if(text==null||text.trim().isEmpty())return;String stamp=new SimpleDateFormat("h:mm:ss a",Locale.US).format(new Date());String old=p.getString("event_log","");String combined=stamp+"  "+text.trim()+(old.isEmpty()?"":"\n"+old);String[] lines=combined.split("\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(lines.length,24);i++){if(i>0)b.append('\n');b.append(lines[i]);}p.edit().putString("event_log",b.toString()).apply();}
    private String shortMessage(Exception e){String s=e.getMessage();if(s==null||s.isEmpty())s=e.getClass().getSimpleName();return s.length()>90?s.substring(0,90):s;}
    private Notification mainNotification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,CH).setSmallIcon(R.drawable.ic_camera).setContentTitle("Nikon Auto Upload").setContentText(text).setContentIntent(pi).setOngoing(true).build();}
    private void notifyEvent(String title,String text){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(System.currentTimeMillis()%100000),new Notification.Builder(this,CH).setSmallIcon(R.drawable.ic_camera).setContentTitle(title).setContentText(text==null?"":text).setAutoCancel(true).build());}
    private void createChannel(){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CH,"Direct Nikon transfer",NotificationManager.IMPORTANCE_LOW));}

    @Override public void onDestroy(){
        if(ftp!=null){ftp.stop();ftp=null;}
        if(cm!=null&&cellularCallback!=null){try{cm.unregisterNetworkCallback(cellularCallback);}catch(Exception ignored){}}
        p.edit().putBoolean("receiver_running",false).putString("ftp_state","Receiver stopped").putString("last_status","Receiver stopped").apply();
        retry.shutdownNow();io.shutdownNow();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
