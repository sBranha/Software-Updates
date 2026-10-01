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
import java.util.*;
import java.util.concurrent.*;

public class DirectTransferService extends Service implements SimpleFtpServer.Listener {
    public static final String ACTION_STATUS="com.nikonautoupload.DIRECT_STATUS";
    public static final String CH="nikon_direct";
    public static final int FTP_PORT=2121;

    private SharedPreferences p;
    private SimpleFtpServer ftp;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService retry=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean processing;
    private volatile Network cellularNetwork;
    private volatile boolean cellularRequestActive;
    private String cellularState="checking cellular data";
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback cellularCallback;
    private String mode="z8ap";

    @Override public void onCreate(){
        super.onCreate();
        p=getSharedPreferences("settings",MODE_PRIVATE);
        ensureFtpPassword();
        createChannel();
        cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        startForeground(71,new Notification.Builder(this,CH)
                .setSmallIcon(R.drawable.ic_camera)
                .setContentTitle("Nikon Auto Upload")
                .setContentText("Z8 direct receiver is starting")
                .setOngoing(true).build());
        requestCellular();
        retry.scheduleWithFixedDelay(()->{
            if(cellularNetwork==null&&!cellularRequestActive)requestCellular();
            processPending();
            broadcast("Z8 receiver running",findBestLocalIp());
        },20,30,TimeUnit.SECONDS);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String requested=intent==null?"z8ap":intent.getStringExtra("mode");
        mode=requested==null?"z8ap":requested;
        startServerOnly();
        return START_STICKY;
    }

    private void startServerOnly(){
        io.submit(()->{
            try{
                startFtp();
                broadcast("FTP receiver ready — join the Z8 Wi-Fi and continue the camera wizard",findBestLocalIp());
            }catch(Exception e){
                broadcast("FTP receiver error: "+e.getMessage(),findBestLocalIp());
            }
        });
    }

    private synchronized void startFtp() throws IOException {
        if(ftp!=null)return;
        File temp=new File(getCacheDir(),"nikon_ftp");
        ftp=new SimpleFtpServer(FTP_PORT,"nikon",p.getString("ftp_password","nikon2026"),temp,this);
        ftp.start();
    }

    private synchronized void requestCellular(){
        if(cm==null||cellularRequestActive||cellularNetwork!=null)return;
        cellularRequestActive=true;
        cellularState="requesting 5G/LTE";
        broadcast("Requesting cellular data for Flickr…",findBestLocalIp());
        NetworkRequest req=new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cellularCallback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network network){
                cellularNetwork=network;
                cellularRequestActive=false;
                cellularState="5G/LTE ready";
                broadcast("Cellular Flickr path ready — Z8 Wi-Fi can stay connected",findBestLocalIp());
                io.submit(DirectTransferService.this::processPending);
            }
            @Override public void onLost(Network network){
                if(network.equals(cellularNetwork))cellularNetwork=null;
                cellularRequestActive=false;
                cellularState="cellular temporarily unavailable";
                broadcast("Cellular data lost — photos will stay queued",findBestLocalIp());
            }
            @Override public void onUnavailable(){
                cellularNetwork=null;
                cellularRequestActive=false;
                cellularState="cellular not ready — check Mobile data";
                broadcast("Cellular data not ready — photos will stay queued",findBestLocalIp());
            }
        };
        try{
            cm.requestNetwork(req,cellularCallback,30000);
        }catch(Exception e){
            cellularRequestActive=false;
            cellularState="cellular request failed";
            broadcast("Could not request cellular data: "+e.getMessage(),findBestLocalIp());
        }
    }

    @Override public void onPhotoReceived(File file,String originalName){
        io.submit(()->{
            try{
                Uri uri=saveToGallery(file,originalName);
                addPending(uri);
                file.delete();
                notifyEvent("Photo received from Nikon Z8",originalName);
                broadcast("Received "+originalName+" — saved on phone and queued for Flickr",findBestLocalIp());
                processPending();
            }catch(Exception e){
                notifyEvent("Photo receive error",e.getMessage());
                broadcast("Photo receive error: "+e.getMessage(),findBestLocalIp());
            }
        });
    }

    @Override public void onStatus(String text){
        broadcast(text,findBestLocalIp());
    }

    private Uri saveToGallery(File file,String name) throws Exception {
        String low=name.toLowerCase(Locale.US);
        String mime=(low.endsWith(".jpeg")||low.endsWith(".jpg"))?"image/jpeg":"application/octet-stream";
        ContentValues v=new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME,name);
        v.put(MediaStore.Images.Media.MIME_TYPE,mime);
        if(Build.VERSION.SDK_INT>=29){
            v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Nikon Auto Upload");
            v.put(MediaStore.Images.Media.IS_PENDING,1);
        }
        Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
        if(u==null)throw new IOException("Could not create phone photo");
        try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(u,"w")){
            if(out==null)throw new IOException("Could not open phone photo");
            byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }
        if(Build.VERSION.SDK_INT>=29){
            ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);
        }
        return u;
    }

    private synchronized void addPending(Uri u){
        Set<String>s=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));
        s.add(u.toString());
        p.edit().putStringSet("pending_uploads",s).apply();
    }

    private synchronized void removePending(String u){
        Set<String>s=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));
        s.remove(u);
        p.edit().putStringSet("pending_uploads",s).apply();
    }

    private void processPending(){
        if(processing)return;
        if(p.getString("access_token","").isEmpty()){
            broadcast("Flickr is not connected — photo uploads will stay queued",findBestLocalIp());
            return;
        }
        Network cell=cellularNetwork;
        if(cell==null){
            if(!cellularRequestActive)requestCellular();
            return;
        }
        processing=true;
        try{
            Set<String> items=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));
            for(String s:items){
                try{
                    Uri u=Uri.parse(s);
                    String name=displayName(u);
                    new FlickrClient(this).upload(u,name,p.getString("tags","nikon z8"),p.getBoolean("public",true),cell);
                    removePending(s);
                    notifyEvent("Uploaded to Flickr",name);
                    broadcast("Uploaded "+name+" to Flickr over cellular data",findBestLocalIp());
                }catch(Exception e){
                    broadcast("Photo is saved; Flickr upload will retry over cellular",findBestLocalIp());
                    break;
                }
            }
        }finally{
            processing=false;
        }
    }

    private String displayName(Uri u){
        try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return "Nikon Z8 photo";
    }

    private void ensureFtpPassword(){
        if(p.getString("ftp_password","").isEmpty()){
            int n=10000000+new java.security.SecureRandom().nextInt(90000000);
            p.edit().putString("ftp_password",String.valueOf(n)).apply();
        }
    }

    private void broadcast(String status,String ip){
        Intent i=new Intent(ACTION_STATUS).setPackage(getPackageName());
        i.putExtra("status",status);
        i.putExtra("mode",mode);
        i.putExtra("ftp_user","nikon");
        i.putExtra("ftp_pass",p.getString("ftp_password",""));
        i.putExtra("ftp_port",FTP_PORT);
        i.putExtra("cellular",cellularState);
        if(ip!=null)i.putExtra("ip",ip);
        sendBroadcast(i);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(71,new Notification.Builder(this,CH)
                .setSmallIcon(R.drawable.ic_camera)
                .setContentTitle("Nikon Auto Upload")
                .setContentText(status)
                .setOngoing(true).build());
    }

    private String findBestLocalIp(){
        try{
            Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
            while(en.hasMoreElements()){
                NetworkInterface ni=en.nextElement();
                String nn=ni.getName().toLowerCase(Locale.US);
                if(!(nn.contains("wlan")||nn.contains("wifi")))continue;
                Enumeration<InetAddress> aa=ni.getInetAddresses();
                while(aa.hasMoreElements()){
                    InetAddress a=aa.nextElement();
                    if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())return a.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return "waiting for Z8 Wi-Fi";
    }

    private void notifyEvent(String title,String text){
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(System.currentTimeMillis()%100000),new Notification.Builder(this,CH)
                .setSmallIcon(R.drawable.ic_camera).setContentTitle(title).setContentText(text==null?"":text).setAutoCancel(true).build());
    }

    private void createChannel(){
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CH,"Direct Nikon transfer",NotificationManager.IMPORTANCE_LOW));
    }

    @Override public void onDestroy(){
        if(ftp!=null){ftp.stop();ftp=null;}
        if(cm!=null&&cellularCallback!=null){try{cm.unregisterNetworkCallback(cellularCallback);}catch(Exception ignored){}}
        retry.shutdownNow();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){return null;}
}
