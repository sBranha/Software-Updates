package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Camera receiver + Flickr upload engine.
 *
 * Pipeline (0.8.9):
 *   camera FTP / USB / Wi-Fi PTP  ->  staged file in app storage (survives restarts)
 *   -> "saves" thread: hash, duplicate check, copy to the phone gallery, queue for Flickr
 *   -> "overlays" thread: optional sports-card copy (never blocks receiving)
 *   -> upload pool (parallel, default 3): upload, verify, file into album, mark complete
 *
 * The camera never waits on the phone gallery, the editor or Flickr. Every stage is
 * persistent, so a crash, reboot or lost connection only delays photos, never drops them.
 */
public class DirectTransferService extends Service implements SimpleFtpServer.Listener {
    public static final String ACTION_STATUS="com.nikonautoupload.DIRECT_STATUS";
    public static final String CH="nikon_direct";
    public static final int FTP_PORT=2121;
    public static final String DEFAULT_FTP_USER="nikon";
    public static final String DEFAULT_FTP_PASSWORD="47250558";

    private static final int NOTE_MAIN=71,NOTE_RECEIVED=72,NOTE_UPLOADED=73,NOTE_ERROR=74;
    private static final long UI_THROTTLE_MS=750;
    private static final long LOCK_LINGER_MS=2*60*1000L;
    private static final int MAX_SAVE_RETRIES=3;

    private SharedPreferences p;
    private SimpleFtpServer ftp;
    private final ExecutorService io=Executors.newSingleThreadExecutor(named("NAU-control"));
    private final ExecutorService saves=Executors.newSingleThreadExecutor(named("NAU-save"));
    private final ExecutorService overlays=Executors.newSingleThreadExecutor(named("NAU-overlay"));
    private ExecutorService uploads;
    private int uploadWorkers=3;
    private final ScheduledExecutorService retry=Executors.newSingleThreadScheduledExecutor(named("NAU-retry"));
    private final Handler main=new Handler(Looper.getMainLooper());

    // In-memory bookkeeping so nothing is processed twice.
    private final Set<String> inFlightFiles=ConcurrentHashMap.newKeySet();
    private final Set<String> inFlightUploads=ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String,Integer> saveFailures=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Integer> uploadAttempts=new ConcurrentHashMap<>();
    private final AtomicInteger pendingSaves=new AtomicInteger();
    private final AtomicInteger activeUploads=new AtomicInteger();
    private final AtomicInteger ftpSessions=new AtomicInteger();
    private volatile long uploadBackoffUntil=0;
    private volatile int consecutiveNetworkFailures=0;
    private volatile long lastActivity=0;
    private final Object hashLock=new Object();
    private LinkedHashSet<String> knownHashes;

    private volatile Network cellularNetwork;
    private volatile boolean cellularRequestActive;
    private volatile String cellularState="not needed — Flickr uploads off";
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback cellularCallback;
    private String mode="z8ap";

    private PowerManager.WakeLock cpuLock;
    private WifiManager.WifiLock wifiLowLatency,wifiHighPerf;

    // Throttled UI / notification state (burst-safe).
    private volatile String latestStatus="Receiver running";
    private volatile boolean statusDirty;
    private volatile long lastStatusFlush;
    private int sessionReceivedNote=0,sessionUploadedNote=0;
    private volatile String lastReceivedNote="",lastUploadedNote="";
    private volatile boolean receivedNoteDirty,uploadedNoteDirty;

    private static ThreadFactory named(String name){return r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;};}

    @Override public void onCreate(){
        super.onCreate();p=getSharedPreferences("settings",MODE_PRIVATE);migrateStableSettings();MultiCameraSupport.ensureDefaults(p);createChannel();cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        startForeground(NOTE_MAIN,mainNotification("Camera FTP receiver is starting"));
        uploadWorkers=Math.max(1,Math.min(4,p.getInt("upload_parallel",3)));
        uploads=Executors.newFixedThreadPool(uploadWorkers,named("NAU-upload"));
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        cpuLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"NikonAutoUpload:transfer");cpuLock.setReferenceCounted(false);
        WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if(wm!=null){
            try{wifiLowLatency=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,"NikonAutoUpload:camera");wifiLowLatency.setReferenceCounted(false);}catch(Exception ignored){}
            try{wifiHighPerf=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"NikonAutoUpload:camera-hp");wifiHighPerf.setReferenceCounted(false);}catch(Exception ignored){}
        }
        p.edit().putBoolean("receiver_running",true).putBoolean("camera_ftp_session_active",false).putString("ftp_state",p.getBoolean("receiver_paused",false)?"Receiver paused":"Waiting for camera").putInt("status_uploading_count",0).apply();
        loadKnownHashes();
        recoverBufferedTransfers(true);
        if(flickrEnabled())requestCellular();
        retry.scheduleWithFixedDelay(this::tick,20,30,TimeUnit.SECONDS);
    }

    private void tick(){
        try{
            if(!p.getBoolean("receiver_paused",false)&&ftp==null)startServerOnly();
            if(flickrEnabled()){if(uploadNetwork()==null&&!cellularRequestActive)requestCellular();kickUploads();}
            recoverBufferedTransfers(false);
            runCleanupIfNeeded();
            p.edit().putString("last_ip",findBestLocalIp()).apply();
            updateLocks();
        }catch(Throwable t){addEvent("Background check error: "+shortMessage(t));}
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        // Re-assert foreground on every start request (required after startForegroundService()).
        try{startForeground(NOTE_MAIN,mainNotification(latestStatus));}catch(Exception ignored){}
        String requested=intent==null?"z8ap":intent.getStringExtra("mode");mode=requested==null?"z8ap":requested;
        String cmd=intent==null?null:intent.getStringExtra("command");
        if("import_file".equals(cmd)){
            String path=intent.getStringExtra("file_path"),name=intent.getStringExtra("file_name"),source=intent.getStringExtra("source_label");
            if(source==null||source.trim().isEmpty())source="Bluetooth/Wi-Fi";
            try{
                File imported=path==null?null:new File(path);
                if(imported!=null&&imported.exists()&&imported.isFile()){
                    if(name==null||name.trim().isEmpty())name=originalNameFor(imported);
                    onPhotoReceived(imported,name);
                }else if(imported==null||!inFlightFiles.contains(imported.getAbsolutePath()))addEvent(source+" handoff file already processed or missing");
            }catch(Exception e){addEvent(source+" handoff import error: "+shortMessage(e));}
            return START_STICKY;
        }
        if("pause_receiver".equals(cmd)){pauseReceiver();return START_STICKY;}
        if("resume_receiver".equals(cmd)){resumeReceiver();return START_STICKY;}
        if("restart_receiver".equals(cmd)){restartReceiver();return START_STICKY;}
        if("retry_failed".equals(cmd)){retryFailedNow();return START_STICKY;}
        if("kick_uploads".equals(cmd)){if(flickrEnabled()){if(uploadNetwork()==null)requestCellular();kickUploads();}return START_STICKY;}
        if(!p.getBoolean("receiver_paused",false))startServerOnly();
        if(flickrEnabled()){if(uploadNetwork()==null&&!cellularRequestActive)requestCellular();kickUploads();}
        else{releaseCellular();cellularState="not needed — Flickr uploads off";p.edit().putString("cellular_state",cellularState).apply();}
        return START_STICKY;
    }

    private void migrateStableSettings(){
        SharedPreferences.Editor e=p.edit();
        if(!p.getBoolean("ftp_fixed_password_v031",false)){e.putString("ftp_password",DEFAULT_FTP_PASSWORD);e.putBoolean("ftp_fixed_password_v031",true);}else if(p.getString("ftp_password","").trim().isEmpty())e.putString("ftp_password",DEFAULT_FTP_PASSWORD);
        if(!p.contains("ftp_user"))e.putString("ftp_user",DEFAULT_FTP_USER);
        if(!p.contains("flickr_upload_enabled"))e.putBoolean("flickr_upload_enabled",true);
        if(!p.contains("daily_albums_enabled"))e.putBoolean("daily_albums_enabled",true);
        if(!p.contains("album_max_photos"))e.putInt("album_max_photos",999);
        if(!p.contains("album_suffix"))e.putString("album_suffix","");
        if(!p.contains("duplicate_protection"))e.putBoolean("duplicate_protection",true);
        if(!p.contains("auto_delete_after_upload"))e.putBoolean("auto_delete_after_upload",false);
        if(!p.contains("cleanup_keep_days"))e.putInt("cleanup_keep_days",0);
        if(!p.contains("receiver_paused"))e.putBoolean("receiver_paused",false);
        if(!p.contains("upload_parallel"))e.putInt("upload_parallel",3);
        if(!p.contains("allow_wifi_uploads"))e.putBoolean("allow_wifi_uploads",true);
        e.apply();
    }

    private boolean flickrEnabled(){return p.getBoolean("flickr_upload_enabled",true);}
    private boolean dailyAlbumsEnabled(){return p.getBoolean("daily_albums_enabled",true);}

    // ---------------------------------------------------------------- FTP receiver control

    private void startServerOnly(){
        if(p.getBoolean("receiver_paused",false))return;
        io.execute(()->{try{if(startFtp())broadcast("FTP receiver ready — waiting for "+MultiCameraSupport.cameraName(p));}catch(Exception e){p.edit().putString("ftp_state","Receiver error").apply();broadcast("FTP receiver error: "+shortMessage(e));}});
    }

    /** @return true when a new server was started. */
    private synchronized boolean startFtp() throws IOException {
        if(ftp!=null||p.getBoolean("receiver_paused",false))return false;
        File temp=new File(getFilesDir(),"nikon_ftp");SimpleFtpServer candidate=new SimpleFtpServer(FTP_PORT,p.getString("ftp_user",DEFAULT_FTP_USER),p.getString("ftp_password",DEFAULT_FTP_PASSWORD),temp,this);candidate.start();ftp=candidate;return true;
    }

    private synchronized void stopFtp(){if(ftp!=null){ftp.stop();ftp=null;}ftpSessions.set(0);p.edit().putBoolean("camera_ftp_session_active",false).apply();updateLocks();}

    private synchronized void pauseReceiver(){
        p.edit().putBoolean("receiver_paused",true).putBoolean("camera_ftp_session_active",false).putString("ftp_state","Receiver paused").apply();
        stopFtp();broadcast("Camera receiving paused — Flickr retries can continue");
    }
    private synchronized void resumeReceiver(){
        p.edit().putBoolean("receiver_paused",false).putString("ftp_state","Waiting for camera").apply();recoverBufferedTransfers(true);startServerOnly();broadcast("Camera receiving resumed");
    }
    private synchronized void restartReceiver(){
        p.edit().putBoolean("receiver_paused",false).putString("ftp_state","Restarting FTP receiver").apply();
        stopFtp();recoverBufferedTransfers(true);startServerOnly();
        broadcast("FTP receiver restarted with updated login");
    }

    // ---------------------------------------------------------------- Internet path for Flickr

    private synchronized void requestCellular(){
        if(!flickrEnabled()||cm==null||cellularRequestActive||cellularNetwork!=null)return;
        // Never stack requests: Android allows ~100 per app and then throws.
        if(cellularCallback!=null){try{cm.unregisterNetworkCallback(cellularCallback);}catch(Exception ignored){}cellularCallback=null;}
        cellularRequestActive=true;cellularState="requesting 5G/LTE";p.edit().putString("cellular_state",cellularState).apply();
        NetworkRequest req=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        cellularCallback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network network){cellularNetwork=network;cellularRequestActive=false;cellularState="5G/LTE ready";p.edit().putString("cellular_state",cellularState).apply();addEvent("Cellular Flickr path ready");uploadBackoffUntil=0;consecutiveNetworkFailures=0;broadcast("Cellular Flickr path ready — camera network can stay connected");kickUploads();}
            @Override public void onLost(Network network){if(network.equals(cellularNetwork))cellularNetwork=null;cellularState=uploadNetwork()!=null?"Wi-Fi internet ready (cellular lost)":"cellular temporarily unavailable";p.edit().putString("cellular_state",cellularState).apply();if(flickrEnabled())addEvent("Cellular lost — uploads queued");broadcast(flickrEnabled()?"Cellular data lost — photos stay queued":"Flickr uploads off — photos still save to phone");}
            @Override public void onUnavailable(){cellularNetwork=null;cellularRequestActive=false;cellularState=uploadNetwork()!=null?"Wi-Fi internet ready (no cellular)":"cellular not ready — check Mobile data";p.edit().putString("cellular_state",cellularState).apply();if(flickrEnabled())kickUploads();}
        };
        try{cm.requestNetwork(req,cellularCallback,30000);}catch(Exception e){cellularRequestActive=false;cellularCallback=null;cellularState="cellular request failed";p.edit().putString("cellular_state",cellularState).apply();broadcast("Could not request cellular data: "+shortMessage(e));}
    }

    private synchronized void releaseCellular(){if(cm!=null&&cellularCallback!=null){try{cm.unregisterNetworkCallback(cellularCallback);}catch(Exception ignored){}}cellularCallback=null;cellularNetwork=null;cellularRequestActive=false;}

    /**
     * Cellular is preferred so the phone can stay joined to the camera's Wi-Fi. If there is no
     * cellular (Wi-Fi-only phone, no SIM, or data off) any network that Android has verified
     * reaches the internet is used. The camera's own Wi-Fi never passes that check.
     */
    private Network uploadNetwork(){
        Network cell=cellularNetwork;if(cell!=null)return cell;
        if(cm==null||!p.getBoolean("allow_wifi_uploads",true))return null;
        try{
            Network active=cm.getActiveNetwork();if(hasValidatedInternet(active))return active;
            for(Network n:cm.getAllNetworks())if(hasValidatedInternet(n))return n;
        }catch(Exception ignored){}
        return null;
    }
    private boolean hasValidatedInternet(Network n){
        if(n==null)return false;NetworkCapabilities c=cm.getNetworkCapabilities(n);
        return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    // ---------------------------------------------------------------- Receiving / saving

    /** Staged transfers survive app restarts; save photos independent of Flickr. */
    private void recoverBufferedTransfers(boolean force){
        if(force)saveFailures.clear();
        File[] folders={new File(getFilesDir(),"nikon_ftp"),new File(getCacheDir(),"nikon_ftp"),new File(getFilesDir(),"usb_import"),new File(getCacheDir(),"usb_import"),new File(getFilesDir(),"nikon_ptp"),new File(getCacheDir(),"nikon_ptp")};
        for(File folder:folders){
            File[] ready=folder.listFiles((dir,name)->!name.endsWith(".part")&&!name.startsWith("."));
            if(ready==null)continue;
            Arrays.sort(ready,Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
            for(File file:ready){
                if(!file.isFile()||inFlightFiles.contains(file.getAbsolutePath()))continue;
                if(p.getBoolean("receiver_paused",false)&&!folder.getName().equals("usb_import"))continue;
                Integer fails=saveFailures.get(file.getAbsolutePath());if(fails!=null&&fails>=MAX_SAVE_RETRIES)continue;
                // Don't race a USB/PTP import that is still being handed over (file younger than 20 s).
                if(!force&&!folder.getName().equals("nikon_ftp")&&System.currentTimeMillis()-file.lastModified()<20000)continue;
                onPhotoReceived(file,originalNameFor(file));
            }
        }
    }

    static String originalNameFor(File f){
        String n=f.getName();
        if(n.endsWith(".ready"))n=n.substring(0,n.length()-".ready".length());
        int sep=n.indexOf("__");if(sep>=0&&sep+2<n.length())n=n.substring(sep+2);
        return n;
    }

    @Override public void onPhotoReceived(File file,String originalName){
        if(file==null)return;
        final String key=file.getAbsolutePath();
        if(!inFlightFiles.add(key))return;
        pendingSaves.incrementAndGet();lastActivity=System.currentTimeMillis();updateLocks();
        final long receivedAt=System.currentTimeMillis();
        try{
            saves.execute(()->{
                try{handleIncoming(file,originalName,receivedAt);}
                finally{inFlightFiles.remove(key);pendingSaves.decrementAndGet();lastActivity=System.currentTimeMillis();updateLocks();}
            });
        }catch(RejectedExecutionException shuttingDown){inFlightFiles.remove(key);pendingSaves.decrementAndGet();}
    }

    private void handleIncoming(File file,String originalName,long receivedAt){
        String key=file.getAbsolutePath();
        try{
            boolean usbImport=key.replace('\\','/').contains("/usb_import/");
            if(p.getBoolean("receiver_paused",false)&&!usbImport){addEvent("Photo retained for resume: "+originalName);broadcast("Receiver paused — pending photo kept");return;}
            if(!file.exists())return;
            String hash=sha256(file);
            if(p.getBoolean("duplicate_protection",true)&&isKnownHash(hash)){deleteQuietly(file);addEvent("Duplicate ignored: "+originalName);broadcast("Duplicate camera photo ignored: "+originalName);return;}
            Uri uri=saveToGallery(file,originalName);rememberHash(hash);deleteQuietly(file);saveFailures.remove(key);
            String uriText=uri.toString();
            boolean send=flickrEnabled()&&isFlickrUploadable(originalName);
            boolean overlayOn=OverlayProcessor.isActiveFor(p,originalName);
            boolean queueOriginal=send&&(!overlayOn||OverlayProcessor.wantsOriginal(p));
            String albumBase=FlickrClient.albumBaseTitle(receivedAt,effectiveSuffix());
            if(queueOriginal){addPending(uriText);queueAlbumBase(uriText,albumBase);}else addToSet("phone_only_uris",uriText);
            boolean sessionActive=p.getBoolean("session_active",false);String sessionName=p.getString("session_name","");
            if(sessionActive){addToSet("current_session_uris",uriText);mapSessionUri(uriText,sessionName);}
            long now=System.currentTimeMillis();
            SharedPreferences.Editor ed=p.edit().putInt("total_received",p.getInt("total_received",0)+1).putString("last_received_name",originalName).putLong("last_received_time",now).putString(usbImport?"usb_state":"ftp_state",usbImport?"USB import complete":"Photo received");
            if(sessionActive)ed.putInt("session_received",p.getInt("session_received",0)+1);
            ed.apply();
            String receiveSource=usbImport?"USB-C camera":MultiCameraSupport.cameraName(p);
            addEvent("Received from "+receiveSource+": "+originalName+(send?" — Flickr workflow":" — phone only")+(overlayOn?" • card queued":""));
            noteReceived(originalName);
            broadcast(send?("Received "+originalName+" — saved on phone and queued for Flickr"):("Received "+originalName+" — saved on phone; Flickr upload is OFF"));
            if(overlayOn){final boolean originalQueued=queueOriginal;overlays.execute(()->processOverlay(uri,originalName,send,originalQueued,albumBase,sessionActive,sessionName));}
            if(queueOriginal)kickUploads();
        }catch(Exception e){
            int n=saveFailures.merge(key,1,Integer::sum);
            addEvent("Incoming photo kept for retry ("+n+"/"+MAX_SAVE_RETRIES+"): "+originalName+" — "+shortMessage(e));
            if(n>=MAX_SAVE_RETRIES)notifyError("Photo could not be saved to the gallery",originalName+" is kept safely in the app. Free some space, then tap Restart receiver.");
            broadcast("Photo save deferred — will retry automatically");
        }
    }

    private void processOverlay(Uri original,String originalName,boolean send,boolean originalQueued,String albumBase,boolean sessionActive,String sessionName){
        try{
            OverlayProcessor.ProcessResult overlay=OverlayProcessor.onImported(this,p,original,originalName);
            if(overlay.overlayUri!=null){
                String overlayText=overlay.overlayUri.toString();
                if(send&&overlay.queueOverlay){addPending(overlayText);queueAlbumBase(overlayText,albumBase);kickUploads();}
                else if(!overlay.holdOverlay)addToSet("phone_only_uris",overlayText);
                if(sessionActive){addToSet("current_session_uris",overlayText);mapSessionUri(overlayText,sessionName);}
                p.edit().putInt("overlay_completed_count",p.getInt("overlay_completed_count",0)+1).apply();
            }else if(send&&!originalQueued){
                // The card could not be made; upload the original instead of silently sending nothing.
                String uriText=original.toString();removeFromSet("phone_only_uris",uriText);addPending(uriText);queueAlbumBase(uriText,albumBase);kickUploads();
                addEvent("Card failed for "+originalName+" — uploading the original instead ("+overlay.message+")");
            }
        }catch(Throwable t){addEvent("Card processing error: "+shortMessage(t));}
    }

    @Override public void onStatus(String text){
        String low=text==null?"":text.toLowerCase(Locale.US);
        if(low.contains("tcp connected")){p.edit().putBoolean("camera_ftp_session_active",true).putString("ftp_state",MultiCameraSupport.cameraName(p)+" connected").apply();addEvent(MultiCameraSupport.cameraName(p)+" connected to FTP receiver");}
        else if(low.contains("login accepted")){p.edit().putBoolean("camera_ftp_session_active",true).putString("ftp_state",MultiCameraSupport.cameraName(p)+" logged in").apply();addEvent(MultiCameraSupport.cameraName(p)+" FTP login accepted");}
        else if(low.contains("transfer incomplete")){addEvent(text);}
        else if(low.contains("login rejected")){p.edit().putString("ftp_state",text).apply();addEvent(text+" — check the FTP user/password in the camera");}
        else if(low.contains("session ended")){if(ftpSessions.get()<=0)p.edit().putBoolean("camera_ftp_session_active",false).putString("ftp_state",text).apply();addEvent(text);}
        broadcast(text);
    }

    @Override public void onSessionCountChanged(int activeSessions){
        ftpSessions.set(activeSessions);lastActivity=System.currentTimeMillis();
        p.edit().putBoolean("camera_ftp_session_active",activeSessions>0).apply();
        updateLocks();
    }

    private boolean isFlickrUploadable(String name){String x=name==null?"":name.toLowerCase(Locale.US);return x.endsWith(".jpg")||x.endsWith(".jpeg");}
    private String mimeForName(String name){
        String x=name==null?"":name.toLowerCase(Locale.US);
        if(x.endsWith(".jpg")||x.endsWith(".jpeg"))return "image/jpeg";
        if(x.endsWith(".nef")||x.endsWith(".nrw"))return "image/x-nikon-nef";
        if(x.endsWith(".arw"))return "image/x-sony-arw";
        if(x.endsWith(".cr2"))return "image/x-canon-cr2";
        if(x.endsWith(".cr3"))return "image/x-canon-cr3";
        if(x.endsWith(".raf"))return "image/x-fuji-raf";
        if(x.endsWith(".dng"))return "image/x-adobe-dng";
        if(x.endsWith(".rw2"))return "image/x-panasonic-rw2";
        if(x.endsWith(".orf"))return "image/x-olympus-orf";
        if(x.endsWith(".tif")||x.endsWith(".tiff"))return "image/tiff";
        if(x.endsWith(".png"))return "image/png";
        if(x.endsWith(".heif")||x.endsWith(".hif")||x.endsWith(".heic"))return "image/heif";
        return "application/octet-stream";
    }

    private Uri saveToGallery(File file,String name) throws Exception {
        String mime=mimeForName(name);
        ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,mime);
        v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Nikon Auto Upload");v.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Could not create phone photo");
        try{
            long copied=0;
            try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Could not open phone photo");byte[] b=new byte[256*1024];int n;while((n=in.read(b))!=-1){out.write(b,0,n);copied+=n;}}
            if(copied!=file.length())throw new IOException("Phone copy is incomplete");
            ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);
            return u;
        }catch(Exception e){try{getContentResolver().delete(u,null,null);}catch(Exception ignored){}throw e;}
    }

    // ---------------------------------------------------------------- Persistent queue helpers

    private synchronized void addPending(String u){addToSet("pending_uploads",u);}
    private synchronized void removePending(String u){removeFromSet("pending_uploads",u);removeAlbumBase(u);}
    private synchronized void addPendingAlbum(String photoId,String baseTitle,String uri,String name,String privacy){Set<String>s=new HashSet<>(p.getStringSet("pending_album_assignments",Collections.emptySet()));removePendingAlbumForUriFromSet(s,uri);s.add(photoId+"\u001f"+baseTitle+"\u001f"+uri+"\u001f"+name+"\u001f"+privacy);p.edit().putStringSet("pending_album_assignments",s).commit();}
    private synchronized void removePendingAlbum(String item){removeFromSet("pending_album_assignments",item);}
    private void removePendingAlbumForUriFromSet(Set<String>s,String uri){if(uri==null||uri.isEmpty())return;Iterator<String>it=s.iterator();while(it.hasNext()){String x=it.next();String[]q=x.split("\u001f",5);if(q.length>=3&&q[2].equals(uri))it.remove();}}
    private boolean hasPendingAlbumForUri(String uri){for(String x:p.getStringSet("pending_album_assignments",Collections.emptySet())){String[]q=x.split("\u001f",5);if(q.length>=3&&q[2].equals(uri))return true;}return false;}
    private synchronized void removePendingAlbumForUri(String uri){Set<String>s=new HashSet<>(p.getStringSet("pending_album_assignments",Collections.emptySet()));removePendingAlbumForUriFromSet(s,uri);p.edit().putStringSet("pending_album_assignments",s).apply();}

    // ---------------------------------------------------------------- Upload engine

    /** Fill the upload pool. Cheap and safe to call from anywhere, any number of times. */
    private void kickUploads(){
        if(!flickrEnabled())return;
        try{io.execute(this::scheduleUploads);}catch(RejectedExecutionException ignored){}
    }

    private void scheduleUploads(){
        if(!flickrEnabled())return;
        Set<String> items=p.getStringSet("pending_uploads",Collections.emptySet());Set<String> albumItems=p.getStringSet("pending_album_assignments",Collections.emptySet());
        if(items.isEmpty()&&albumItems.isEmpty())return;
        if(p.getString("access_token","").isEmpty()){broadcast("Flickr is not connected — pending photos are safely saved");return;}
        if(System.currentTimeMillis()<uploadBackoffUntil)return;
        if(uploadNetwork()==null){if(!cellularRequestActive)requestCellular();return;}
        int slots=uploadWorkers*2-inFlightUploads.size();
        // 1) Photos already on Flickr that still need verify/album filing.
        for(String item:new ArrayList<>(albumItems)){
            if(slots<=0)return;
            String[] q=item.split("\u001f",5);String key=q.length>=3&&!q[2].isEmpty()?q[2]:("photo:"+q[0]);
            if(inFlightUploads.add(key)){slots--;submit(key,()->albumTask(item));}
        }
        // 2) New uploads, oldest first; items that keep failing go to the back of the line.
        ArrayList<String> ordered=new ArrayList<>(items);
        ordered.sort(Comparator.comparingInt((String s)->uploadAttempts.getOrDefault(s,0)).thenComparingLong(DirectTransferService::mediaId));
        for(String s:ordered){
            if(slots<=0)return;
            if(inFlightUploads.contains(s)||hasPendingAlbumForUri(s))continue;
            if(inFlightUploads.add(s)){slots--;submit(s,()->uploadTask(s));}
        }
    }

    private void submit(String key,Runnable task){
        try{uploads.execute(()->{
            activeUploads.incrementAndGet();p.edit().putInt("status_uploading_count",activeUploads.get()).apply();updateLocks();
            try{task.run();}
            finally{inFlightUploads.remove(key);int left=activeUploads.decrementAndGet();p.edit().putInt("status_uploading_count",Math.max(0,left)).apply();lastActivity=System.currentTimeMillis();updateLocks();kickUploads();}
        });}catch(RejectedExecutionException e){inFlightUploads.remove(key);}
    }

    private static long mediaId(String uri){try{return Long.parseLong(Uri.parse(uri).getLastPathSegment());}catch(Exception e){return Long.MAX_VALUE;}}

    private void uploadTask(String s){
        if(!flickrEnabled()||System.currentTimeMillis()<uploadBackoffUntil)return;
        Network net=uploadNetwork();if(net==null){requestCellular();return;}
        Uri u=Uri.parse(s);
        if(!p.getStringSet("pending_uploads",Collections.emptySet()).contains(s))return;
        if(!uriExists(u)){removePending(s);removeFromSet("failed_upload_uris",s);uploadAttempts.remove(s);addEvent("Skipped missing phone file");return;}
        String name=displayName(u);boolean isPublic=p.getBoolean("public",true);String privacy=isPublic?"PUBLIC":"ONLY ME";
        try{
            broadcast("Uploading "+name+" to Flickr • "+privacy+"…");
            FlickrClient client=new FlickrClient(this);
            String photoId=client.upload(u,name,p.getString("tags","nikon z8"),isPublic,net);
            String base=albumBaseForUri(s);if(base.isEmpty())base=FlickrClient.albumBaseTitle(photoDate(u),effectiveSuffix());
            // Persist "uploaded as photoId" immediately so a later failure never re-uploads a duplicate.
            addPendingAlbum(photoId,base,s,name,privacy);
            onNetworkSuccess();
            completeUploaded(client,photoId,base,s,name,privacy,net);
        }catch(Exception e){handleUploadFailure(s,name,e);}
    }

    private void albumTask(String item){
        String[] q=item.split("\u001f",5);if(q.length<2){removePendingAlbum(item);return;}
        String photoId=q[0],base=q[1],uri=q.length>=3?q[2]:"",name=q.length>=4?q[3]:"Nikon Z8 photo",privacy=q.length>=5?q[4]:(p.getBoolean("public",true)?"PUBLIC":"ONLY ME");
        if(!flickrEnabled()||System.currentTimeMillis()<uploadBackoffUntil)return;
        Network net=uploadNetwork();if(net==null){requestCellular();return;}
        try{completeUploaded(new FlickrClient(this),photoId,base,uri,name,privacy,net);}
        catch(Exception e){handleUploadFailure(uri.isEmpty()?("photo:"+photoId):uri,name,e);}
    }

    private void completeUploaded(FlickrClient client,String photoId,String base,String uri,String name,String privacy,Network net) throws Exception {
        try{client.verifyPhoto(photoId,net);}
        catch(FlickrClient.FlickrApiException fe){
            if(fe.permanent){
                // Flickr no longer has it (deleted on the website?). Forget the id; the photo uploads again.
                removePendingAlbumByPhoto(photoId);addEvent("Flickr copy of "+name+" not found — uploading again");return;
            }
            throw fe;
        }
        String album="";
        if(dailyAlbumsEnabled()){
            try{FlickrClient.AlbumResult ar=client.addToDailyAlbum(photoId,base,albumLimit(),net);album=ar.title;p.edit().putString("last_album_name",ar.title).putInt("last_album_count",ar.count).putString("last_album_status","In album").apply();}
            catch(FlickrClient.FlickrApiException fe){
                if(!fe.permanent)throw fe;
                p.edit().putString("last_album_status","Album skipped: "+shortMessage(fe)).apply();addEvent("Uploaded "+name+" but Flickr refused the album: "+shortMessage(fe));
            }
        }
        removePendingAlbumByPhoto(photoId);
        if(!uri.isEmpty())finalizeUpload(uri,name,privacy,album);
        uploadAttempts.remove(uri);
        onNetworkSuccess();
    }

    private synchronized void removePendingAlbumByPhoto(String photoId){Set<String>s=new HashSet<>(p.getStringSet("pending_album_assignments",Collections.emptySet()));Iterator<String>it=s.iterator();while(it.hasNext())if(it.next().startsWith(photoId+"\u001f"))it.remove();p.edit().putStringSet("pending_album_assignments",s).commit();}

    private void onNetworkSuccess(){consecutiveNetworkFailures=0;uploadBackoffUntil=0;}

    private void handleUploadFailure(String key,String name,Exception e){
        if(e instanceof FlickrClient.FlickrApiException){
            FlickrClient.FlickrApiException fe=(FlickrClient.FlickrApiException)e;
            if(fe.permanent){
                // This one photo was refused (bad/unsupported file). Park it so it can't block the queue.
                removePending(key);addToSet("failed_upload_uris",key);uploadAttempts.remove(key);
                addEvent("Flickr refused "+name+": "+shortMessage(e)+" — kept on phone (Uploads ▸ Retry)");
                return;
            }
            // Sign-in / key problem: pause the whole queue for a minute and tell the user.
            uploadBackoffUntil=System.currentTimeMillis()+60_000L;
            addEvent(shortMessage(e));broadcast(shortMessage(e));notifyError("Flickr uploads are waiting",shortMessage(e));
            return;
        }
        int n=uploadAttempts.merge(key,1,Integer::sum);
        if(key.startsWith("content:"))addToSet("failed_upload_uris",key);
        int f=++consecutiveNetworkFailures;
        long delay=Math.min(5*60_000L,5_000L*(1L<<Math.min(6,f-1)));
        uploadBackoffUntil=System.currentTimeMillis()+delay;
        addEvent("Flickr retry queued ("+n+"): "+name+" — "+shortMessage(e));
        broadcast("Photo is saved; Flickr upload will retry in "+(delay/1000)+" s");
        retry.schedule(this::kickUploads,delay+250,TimeUnit.MILLISECONDS);
    }

    private void retryFailedNow(){
        Set<String> failed=new HashSet<>(p.getStringSet("failed_upload_uris",Collections.emptySet()));
        Set<String> uploaded=p.getStringSet("uploaded_uris",Collections.emptySet());
        for(String s:failed){if(!uploaded.contains(s)&&uriExists(Uri.parse(s)))addPending(s);else removeFromSet("failed_upload_uris",s);}
        uploadAttempts.clear();uploadBackoffUntil=0;consecutiveNetworkFailures=0;
        if(flickrEnabled()){if(uploadNetwork()==null)requestCellular();kickUploads();}
        recoverBufferedTransfers(true);
    }

    private void finalizeUpload(String uriText,String name,String privacy,String album){
        boolean first=!p.getStringSet("uploaded_uris",Collections.emptySet()).contains(uriText);removePending(uriText);removeFromSet("failed_upload_uris",uriText);removeFromSet("phone_only_uris",uriText);addToSet("uploaded_uris",uriText);
        long now=System.currentTimeMillis();SharedPreferences.Editor e=p.edit().putString("last_uploaded_name",name).putLong("last_uploaded_time",now).putString("last_upload_privacy",privacy);
        if(!album.isEmpty())e.putString("album_for_"+Integer.toHexString(uriText.hashCode()),album);
        if(first){e.putInt("total_uploaded",p.getInt("total_uploaded",0)+1);if(p.getBoolean("session_active",false)||isCurrentSessionUri(uriText))e.putInt("session_uploaded",p.getInt("session_uploaded",0)+1);}e.apply();
        if(!album.isEmpty())addSessionAlbum(album);
        String detail=name+" • "+privacy+(album.isEmpty()?"":" • "+album);
        addCompleted("✓ "+detail);addEvent("Upload verified: "+detail);noteUploaded(detail);broadcast("Verified on Flickr: "+detail);
        if(p.getBoolean("auto_delete_after_upload",false))deletePhoneUri(Uri.parse(uriText),"Auto-deleted phone copy after verified upload");
    }

    private int albumLimit(){return Math.max(1,Math.min(999,p.getInt("album_max_photos",999)));}
    private String effectiveSuffix(){if(p.getBoolean("session_active",false)){String s=cleanSuffix(p.getString("session_name",""));if(!s.isEmpty())return s;}return cleanSuffix(p.getString("album_suffix",""));}
    private String cleanSuffix(String s){return s==null?"":s.replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}

    private synchronized void queueAlbumBase(String uri,String base){Set<String>s=new HashSet<>(p.getStringSet("pending_album_bases",Collections.emptySet()));Iterator<String>it=s.iterator();while(it.hasNext())if(it.next().startsWith(uri+"\u001f"))it.remove();s.add(uri+"\u001f"+base);p.edit().putStringSet("pending_album_bases",s).apply();}
    private String albumBaseForUri(String uri){for(String x:p.getStringSet("pending_album_bases",Collections.emptySet())){String[]q=x.split("\u001f",2);if(q.length==2&&q[0].equals(uri))return q[1];}return "";}
    private synchronized void removeAlbumBase(String uri){Set<String>s=new HashSet<>(p.getStringSet("pending_album_bases",Collections.emptySet()));Iterator<String>it=s.iterator();while(it.hasNext())if(it.next().startsWith(uri+"\u001f"))it.remove();p.edit().putStringSet("pending_album_bases",s).apply();}

    private synchronized void mapSessionUri(String uri,String session){if(session==null||session.trim().isEmpty())return;Set<String>s=new HashSet<>(p.getStringSet("session_uri_map",Collections.emptySet()));s.add(uri+"\u001f"+session.trim());p.edit().putStringSet("session_uri_map",s).apply();}
    private boolean isCurrentSessionUri(String uri){return p.getStringSet("current_session_uris",Collections.emptySet()).contains(uri);}
    private synchronized void addSessionAlbum(String album){if(album==null||album.isEmpty())return;Set<String>s=new HashSet<>(p.getStringSet("session_album_names",Collections.emptySet()));s.add(album);p.edit().putStringSet("session_album_names",s).putInt("session_albums",s.size()).apply();}

    private void runCleanupIfNeeded(){
        int days=p.getInt("cleanup_keep_days",0);if(days<=0)return;long now=System.currentTimeMillis(),last=p.getLong("last_cleanup_ms",0);if(now-last<6*60*60*1000L)return;p.edit().putLong("last_cleanup_ms",now).apply();
        long cutoff=now-days*24L*60L*60L*1000L;for(String s:new HashSet<>(p.getStringSet("uploaded_uris",Collections.emptySet()))){try{Uri u=Uri.parse(s);if(uriExists(u)&&photoDate(u)<cutoff)deletePhoneUri(u,"Auto-cleaned old uploaded phone copy");}catch(Exception ignored){}}
    }

    private void deletePhoneUri(Uri u,String event){try{int n=getContentResolver().delete(u,null,null);if(n>0){addEvent(event);removeFromSet("favorite_uris",u.toString());}}catch(Exception e){addEvent("Could not delete phone copy automatically: "+shortMessage(e));}}

    // ---------------------------------------------------------------- Duplicate protection

    private String sha256(File f) throws Exception {MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[]b=new byte[256*1024];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();}
    private void loadKnownHashes(){synchronized(hashLock){knownHashes=new LinkedHashSet<>(p.getStringSet("known_hashes",Collections.emptySet()));}}
    private boolean isKnownHash(String h){synchronized(hashLock){return h!=null&&!h.isEmpty()&&knownHashes.contains(h);}}
    private void rememberHash(String h){
        if(h==null||h.isEmpty())return;
        synchronized(hashLock){knownHashes.add(h);Iterator<String>it=knownHashes.iterator();while(knownHashes.size()>5000&&it.hasNext()){it.next();it.remove();}p.edit().putStringSet("known_hashes",new HashSet<>(knownHashes)).apply();}
    }

    private synchronized void addToSet(String key,String value){Set<String>s=new HashSet<>(p.getStringSet(key,Collections.emptySet()));if(s.add(value))p.edit().putStringSet(key,s).apply();}
    private synchronized void removeFromSet(String key,String value){Set<String>s=new HashSet<>(p.getStringSet(key,Collections.emptySet()));if(s.remove(value))p.edit().putStringSet(key,s).apply();}
    private synchronized void addCompleted(String text){String stamp=new SimpleDateFormat("h:mm:ss a",Locale.US).format(new Date());String old=p.getString("completed_upload_log","");String combined=stamp+"  "+text+(old.isEmpty()?"":"\n"+old);String[]lines=combined.split("\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(50,lines.length);i++){if(i>0)b.append('\n');b.append(lines[i]);}p.edit().putString("completed_upload_log",b.toString()).apply();}

    private long photoDate(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DATE_ADDED},null,null,null)){if(c!=null&&c.moveToFirst()){long t=c.getLong(0);if(t>0)return t*1000L;}}catch(Exception ignored){}return System.currentTimeMillis();}
    private String displayName(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "Nikon Z8 photo";}
    private boolean uriExists(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media._ID},null,null,null)){return c!=null&&c.moveToFirst();}catch(Exception e){return false;}}
    private static void deleteQuietly(File f){try{if(f!=null&&f.exists()&&!f.delete())f.deleteOnExit();}catch(Exception ignored){}}

    // ---------------------------------------------------------------- Keep the phone working during sessions

    /**
     * While the camera is connected, photos are being saved, or uploads are running, hold a
     * partial wake lock (CPU keeps running with the screen off) and a Wi-Fi performance lock
     * (stops Wi-Fi power-save from throttling a burst). Released 2 minutes after the last activity.
     */
    private void updateLocks(){
        try{
            long now=System.currentTimeMillis();
            boolean camera=ftpSessions.get()>0;
            boolean busy=camera||pendingSaves.get()>0||activeUploads.get()>0||now-lastActivity<LOCK_LINGER_MS;
            synchronized(this){
                if(busy){if(cpuLock!=null)cpuLock.acquire(15*60*1000L);}
                else if(cpuLock!=null&&cpuLock.isHeld())cpuLock.release();
                boolean wifi=camera||(pendingSaves.get()>0&&now-lastActivity<LOCK_LINGER_MS);
                for(WifiManager.WifiLock l:new WifiManager.WifiLock[]{wifiLowLatency,wifiHighPerf}){
                    if(l==null)continue;
                    if(wifi&&!l.isHeld())l.acquire();else if(!wifi&&l.isHeld())l.release();
                }
            }
        }catch(Exception ignored){}
    }

    // ---------------------------------------------------------------- Status, notifications (throttled)

    private void broadcast(String status){
        latestStatus=status==null?"Receiver running":status;statusDirty=true;scheduleUiFlush();
    }

    private void scheduleUiFlush(){
        long wait=Math.max(0,UI_THROTTLE_MS-(SystemClock.uptimeMillis()-lastStatusFlush));
        main.removeCallbacks(flushUi);main.postDelayed(flushUi,wait);
    }

    private final Runnable flushUi=()->{
        lastStatusFlush=SystemClock.uptimeMillis();
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(statusDirty){
                statusDirty=false;String status=latestStatus;
                if(!flickrEnabled())cellularState="not needed — Flickr uploads off";
                String ip=p.getString("last_ip","");
                p.edit().putString("last_status",status).putBoolean("receiver_running",true).putString("cellular_state",cellularState).apply();
                Intent i=new Intent(ACTION_STATUS).setPackage(getPackageName());i.putExtra("status",status);i.putExtra("mode",mode);i.putExtra("ftp_user",p.getString("ftp_user",DEFAULT_FTP_USER));i.putExtra("ftp_port",FTP_PORT);i.putExtra("cellular",cellularState);if(!ip.isEmpty())i.putExtra("ip",ip);sendBroadcast(i);
                nm.notify(NOTE_MAIN,mainNotification(status));
            }
            if(receivedNoteDirty){receivedNoteDirty=false;nm.notify(NOTE_RECEIVED,eventNotification("Photos received: "+sessionReceivedNote,"Latest: "+lastReceivedNote));}
            if(uploadedNoteDirty){uploadedNoteDirty=false;nm.notify(NOTE_UPLOADED,eventNotification("Uploaded to Flickr: "+sessionUploadedNote,"Latest: "+lastUploadedNote));}
        }catch(Exception ignored){}
    };

    private void noteReceived(String name){synchronized(this){sessionReceivedNote++;}lastReceivedNote=name;receivedNoteDirty=true;scheduleUiFlush();}
    private void noteUploaded(String detail){synchronized(this){sessionUploadedNote++;}lastUploadedNote=detail;uploadedNoteDirty=true;scheduleUiFlush();}
    private void notifyError(String title,String text){try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTE_ERROR,eventNotification(title,text));}catch(Exception ignored){}}

    private String findBestLocalIp(){return MultiCameraSupport.findBestLocalIp();}
    private synchronized void addEvent(String text){if(text==null||text.trim().isEmpty())return;String stamp=new SimpleDateFormat("h:mm:ss a",Locale.US).format(new Date());String old=p.getString("event_log","");String combined=stamp+"  "+text.trim()+(old.isEmpty()?"":"\n"+old);String[] lines=combined.split("\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(lines.length,80);i++){if(i>0)b.append('\n');b.append(lines[i]);}p.edit().putString("event_log",b.toString()).apply();}
    private static String shortMessage(Throwable e){String s=e.getMessage();if(s==null||s.isEmpty())s=e.getClass().getSimpleName();return s.length()>160?s.substring(0,160):s;}
    private Notification mainNotification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,CH).setSmallIcon(R.drawable.ic_camera).setContentTitle("Nikon Auto Upload").setContentText(text).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();}
    private Notification eventNotification(String title,String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,CH).setSmallIcon(R.drawable.ic_camera).setContentTitle(title).setContentText(text==null?"":text).setContentIntent(pi).setOnlyAlertOnce(true).setAutoCancel(true).build();}
    private void createChannel(){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CH,"Direct Nikon transfer",NotificationManager.IMPORTANCE_LOW));}

    @Override public void onDestroy(){
        stopFtp();releaseCellular();
        p.edit().putBoolean("receiver_running",false).putString("ftp_state","Receiver stopped").putString("last_status","Receiver stopped").putBoolean("camera_ftp_session_active",false).putInt("status_uploading_count",0).apply();
        main.removeCallbacks(flushUi);
        retry.shutdownNow();io.shutdownNow();saves.shutdownNow();overlays.shutdownNow();if(uploads!=null)uploads.shutdownNow();
        try{if(cpuLock!=null&&cpuLock.isHeld())cpuLock.release();}catch(Exception ignored){}
        for(WifiManager.WifiLock l:new WifiManager.WifiLock[]{wifiLowLatency,wifiHighPerf})try{if(l!=null&&l.isHeld())l.release();}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
