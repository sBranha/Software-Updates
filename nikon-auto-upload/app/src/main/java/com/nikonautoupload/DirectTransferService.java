package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
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
    private WifiManager.LocalOnlyHotspotReservation reservation;
    private SimpleFtpServer ftp;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService retry=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean processing;
    private String mode="server";

    @Override public void onCreate(){
        super.onCreate();
        p=getSharedPreferences("settings",MODE_PRIVATE);
        ensureFtpPassword();
        createChannel();
        startForeground(71,new Notification.Builder(this,CH)
                .setSmallIcon(R.drawable.ic_camera)
                .setContentTitle("Nikon Auto Upload")
                .setContentText("Direct Z8 receiver is starting")
                .setOngoing(true).build());
        retry.scheduleWithFixedDelay(this::processPending,20,60,TimeUnit.SECONDS);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String requested=intent==null?"server":intent.getStringExtra("mode");
        if(requested==null)requested="server";
        mode=requested;
        stopTransportOnly();
        if("local".equals(mode)) startLocalHotspot(); else startServerOnly();
        return START_STICKY;
    }

    private void startLocalHotspot(){
        broadcast("Starting private camera Wi-Fi…",null,null,null);
        WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        try{
            wm.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback(){
                @Override public void onStarted(WifiManager.LocalOnlyHotspotReservation r){
                    reservation=r;
                    String ssid="", pass="";
                    try{
                        if(Build.VERSION.SDK_INT>=30){
                            SoftApConfiguration c=r.getSoftApConfiguration();
                            ssid=c.getSsid();
                            pass=c.getPassphrase();
                        }else{
                            WifiConfiguration c=r.getWifiConfiguration();
                            if(c!=null){ssid=stripQuotes(c.SSID);pass=stripQuotes(c.preSharedKey);}
                        }
                    }catch(Exception ignored){}
                    final String fs=ssid==null?"":ssid, fp=pass==null?"":pass;
                    io.submit(()->{
                        try{
                            Thread.sleep(700);
                            startFtp();
                            String ip=findBestLocalIp();
                            broadcast("Private camera Wi-Fi is ready",fs,fp,ip);
                        }catch(Exception e){
                            broadcast("FTP receiver error: "+e.getMessage(),fs,fp,findBestLocalIp());
                        }
                    });
                }
                @Override public void onStopped(){
                    broadcast("Private camera Wi-Fi stopped",null,null,null);
                }
                @Override public void onFailed(int reason){
                    broadcast("Could not start private hotspot (error "+reason+"). Try Phone Hotspot mode instead.",null,null,null);
                }
            },new Handler(Looper.getMainLooper()));
        }catch(SecurityException e){
            broadcast("Nearby Wi-Fi permission is required",null,null,null);
        }catch(Exception e){
            broadcast("Hotspot error: "+e.getMessage(),null,null,null);
        }
    }

    private void startServerOnly(){
        io.submit(()->{
            try{
                startFtp();
                broadcast("FTP receiver ready. Connect the Z8 to this phone's hotspot.",null,null,findBestLocalIp());
            }catch(Exception e){broadcast("FTP receiver error: "+e.getMessage(),null,null,null);}
        });
    }

    private synchronized void startFtp() throws IOException {
        if(ftp!=null)return;
        File temp=new File(getCacheDir(),"nikon_ftp");
        ftp=new SimpleFtpServer(FTP_PORT,"nikon",p.getString("ftp_password","nikon2026"),temp,this);
        ftp.start();
    }

    @Override public void onPhotoReceived(File file,String originalName){
        io.submit(()->{
            try{
                Uri uri=saveToGallery(file,originalName);
                addPending(uri);
                file.delete();
                notifyEvent("Photo received from Nikon Z8",originalName);
                broadcast("Received "+originalName+" — queued for Flickr",null,null,findBestLocalIp());
                processPending();
            }catch(Exception e){
                notifyEvent("Photo receive error",e.getMessage());
            }
        });
    }

    @Override public void onStatus(String text){
        broadcast(text,null,null,findBestLocalIp());
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
        if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}
        return u;
    }

    private synchronized void addPending(Uri u){
        Set<String>s=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));
        s.add(u.toString());p.edit().putStringSet("pending_uploads",s).apply();
    }

    private synchronized void removePending(String u){
        Set<String>s=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));
        s.remove(u);p.edit().putStringSet("pending_uploads",s).apply();
    }

    private void processPending(){
        if(processing)return;
        if(p.getString("access_token","").isEmpty())return;
        processing=true;
        try{
            Set<String> items=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));
            for(String s:items){
                try{
                    Uri u=Uri.parse(s);
                    String name=displayName(u);
                    new FlickrClient(this).upload(u,name,p.getString("tags","nikon z8"),p.getBoolean("public",true));
                    removePending(s);
                    notifyEvent("Uploaded to Flickr",name);
                    broadcast("Uploaded "+name+" to Flickr using the phone's internet connection",null,null,findBestLocalIp());
                }catch(Exception e){
                    broadcast("Photo is saved on phone; Flickr upload will retry automatically",null,null,findBestLocalIp());
                    break;
                }
            }
        }finally{processing=false;}
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

    private void broadcast(String status,String ssid,String wifiPass,String ip){
        Intent i=new Intent(ACTION_STATUS).setPackage(getPackageName());
        i.putExtra("status",status);
        i.putExtra("mode",mode);
        i.putExtra("ftp_user","nikon");
        i.putExtra("ftp_pass",p.getString("ftp_password",""));
        i.putExtra("ftp_port",FTP_PORT);
        if(ssid!=null)i.putExtra("ssid",ssid);
        if(wifiPass!=null)i.putExtra("wifi_pass",wifiPass);
        if(ip!=null)i.putExtra("ip",ip);
        sendBroadcast(i);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(71,new Notification.Builder(this,CH).setSmallIcon(R.drawable.ic_camera).setContentTitle("Nikon Auto Upload").setContentText(status).setOngoing(true).build());
    }

    private String findBestLocalIp(){
        String fallback="";
        try{
            Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
            while(en.hasMoreElements()){
                NetworkInterface ni=en.nextElement();
                String nn=ni.getName().toLowerCase(Locale.US);
                Enumeration<InetAddress> aa=ni.getInetAddresses();
                while(aa.hasMoreElements()){
                    InetAddress a=aa.nextElement();
                    if(!(a instanceof Inet4Address)||a.isLoopbackAddress()||!a.isSiteLocalAddress())continue;
                    String h=a.getHostAddress();
                    if(nn.contains("wlan")||nn.contains("ap")||nn.contains("swlan")||nn.contains("wifi"))return h;
                    if(fallback.isEmpty()&&!nn.contains("rmnet")&&!nn.contains("ccmni"))fallback=h;
                }
            }
        }catch(Exception ignored){}
        return fallback.isEmpty()?"(IP appears after hotspot starts)":fallback;
    }

    private static String stripQuotes(String s){if(s==null)return "";if(s.length()>1&&s.startsWith("\"")&&s.endsWith("\""))return s.substring(1,s.length()-1);return s;}

    private void notifyEvent(String title,String text){
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(System.currentTimeMillis()%100000),new Notification.Builder(this,CH).setSmallIcon(R.drawable.ic_camera).setContentTitle(title).setContentText(text==null?"":text).setAutoCancel(true).build());
    }

    private void createChannel(){
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CH,"Direct Nikon transfer",NotificationManager.IMPORTANCE_LOW));
    }

    private synchronized void stopTransportOnly(){
        if(ftp!=null){ftp.stop();ftp=null;}
        if(reservation!=null){try{reservation.close();}catch(Exception ignored){}reservation=null;}
    }

    @Override public void onDestroy(){
        stopTransportOnly();
        retry.shutdownNow();io.shutdownNow();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
