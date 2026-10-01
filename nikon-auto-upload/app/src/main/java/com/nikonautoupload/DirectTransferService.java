package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.*;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
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
        super.onCreate();p=getSharedPreferences("settings",MODE_PRIVATE);migrateStableSettings();createChannel();cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        p.edit().putBoolean("receiver_running",true).putString("ftp_state",p.getBoolean("receiver_paused",false)?"Receiver paused":"Waiting for Z8").apply();
        startForeground(71,mainNotification("Z8 direct receiver is starting"));
        if(flickrEnabled())requestCellular();
        retry.scheduleWithFixedDelay(()->{
            if(!p.getBoolean("receiver_paused",false)&&ftp==null)startServerOnly();
            if(flickrEnabled()){if(cellularNetwork==null&&!cellularRequestActive)requestCellular();processPending();}
            runCleanupIfNeeded();String ip=findBestLocalIp();p.edit().putString("last_ip",ip).apply();
        },20,30,TimeUnit.SECONDS);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String requested=intent==null?"z8ap":intent.getStringExtra("mode");mode=requested==null?"z8ap":requested;
        String cmd=intent==null?null:intent.getStringExtra("command");
        if("pause_receiver".equals(cmd)){pauseReceiver();return START_STICKY;}
        if("resume_receiver".equals(cmd)){resumeReceiver();return START_STICKY;}
        if("retry_failed".equals(cmd)){if(flickrEnabled()){requestCellular();io.submit(this::processPending);}return START_STICKY;}
        if(!p.getBoolean("receiver_paused",false))startServerOnly();
        if(flickrEnabled()){if(cellularNetwork==null&&!cellularRequestActive)requestCellular();io.submit(this::processPending);}else{releaseCellular();cellularState="not needed — Flickr uploads off";p.edit().putString("cellular_state",cellularState).apply();}
        return START_STICKY;
    }

    private void migrateStableSettings(){
        SharedPreferences.Editor e=p.edit();
        if(!p.getBoolean("ftp_fixed_password_v031",false)){e.putString("ftp_password",DEFAULT_FTP_PASSWORD);e.putBoolean("ftp_fixed_password_v031",true);}else if(p.getString("ftp_password","").trim().isEmpty())e.putString("ftp_password",DEFAULT_FTP_PASSWORD);
        if(!p.contains("flickr_upload_enabled"))e.putBoolean("flickr_upload_enabled",true);
        if(!p.contains("daily_albums_enabled"))e.putBoolean("daily_albums_enabled",true);
        if(!p.contains("album_max_photos"))e.putInt("album_max_photos",999);
        if(!p.contains("album_suffix"))e.putString("album_suffix","");
        if(!p.contains("duplicate_protection"))e.putBoolean("duplicate_protection",true);
        if(!p.contains("auto_delete_after_upload"))e.putBoolean("auto_delete_after_upload",false);
        if(!p.contains("cleanup_keep_days"))e.putInt("cleanup_keep_days",0);
        if(!p.contains("receiver_paused"))e.putBoolean("receiver_paused",false);
        e.apply();
    }

    private boolean flickrEnabled(){return p.getBoolean("flickr_upload_enabled",true);}
    private boolean dailyAlbumsEnabled(){return p.getBoolean("daily_albums_enabled",true);}

    private void startServerOnly(){
        if(p.getBoolean("receiver_paused",false))return;
        io.submit(()->{try{startFtp();broadcast("FTP receiver ready — waiting for Nikon Z8",findBestLocalIp());}catch(Exception e){p.edit().putString("ftp_state","Receiver error").apply();broadcast("FTP receiver error: "+shortMessage(e),findBestLocalIp());}});
    }

    private synchronized void startFtp() throws IOException {
        if(ftp!=null||p.getBoolean("receiver_paused",false))return;
        File temp=new File(getCacheDir(),"nikon_ftp");ftp=new SimpleFtpServer(FTP_PORT,FTP_USER,p.getString("ftp_password",DEFAULT_FTP_PASSWORD),temp,this);ftp.start();
    }

    private synchronized void pauseReceiver(){
        p.edit().putBoolean("receiver_paused",true).putString("ftp_state","Receiver paused").apply();
        if(ftp!=null){ftp.stop();ftp=null;}broadcast("Z8 receiving paused — Flickr retries can continue",findBestLocalIp());
    }
    private synchronized void resumeReceiver(){
        p.edit().putBoolean("receiver_paused",false).putString("ftp_state","Waiting for Z8").apply();startServerOnly();broadcast("Z8 receiving resumed",findBestLocalIp());
    }

    private synchronized void requestCellular(){
        if(!flickrEnabled()||cm==null||cellularRequestActive||cellularNetwork!=null)return;
        cellularRequestActive=true;cellularState="requesting 5G/LTE";p.edit().putString("cellular_state",cellularState).apply();
        NetworkRequest req=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        cellularCallback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network network){cellularNetwork=network;cellularRequestActive=false;cellularState="5G/LTE ready";p.edit().putString("cellular_state",cellularState).apply();addEvent("Cellular Flickr path ready");broadcast("Cellular Flickr path ready — Z8 Wi-Fi can stay connected",findBestLocalIp());io.submit(DirectTransferService.this::processPending);}
            @Override public void onLost(Network network){if(network.equals(cellularNetwork))cellularNetwork=null;cellularRequestActive=false;cellularState="cellular temporarily unavailable";p.edit().putString("cellular_state",cellularState).apply();if(flickrEnabled())addEvent("Cellular lost — uploads queued");broadcast(flickrEnabled()?"Cellular data lost — photos will stay queued":"Flickr uploads off — photos still save to phone",findBestLocalIp());}
            @Override public void onUnavailable(){cellularNetwork=null;cellularRequestActive=false;cellularState="cellular not ready — check Mobile data";p.edit().putString("cellular_state",cellularState).apply();if(flickrEnabled())broadcast("Cellular data not ready — photos will stay queued",findBestLocalIp());}
        };
        try{cm.requestNetwork(req,cellularCallback,30000);}catch(Exception e){cellularRequestActive=false;cellularState="cellular request failed";p.edit().putString("cellular_state",cellularState).apply();broadcast("Could not request cellular data: "+shortMessage(e),findBestLocalIp());}
    }

    private synchronized void releaseCellular(){if(cm!=null&&cellularCallback!=null){try{cm.unregisterNetworkCallback(cellularCallback);}catch(Exception ignored){}}cellularCallback=null;cellularNetwork=null;cellularRequestActive=false;}

    @Override public void onPhotoReceived(File file,String originalName){
        io.submit(()->{
            try{
                if(p.getBoolean("receiver_paused",false)){file.delete();broadcast("Z8 receiving is paused",findBestLocalIp());return;}
                String hash=sha256(file);
                if(p.getBoolean("duplicate_protection",true)&&isKnownHash(hash)){file.delete();addEvent("Duplicate ignored: "+originalName);broadcast("Duplicate Z8 photo ignored: "+originalName,findBestLocalIp());return;}
                Uri uri=saveToGallery(file,originalName);rememberHash(hash);file.delete();
                boolean send=flickrEnabled();String uriText=uri.toString();
                if(send){addPending(uri);queueAlbumBase(uriText,FlickrClient.albumBaseTitle(System.currentTimeMillis(),effectiveSuffix()));}else addToSet("phone_only_uris",uriText);
                if(p.getBoolean("session_active",false)){addToSet("current_session_uris",uriText);mapSessionUri(uriText,p.getString("session_name",""));p.edit().putInt("session_received",p.getInt("session_received",0)+1).apply();}
                long now=System.currentTimeMillis();p.edit().putInt("total_received",p.getInt("total_received",0)+1).putString("last_received_name",originalName).putLong("last_received_time",now).putString("ftp_state","Photo received").apply();
                addEvent(send?"Received from Z8: "+originalName+" — queued for Flickr":"Received from Z8: "+originalName+" — phone only");notifyEvent("Photo received from Nikon Z8",originalName);
                if(send){broadcast("Received "+originalName+" — saved on phone and queued for Flickr",findBestLocalIp());processPending();}else broadcast("Received "+originalName+" — saved on phone; Flickr upload is OFF",findBestLocalIp());
            }catch(Exception e){notifyEvent("Photo receive error",shortMessage(e));addEvent("Receive error: "+shortMessage(e));broadcast("Photo receive error: "+shortMessage(e),findBestLocalIp());}
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
        String low=name.toLowerCase(Locale.US),mime=(low.endsWith(".jpeg")||low.endsWith(".jpg"))?"image/jpeg":"application/octet-stream";
        ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,mime);
        if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Nikon Auto Upload");v.put(MediaStore.Images.Media.IS_PENDING,1);}
        Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Could not create phone photo");
        try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Could not open phone photo");byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}return u;
    }

    private synchronized void addPending(Uri u){addToSet("pending_uploads",u.toString());}
    private synchronized void removePending(String u){removeFromSet("pending_uploads",u);removeAlbumBase(u);}
    private synchronized void addPendingAlbum(String photoId,String baseTitle,String uri,String name,String privacy){Set<String>s=new HashSet<>(p.getStringSet("pending_album_assignments",Collections.emptySet()));removePendingAlbumForUriFromSet(s,uri);s.add(photoId+"\u001f"+baseTitle+"\u001f"+uri+"\u001f"+name+"\u001f"+privacy);p.edit().putStringSet("pending_album_assignments",s).apply();}
    private synchronized void removePendingAlbum(String item){removeFromSet("pending_album_assignments",item);}
    private void removePendingAlbumForUriFromSet(Set<String>s,String uri){Iterator<String>it=s.iterator();while(it.hasNext()){String x=it.next();String[]q=x.split("\u001f",5);if(q.length>=3&&q[2].equals(uri))it.remove();}}
    private boolean hasPendingAlbumForUri(String uri){for(String x:p.getStringSet("pending_album_assignments",Collections.emptySet())){String[]q=x.split("\u001f",5);if(q.length>=3&&q[2].equals(uri))return true;}return false;}

    private void processPending(){
        if(processing||!flickrEnabled())return;
        Set<String> items=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));Set<String> albumItems=new HashSet<>(p.getStringSet("pending_album_assignments",Collections.emptySet()));
        if(items.isEmpty()&&albumItems.isEmpty())return;if(p.getString("access_token","").isEmpty()){broadcast("Flickr is not connected — pending photos are safely saved",findBestLocalIp());return;}
        Network cell=cellularNetwork;if(cell==null){if(!cellularRequestActive)requestCellular();return;}processing=true;
        try{
            processPendingAlbums(cell);
            items=new HashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));
            for(String s:items){
                if(!flickrEnabled())break;if(hasPendingAlbumForUri(s))continue;
                Uri u=Uri.parse(s);if(!uriExists(u)){removePending(s);removeFromSet("failed_upload_uris",s);addEvent("Skipped missing phone file");continue;}
                String name=displayName(u);boolean isPublic=p.getBoolean("public",true);String privacy=isPublic?"PUBLIC":"PRIVATE";
                try{
                    broadcast("Uploading "+name+" to Flickr • "+privacy+"…",findBestLocalIp());FlickrClient client=new FlickrClient(this);
                    String photoId=client.upload(u,name,p.getString("tags","nikon z8"),isPublic,cell);client.verifyPhoto(photoId,cell);
                    if(dailyAlbumsEnabled()){
                        String base=albumBaseForUri(s);if(base.isEmpty())base=FlickrClient.albumBaseTitle(photoDate(u),effectiveSuffix());
                        addPendingAlbum(photoId,base,s,name,privacy);
                        try{FlickrClient.AlbumResult ar=client.addToDailyAlbum(photoId,base,albumLimit(),cell);removePendingAlbumForUri(s);p.edit().putString("last_album_name",ar.title).putInt("last_album_count",ar.count).putString("last_album_status","Verified in album").apply();finalizeUpload(s,name,privacy,ar.title);}
                        catch(Exception albumError){p.edit().putString("last_album_status","Waiting to file: "+shortMessage(albumError)).apply();addEvent("Album filing queued: "+shortMessage(albumError));broadcast("Photo uploaded; album filing will retry",findBestLocalIp());}
                    }else finalizeUpload(s,name,privacy,"");
                }catch(Exception e){addToSet("failed_upload_uris",s);addEvent("Flickr retry queued: "+shortMessage(e));broadcast("Photo is saved; Flickr upload will retry over cellular",findBestLocalIp());break;}
            }
            processPendingAlbums(cell);
        }finally{processing=false;}
    }

    private void processPendingAlbums(Network cell){
        Set<String> queued=new HashSet<>(p.getStringSet("pending_album_assignments",Collections.emptySet()));if(queued.isEmpty())return;FlickrClient client=new FlickrClient(this);
        for(String item:queued){
            String[] q=item.split("\u001f",5);if(q.length<2){removePendingAlbum(item);continue;}
            String photoId=q[0],base=q[1],uri=q.length>=3?q[2]:"",name=q.length>=4?q[3]:"Nikon Z8 photo",privacy=q.length>=5?q[4]:(p.getBoolean("public",true)?"PUBLIC":"PRIVATE");
            try{
                String album="";
                if(dailyAlbumsEnabled()){FlickrClient.AlbumResult ar=client.addToDailyAlbum(photoId,base,albumLimit(),cell);album=ar.title;p.edit().putString("last_album_name",ar.title).putInt("last_album_count",ar.count).putString("last_album_status","Verified in album").apply();}
                removePendingAlbum(item);if(!uri.isEmpty())finalizeUpload(uri,name,privacy,album);
            }catch(Exception e){p.edit().putString("last_album_status","Waiting to file: "+shortMessage(e)).apply();break;}
        }
    }

    private synchronized void removePendingAlbumForUri(String uri){Set<String>s=new HashSet<>(p.getStringSet("pending_album_assignments",Collections.emptySet()));removePendingAlbumForUriFromSet(s,uri);p.edit().putStringSet("pending_album_assignments",s).apply();}

    private void finalizeUpload(String uriText,String name,String privacy,String album){
        boolean first=!p.getStringSet("uploaded_uris",Collections.emptySet()).contains(uriText);removePending(uriText);removeFromSet("failed_upload_uris",uriText);removeFromSet("phone_only_uris",uriText);addToSet("uploaded_uris",uriText);
        long now=System.currentTimeMillis();SharedPreferences.Editor e=p.edit().putString("last_uploaded_name",name).putLong("last_uploaded_time",now).putString("last_upload_privacy",privacy);
        if(first){e.putInt("total_uploaded",p.getInt("total_uploaded",0)+1);if(p.getBoolean("session_active",false)||isCurrentSessionUri(uriText))e.putInt("session_uploaded",p.getInt("session_uploaded",0)+1);}e.apply();
        if(!album.isEmpty()){addSessionAlbum(album);addEvent("Verified in Flickr album: "+album);}
        addCompleted("✓ "+name+" • "+privacy+(album.isEmpty()?"":" • "+album));
        String detail=name+" • "+privacy+(album.isEmpty()?"":" • "+album);addEvent("Upload verified: "+detail);notifyEvent("Uploaded to Flickr",detail);broadcast("Verified on Flickr: "+detail,findBestLocalIp());
        if(p.getBoolean("auto_delete_after_upload",false))deletePhoneUri(Uri.parse(uriText),"Auto-deleted phone copy after verified upload");
    }

    private int albumLimit(){return Math.max(1,Math.min(999,p.getInt("album_max_photos",999)));}
    private String effectiveSuffix(){if(p.getBoolean("session_active",false)){String s=cleanSuffix(p.getString("session_name",""));if(!s.isEmpty())return s;}return cleanSuffix(p.getString("album_suffix",""));}
    private String cleanSuffix(String s){return s==null?"":s.replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}

    private synchronized void queueAlbumBase(String uri,String base){Set<String>s=new HashSet<>(p.getStringSet("pending_album_bases",Collections.emptySet()));Iterator<String>it=s.iterator();while(it.hasNext())if(it.next().startsWith(uri+"\u001f"))it.remove();s.add(uri+"\u001f"+base);p.edit().putStringSet("pending_album_bases",s).apply();}
    private String albumBaseForUri(String uri){for(String x:p.getStringSet("pending_album_bases",Collections.emptySet())){String[]q=x.split("\u001f",2);if(q.length==2&&q[0].equals(uri))return q[1];}return "";}
    private synchronized void removeAlbumBase(String uri){Set<String>s=new HashSet<>(p.getStringSet("pending_album_bases",Collections.emptySet()));Iterator<String>it=s.iterator();while(it.hasNext())if(it.next().startsWith(uri+"\u001f"))it.remove();p.edit().putStringSet("pending_album_bases",s).apply();}

    private synchronized void mapSessionUri(String uri,String session){if(session==null||session.trim().isEmpty())return;Set<String>s=new HashSet<>(p.getStringSet("session_uri_map",Collections.emptySet()));s.add(uri+"\u001f"+session.trim());p.edit().putStringSet("session_uri_map",s).apply();}
    private boolean isCurrentSessionUri(String uri){for(String x:p.getStringSet("current_session_uris",Collections.emptySet()))if(x.equals(uri))return true;return false;}
    private synchronized void addSessionAlbum(String album){if(album==null||album.isEmpty())return;Set<String>s=new HashSet<>(p.getStringSet("session_album_names",Collections.emptySet()));int before=s.size();s.add(album);p.edit().putStringSet("session_album_names",s).putInt("session_albums",s.size()).apply();}

    private void runCleanupIfNeeded(){
        int days=p.getInt("cleanup_keep_days",0);if(days<=0)return;long now=System.currentTimeMillis(),last=p.getLong("last_cleanup_ms",0);if(now-last<6*60*60*1000L)return;p.edit().putLong("last_cleanup_ms",now).apply();
        long cutoff=now-days*24L*60L*60L*1000L;for(String s:new HashSet<>(p.getStringSet("uploaded_uris",Collections.emptySet()))){try{Uri u=Uri.parse(s);if(uriExists(u)&&photoDate(u)<cutoff)deletePhoneUri(u,"Auto-cleaned old uploaded phone copy");}catch(Exception ignored){}}
    }

    private void deletePhoneUri(Uri u,String event){try{int n=getContentResolver().delete(u,null,null);if(n>0){addEvent(event);removeFromSet("favorite_uris",u.toString());}}catch(Exception e){addEvent("Could not delete phone copy automatically: "+shortMessage(e));}}

    private String sha256(File f) throws Exception {MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[]b=new byte[128*1024];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();}
    private boolean isKnownHash(String h){return h!=null&&!h.isEmpty()&&p.getStringSet("known_hashes",Collections.emptySet()).contains(h);}
    private synchronized void rememberHash(String h){if(h==null||h.isEmpty())return;Set<String>s=new HashSet<>(p.getStringSet("known_hashes",Collections.emptySet()));s.add(h);while(s.size()>5000){Iterator<String>it=s.iterator();if(it.hasNext()){it.next();it.remove();}else break;}p.edit().putStringSet("known_hashes",s).apply();}

    private synchronized void addToSet(String key,String value){Set<String>s=new HashSet<>(p.getStringSet(key,Collections.emptySet()));s.add(value);p.edit().putStringSet(key,s).apply();}
    private synchronized void removeFromSet(String key,String value){Set<String>s=new HashSet<>(p.getStringSet(key,Collections.emptySet()));s.remove(value);p.edit().putStringSet(key,s).apply();}
    private void addCompleted(String text){String stamp=new SimpleDateFormat("h:mm:ss a",Locale.US).format(new Date());String old=p.getString("completed_upload_log","");String combined=stamp+"  "+text+(old.isEmpty()?"":"\n"+old);String[]lines=combined.split("\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(50,lines.length);i++){if(i>0)b.append('\n');b.append(lines[i]);}p.edit().putString("completed_upload_log",b.toString()).apply();}

    private long photoDate(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DATE_ADDED},null,null,null)){if(c!=null&&c.moveToFirst()){long t=c.getLong(0);if(t>0)return t*1000L;}}catch(Exception ignored){}return System.currentTimeMillis();}
    private String displayName(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "Nikon Z8 photo";}
    private boolean uriExists(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media._ID},null,null,null)){return c!=null&&c.moveToFirst();}catch(Exception e){return false;}}

    private void broadcast(String status,String ip){
        if(status==null)status="Receiver running";if(!flickrEnabled())cellularState="not needed — Flickr uploads off";
        SharedPreferences.Editor ed=p.edit().putString("last_status",status).putBoolean("receiver_running",true).putString("cellular_state",cellularState);if(ip!=null)ed.putString("last_ip",ip);ed.apply();
        Intent i=new Intent(ACTION_STATUS).setPackage(getPackageName());i.putExtra("status",status);i.putExtra("mode",mode);i.putExtra("ftp_user",FTP_USER);i.putExtra("ftp_pass",p.getString("ftp_password",DEFAULT_FTP_PASSWORD));i.putExtra("ftp_port",FTP_PORT);i.putExtra("cellular",cellularState);if(ip!=null)i.putExtra("ip",ip);sendBroadcast(i);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(71,mainNotification(status));
    }

    private String findBestLocalIp(){try{Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();while(en.hasMoreElements()){NetworkInterface ni=en.nextElement();String nn=ni.getName().toLowerCase(Locale.US);if(!(nn.contains("wlan")||nn.contains("wifi")))continue;Enumeration<InetAddress> aa=ni.getInetAddresses();while(aa.hasMoreElements()){InetAddress a=aa.nextElement();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())return a.getHostAddress();}}}catch(Exception ignored){}return "waiting for Z8 Wi-Fi";}
    private synchronized void addEvent(String text){if(text==null||text.trim().isEmpty())return;String stamp=new SimpleDateFormat("h:mm:ss a",Locale.US).format(new Date());String old=p.getString("event_log","");String combined=stamp+"  "+text.trim()+(old.isEmpty()?"":"\n"+old);String[] lines=combined.split("\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(lines.length,80);i++){if(i>0)b.append('\n');b.append(lines[i]);}p.edit().putString("event_log",b.toString()).apply();}
    private String shortMessage(Exception e){String s=e.getMessage();if(s==null||s.isEmpty())s=e.getClass().getSimpleName();return s.length()>120?s.substring(0,120):s;}
    private Notification mainNotification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,CH).setSmallIcon(R.drawable.ic_camera).setContentTitle("Nikon Auto Upload").setContentText(text).setContentIntent(pi).setOngoing(true).build();}
    private void notifyEvent(String title,String text){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(System.currentTimeMillis()%100000),new Notification.Builder(this,CH).setSmallIcon(R.drawable.ic_camera).setContentTitle(title).setContentText(text==null?"":text).setAutoCancel(true).build());}
    private void createChannel(){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CH,"Direct Nikon transfer",NotificationManager.IMPORTANCE_LOW));}

    @Override public void onDestroy(){if(ftp!=null){ftp.stop();ftp=null;}releaseCellular();p.edit().putBoolean("receiver_running",false).putString("ftp_state","Receiver stopped").putString("last_status","Receiver stopped").apply();retry.shutdownNow();io.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
