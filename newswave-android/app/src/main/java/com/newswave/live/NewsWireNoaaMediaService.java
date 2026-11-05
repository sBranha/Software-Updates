package com.newswave.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaDescription;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.media.MediaBrowserService;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * User-initiated NOAA Weather Radio internet rebroadcast playback.
 *
 * This is a dedicated Android Auto media-browser service, deliberately separate
 * from the existing WEATHER CarAppService and its weather/radar UI. There is no
 * RF receiver or claim of continuous emergency-alert monitoring.
 *
 * Stations are only exposed after they were obtained from the saved HTTPS
 * favorites or the nearby third-party internet directory; no arbitrary
 * browser-provided media ID is treated as a playback URL.
 */
public final class NewsWireNoaaMediaService extends MediaBrowserService {
    public static final String ACTION_PLAY_STREAM="com.newswire.live.PLAY_NOAA_STREAM";
    public static final String ACTION_STOP_STREAM="com.newswire.live.STOP_NOAA_STREAM";
    public static final String EXTRA_STREAM="stream";
    public static final String EXTRA_NAME="name";

    private static final String ROOT="newswire_noaa_radio";
    private static final String CHANNEL="newswire_noaa_audio";
    private static final int NOTIFICATION_ID=4931;
    private static final String TAG="NewsWireNoaaAudio";
    private static final long CONNECT_TIMEOUT_MS=20000L;

    private final Handler main=new Handler(Looper.getMainLooper());
    private final Object stationLock=new Object();
    private final Map<String,LocalRadioDirectory.Station> allowedStations=new HashMap<>();
    private MediaSession session;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private LocalRadioDirectory.Station current;
    private boolean hasFocus=false,pausedForFocus=false,isPrepared=false;
    private int generation=0;
    private Runnable connectionTimeout;

    @Override public void onCreate(){
        super.onCreate();
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        NotificationManager notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(notifications!=null)notifications.createNotificationChannel(new NotificationChannel(
            CHANNEL,"NewsWire NOAA Weather Radio",NotificationManager.IMPORTANCE_LOW));
        session=new MediaSession(this,"NewsWire NOAA Weather Radio");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|
            MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){
                if(player!=null&&isPrepared){
                    if(!requestFocus())return;
                    try{player.start();setPlaybackState(PlaybackState.STATE_PLAYING);}
                    catch(IllegalStateException e){stopAudio();}
                }else if(current!=null)startStation(current);
            }
            @Override public void onPlayFromMediaId(String mediaId,Bundle extras){
                LocalRadioDirectory.Station station;
                synchronized(stationLock){station=allowedStations.get(mediaId);}
                if(station!=null)startStation(station);
            }
            @Override public void onPause(){
                pausedForFocus=false;
                pauseAudio();
            }
            @Override public void onStop(){stopAudio();}
        });
        session.setActive(true);
        setSessionToken(session.getSessionToken());
        setPlaybackState(PlaybackState.STATE_STOPPED);
    }

    @Override public BrowserRoot onGetRoot(String clientPackageName,int clientUid,Bundle hints){
        return new BrowserRoot(ROOT,null);
    }

    @Override public void onLoadChildren(String parentId,Result<List<MediaBrowser.MediaItem>> result){
        if(!ROOT.equals(parentId)){
            result.sendResult(new ArrayList<>());
            return;
        }
        result.detach();
        final android.content.Context context=getApplicationContext();
        new Thread(()->{
            List<LocalRadioDirectory.Station> combined=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            for(LocalRadioDirectory.Station station:RadioStationFavorites.list(context)){
                if(station.genre.toLowerCase(Locale.US).contains("noaa")
                    &&seen.add(station.stream))combined.add(station);
            }
            SharedPreferences prefs=getSharedPreferences("newswire-location",MODE_PRIVATE);
            String state=prefs.getString("state","");
            double lat=Double.longBitsToDouble(prefs.getLong("lat",
                Double.doubleToLongBits(Double.NaN)));
            double lon=Double.longBitsToDouble(prefs.getLong("lon",
                Double.doubleToLongBits(Double.NaN)));
            // Do not present nationwide streams as "nearby" without an area.
            if((WidgetAreaResolver.valid(lat,lon))||!state.isEmpty()){
                try{
                    for(LocalRadioDirectory.Station station:NoaaRadioDirectory.nearby(lat,lon,state)){
                        if(seen.add(station.stream))combined.add(station);
                    }
                }catch(Exception e){Log.w(TAG,"NOAA directory unavailable; keeping saved stations",e);}
            }
            List<MediaBrowser.MediaItem> items=new ArrayList<>();
            synchronized(stationLock){
                allowedStations.clear();
                for(LocalRadioDirectory.Station station:combined){
                    if(!station.stream.startsWith("https://"))continue;
                    String id=mediaId(station.stream);
                    allowedStations.put(id,station);
                    MediaDescription description=new MediaDescription.Builder()
                        .setMediaId(id).setTitle(station.name)
                        .setSubtitle(station.genre).build();
                    items.add(new MediaBrowser.MediaItem(description,
                        MediaBrowser.MediaItem.FLAG_PLAYABLE));
                }
            }
            main.post(()->result.sendResult(items));
        },"newswire-noaa-car-directory").start();
    }

    private static String mediaId(String url){
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            byte[] bytes=hash.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder id=new StringBuilder("nwr:");
            for(byte b:bytes)id.append(String.format(Locale.US,"%02x",b&255));
            return id.toString();
        }catch(Exception impossible){return "nwr:"+Integer.toHexString(url.hashCode());}
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){
            if(ACTION_STOP_STREAM.equals(intent.getAction())){
                stopAudio();
            }else if(ACTION_PLAY_STREAM.equals(intent.getAction())){
                String link=intent.getStringExtra(EXTRA_STREAM);
                String name=intent.getStringExtra(EXTRA_NAME);
                Uri uri=link==null?null:Uri.parse(link);
                if(uri!=null&&"https".equalsIgnoreCase(uri.getScheme())
                    &&uri.getHost()!=null&&link.length()<=1900){
                    String label=name==null||name.trim().isEmpty()?
                        "NOAA Weather Radio":name.trim();
                    startStation(new LocalRadioDirectory.Station(label,link,"",
                        "NOAA rebroadcast (third party)","",Double.NaN));
                }
            }
        }
        return START_NOT_STICKY;
    }

    private boolean requestFocus(){
        if(audio==null)return true;
        if(hasFocus)return true;
        focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(change->main.post(()->{
                if(change==AudioManager.AUDIOFOCUS_LOSS){
                    stopAudio();
                }else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){
                    if(player!=null&&isPrepared&&player.isPlaying()){
                        pausedForFocus=true;
                        pauseAudio();
                    }
                }else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){
                    if(player!=null&&isPrepared)player.setVolume(.2f,.2f);
                }else if(change==AudioManager.AUDIOFOCUS_GAIN){
                    if(player!=null&&isPrepared)player.setVolume(1f,1f);
                    if(pausedForFocus&&player!=null&&isPrepared){
                        pausedForFocus=false;
                        try{player.start();setPlaybackState(PlaybackState.STATE_PLAYING);}
                        catch(IllegalStateException e){stopAudio();}
                    }
                }
            })).build();
        hasFocus=audio.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasFocus;
    }

    private void releaseFocus(){
        if(hasFocus&&audio!=null&&focusRequest!=null)
            audio.abandonAudioFocusRequest(focusRequest);
        hasFocus=false;focusRequest=null;
    }

    private Notification buildNotification(String label){
        Intent open=new Intent(this,NewsRadioActivity.class);
        PendingIntent pending=PendingIntent.getActivity(this,0,open,
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("NewsWire NOAA Weather Radio")
            .setContentText(label)
            .setContentIntent(pending)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC).build();
    }

    private void setPlaybackState(int state){
        if(session==null)return;
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|
            PlaybackState.ACTION_STOP|PlaybackState.ACTION_PLAY_FROM_MEDIA_ID;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions).setState(state,PlaybackState.PLAYBACK_POSITION_UNKNOWN,1f)
            .build());
    }

    private void clearTimeout(){
        if(connectionTimeout!=null){
            main.removeCallbacks(connectionTimeout);
            connectionTimeout=null;
        }
    }

    private void releasePlayer(){
        clearTimeout();
        if(player!=null){
            try{player.reset();player.release();}
            catch(RuntimeException ignored){}
            player=null;
        }
        isPrepared=false;pausedForFocus=false;
    }

    private void startStation(LocalRadioDirectory.Station station){
        if(station==null||!station.stream.startsWith("https://"))return;
        releasePlayer();
        releaseFocus();
        current=station;
        int revision=++generation;
        if(!requestFocus()){
            setPlaybackState(PlaybackState.STATE_ERROR);
            return;
        }
        // Car UI playback is media-session-driven; weather templates are unchanged.
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE,station.name)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE,station.name)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                "Third-party NOAA Weather Radio internet rebroadcast").build());
        setPlaybackState(PlaybackState.STATE_BUFFERING);
        // The user explicitly initiated playback. The FGS notification remains
        // visible while connecting, and is removed on failure or Stop.
        try{
            if(Build.VERSION.SDK_INT>=26){
                startForegroundService(new Intent(this,NewsWireNoaaMediaService.class));
            }
            startForeground(NOTIFICATION_ID,buildNotification("Connecting: "+station.name));
            MediaPlayer opening=new MediaPlayer();
            player=opening;
            opening.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            opening.setDataSource(this,Uri.parse(station.stream));
            opening.setOnPreparedListener(p->{
                if(revision!=generation)return;
                clearTimeout();
                try{
                    isPrepared=true;
                    p.start();
                    startForeground(NOTIFICATION_ID,buildNotification(station.name));
                    setPlaybackState(PlaybackState.STATE_PLAYING);
                }catch(RuntimeException e){Log.w(TAG,"NOAA playback failed",e);stopAudio();}
            });
            opening.setOnErrorListener((p,what,extra)->{
                if(revision==generation){
                    Log.w(TAG,"NOAA stream error "+what+"/"+extra);
                    stopAudio();
                }
                return true;
            });
            opening.setOnCompletionListener(p->{
                if(revision==generation)stopAudio();
            });
            opening.prepareAsync();
            connectionTimeout=()->{
                if(revision==generation&&!isPrepared){
                    Log.w(TAG,"NOAA stream connection timed out");
                    stopAudio();
                }
            };
            main.postDelayed(connectionTimeout,CONNECT_TIMEOUT_MS);
        }catch(Exception problem){
            Log.w(TAG,"Could not start NOAA stream",problem);
            stopAudio();
        }
    }

    private void pauseAudio(){
        if(player!=null&&isPrepared){
            try{if(player.isPlaying())player.pause();}
            catch(IllegalStateException e){stopAudio();return;}
        }
        setPlaybackState(PlaybackState.STATE_PAUSED);
    }

    private void stopAudio(){
        ++generation;
        releasePlayer();
        releaseFocus();
        current=null;
        setPlaybackState(PlaybackState.STATE_STOPPED);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy(){
        ++generation;
        releasePlayer();
        releaseFocus();
        if(session!=null){session.setActive(false);session.release();session=null;}
        super.onDestroy();
    }
}
