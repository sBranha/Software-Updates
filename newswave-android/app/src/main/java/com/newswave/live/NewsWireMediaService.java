package com.newswave.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Standard Android Auto media source. Binding/browsing never starts audio.
 * Speech is rendered to a private WAV so Pause/Resume preserves the position.
 * The existing weather-map CarAppService remains independent.
 */
public class NewsWireMediaService extends MediaBrowserServiceCompat {
    private static final String CHANNEL="newswire-briefing",KEEP="keep",PLAY="play",PAUSE="pause",STOP="stop";
    private static final int NOTIFICATION_ID=71017;
    private static final long PREPARATION_TIMEOUT_MS=90_000,PAUSED_TIMEOUT_MS=5*60_000;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    final CarPlaybackState playback=new CarPlaybackState();
    private final String controlToken=UUID.randomUUID().toString();
    private final AudioAttributes attributes=new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    MediaSessionCompat session;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private int focusEpoch;
    private boolean hasFocus,foreground,closed,ttsReady,ttsFailed;
    private TextToSpeech tts;
    private MediaPlayer player;
    private Future<?> loading;
    private File rendered;
    private String speech,utterance,status="Choose Play My News or National News";
    private Bitmap artwork;
    private final Runnable preparationTimeout=()->fail("Briefing timed out. Check your connection and tap Play to retry.");
    private final Runnable pausedTimeout=()->stopPlayback("Stopped after five minutes paused. Tap Play for a new briefing.");

    @Override public void onCreate() {
        super.onCreate();
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        NotificationManager notifications=getSystemService(NotificationManager.class);
        if(notifications!=null){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"NewsWire spoken news",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Playback controls for briefings you choose to play");
            channel.setSound(null,null);notifications.createNotificationChannel(channel);
        }
        Drawable logo=getDrawable(R.drawable.ic_launcher);
        if(logo!=null){
            artwork=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
            logo.setBounds(0,0,256,256);logo.draw(new Canvas(artwork));
        }
        clearOldAudio();
        session=new MediaSessionCompat(this,"NewsWireAudio");
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS|MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        // A killed process must not resume spoken news simply because a receiver restarts it.
        session.setMediaButtonReceiver(null);
        session.setSessionActivity(openPhone());
        session.setCallback(new MediaSessionCompat.Callback(){
            @Override public void onPlay(){playCurrent();}
            @Override public void onPlayFromMediaId(String id,Bundle extras){select(id);}
            @Override public void onPlayFromSearch(String query,Bundle extras){select(CarBriefingText.search(query));}
            @Override public void onPrepare(){
                if(playback.phase()==CarPlaybackState.Phase.IDLE||playback.phase()==CarPlaybackState.Phase.STOPPED)
                    publish(PlaybackStateCompat.STATE_PAUSED,"Tap Play to load a fresh briefing");
            }
            @Override public void onPrepareFromMediaId(String id,Bundle extras){
                if(!CarBriefingText.playable(id))return;
                stopPlayback("Tap Play to load a fresh briefing");
                playback.load(id);playback.stop();setMetadata(0);publish(PlaybackStateCompat.STATE_PAUSED,status);
            }
            @Override public void onPause(){pauseByUser();}
            @Override public void onStop(){stopPlayback("Briefing stopped. Tap Play to hear the latest news.");}
            @Override public void onSeekTo(long position){
                if(player==null||!playback.ready())return;
                player.seekTo((int)Math.max(0,Math.min(player.getDuration(),position)));
                publish(stateCode(),status);
            }
        },main);
        setSessionToken(session.getSessionToken());
        setMetadata(0);
        publish(PlaybackStateCompat.STATE_NONE,status);
    }

    @Override public BrowserRoot onGetRoot(String name,int uid,Bundle hints) {
        if(!CarMediaCaller.allowed(this,name,uid))return null;
        Bundle extras=new Bundle();
        extras.putBoolean("android.media.browse.SEARCH_SUPPORTED",true);
        return new BrowserRoot(CarBriefingText.ROOT,extras);
    }
    @Override public void onLoadChildren(String parent,Result<List<MediaBrowserCompat.MediaItem>> result) {
        if(!CarBriefingText.ROOT.equals(parent)){result.sendResult(Collections.emptyList());return;}
        List<MediaBrowserCompat.MediaItem> items=new ArrayList<>();
        items.add(item(CarBriefingText.LOCAL,"Local news, NWS weather and alerts"));
        items.add(item(CarBriefingText.NATIONAL,"Source-attributed national headlines"));
        result.sendResult(items);
    }
    @Override public void onLoadItem(String id,Result<MediaBrowserCompat.MediaItem> result) {
        result.sendResult(CarBriefingText.playable(id)?item(id,"Fresh spoken news"):null);
    }
    @Override public void onSearch(String query,Bundle extras,Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(Collections.singletonList(item(CarBriefingText.search(query),"Fresh spoken news")));
    }
    private MediaBrowserCompat.MediaItem item(String id,String subtitle) {
        MediaDescriptionCompat.Builder description=new MediaDescriptionCompat.Builder()
            .setMediaId(id).setTitle(CarBriefingText.title(id)).setSubtitle(subtitle);
        if(artwork!=null)description.setIconBitmap(artwork);
        return new MediaBrowserCompat.MediaItem(description.build(),MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        // Only immutable notification intents created by this instance carry this nonce.
        if(intent!=null&&controlToken.equals(intent.getStringExtra("control"))){
            String action=intent.getAction();
            if(PLAY.equals(action))playCurrent();
            else if(PAUSE.equals(action))pauseByUser();
            else if(STOP.equals(action))stopPlayback("Briefing stopped");
        }
        if(!foreground)stopSelf(startId);
        return START_NOT_STICKY;
    }

    private void select(String id) {
        if(closed)return;
        if(!CarBriefingText.playable(id)){fail("Unknown briefing. Choose Play My News or National News.");return;}
        releaseWork();
        long token=playback.load(id);
        setMetadata(0);
        status="Loading fresh news…";
        if(!enterForeground())return;
        session.setActive(true);publish(PlaybackStateCompat.STATE_BUFFERING,status);
        main.postDelayed(preparationTimeout,PREPARATION_TIMEOUT_MS);
        initializeVoice();
        loadBriefing(token,id);
    }

    /** Separated so service tests can supply deterministic data without contacting news providers. */
    void loadBriefing(long token,String id) {
        loading=worker.submit(()->{
            try {
                WidgetAreaResolver.Area area=WidgetAreaResolver.resolve(this);
                MyAreaData.Snapshot snapshot=null;
                List<LocalNewspapers.Headline> headlines;
                if(CarBriefingText.NATIONAL.equals(id)){
                    RefreshBudget.begin(42_000);
                    try{headlines=LocalNewspapers.nationalHeadlines();}
                    finally{RefreshBudget.clear();}
                }else {
                    if(area==null)throw new IllegalStateException("Set your ZIP in NewsWire on the phone, or open it once with location enabled.");
                    snapshot=MyAreaData.load(area.latitude,area.longitude,area.city,area.region);
                    headlines=snapshot.headlines;
                }
                List<CarBriefingText.Story> stories=new ArrayList<>();
                if(headlines!=null)for(LocalNewspapers.Headline h:headlines)
                    if(NewsPreferences.allow(this,h.title))stories.add(new CarBriefingText.Story(h.title,h.source,h.publishedMillis));
                CarBriefingText.Weather weather=snapshot==null?null:new CarBriefingText.Weather(
                    snapshot.temperature,snapshot.condition,snapshot.wind,snapshot.conditionsAvailable,
                    snapshot.warningsAvailable,snapshot.observationAt,snapshot.warnings);
                String text=CarBriefingText.format(id,area==null?"":area.label,weather,stories,System.currentTimeMillis());
                main.post(()->dataReady(token,text));
            }catch(Exception error){
                String message=error instanceof IllegalStateException?error.getMessage():
                    "News is unavailable. Check the connection and tap Play to retry.";
                main.post(()->{if(!closed&&playback.current(token))fail(message);});
            }
        });
    }
    void dataReady(long token,String text) {
        if(closed||!playback.current(token))return;
        speech=text;synthesize(token);
    }
    private void initializeVoice() {
        if(tts!=null)return;
        tts=new TextToSpeech(this,result->main.post(()->{
            if(closed||tts==null)return;
            ttsReady=result==TextToSpeech.SUCCESS;
            if(ttsReady){
                int language=tts.setLanguage(Locale.US);
                ttsReady=language!=TextToSpeech.LANG_MISSING_DATA&&language!=TextToSpeech.LANG_NOT_SUPPORTED;
            }
            ttsFailed=!ttsReady;
            if(!ttsReady){fail("Phone voice unavailable. Check Android Text-to-speech settings on the phone.");return;}
            tts.setSpeechRate(.95f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){}
                @Override public void onDone(String id){main.post(()->rendered(id));}
                @Override public void onError(String id){main.post(()->{
                    if(id.equals(utterance))fail("Could not prepare the spoken briefing. Tap Play to retry.");
                });}
            });
            synthesize(playback.token());
        }));
    }
    private void synthesize(long token) {
        if(speech==null||utterance!=null)return;
        if(ttsFailed){fail("Phone voice unavailable. Check Android Text-to-speech settings.");return;}
        if(!ttsReady)return;
        if(speech.length()>=TextToSpeech.getMaxSpeechInputLength()){
            fail("Briefing is too long to speak. Tap Play to refresh.");return;
        }
        try {
            rendered=File.createTempFile("newswire-car-",".wav",getCacheDir());
            utterance="car_"+token+"_"+UUID.randomUUID();
            status="Preparing spoken news…";publish(stateCode(),status);
            if(tts.synthesizeToFile(speech,new Bundle(),rendered,utterance)!=TextToSpeech.SUCCESS)
                fail("Phone voice could not prepare audio. Tap Play to retry.");
        }catch(Exception error){fail("Could not save the spoken briefing. Check phone storage.");}
    }
    private void rendered(String id) {
        if(closed||!id.equals(utterance)||rendered==null)return;
        long token=playback.token();
        try {
            player=new MediaPlayer();
            MediaPlayer preparedPlayer=player;
            player.setAudioAttributes(attributes);
            player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(rendered.getAbsolutePath());
            player.setOnPreparedListener(ready->{
                if(closed||ready!=player||!playback.prepared(token,SystemClock.elapsedRealtime()))return;
                main.removeCallbacks(preparationTimeout);setMetadata(ready.getDuration());
                if(playback.wantsPlay())startPrepared();
                else {publish(PlaybackStateCompat.STATE_PAUSED,"Paused · tap Play to continue");schedulePausedTimeout();}
            });
            player.setOnCompletionListener(done->{
                if(done==player)stopPlayback("Briefing finished. Tap Play for the latest news.");
            });
            player.setOnErrorListener((failed,what,extra)->{
                if(failed==player)fail("Audio playback failed. Tap Play to retry.");return true;
            });
            preparedPlayer.prepareAsync();
        }catch(Exception error){fail("Could not open the spoken briefing. Tap Play to retry.");}
    }

    private void playCurrent() {
        if(closed)return;
        main.removeCallbacks(pausedTimeout);
        if(player!=null&&playback.fresh(SystemClock.elapsedRealtime())){
            playback.requestPlay();if(enterForeground())startPrepared();return;
        }
        if(playback.phase()==CarPlaybackState.Phase.BUFFERING||
                (!playback.ready()&&loading!=null&&playback.phase()==CarPlaybackState.Phase.PAUSED)){
            playback.requestPlay();publish(PlaybackStateCompat.STATE_BUFFERING,status);return;
        }
        select(playback.selection());
    }
    private void startPrepared() {
        if(player==null||!playback.wantsPlay())return;
        if(!playback.fresh(SystemClock.elapsedRealtime())){select(playback.selection());return;}
        if(!hasFocus&&!requestFocus())return;
        try {
            player.start();playback.playing();
            main.removeCallbacks(pausedTimeout);publish(PlaybackStateCompat.STATE_PLAYING,"Playing your news briefing");
        }catch(IllegalStateException error){fail("Audio could not start. Tap Play to retry.");}
    }
    private boolean requestFocus() {
        if(audio==null){fail("Phone audio is unavailable.");return false;}
        abandonFocus();
        final int epoch=++focusEpoch;
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes).setWillPauseWhenDucked(true).setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(change->{
                if(closed||epoch!=focusEpoch)return;
                if(change==AudioManager.AUDIOFOCUS_GAIN){
                    hasFocus=true;
                    if(playback.focusGain())startPrepared();
                }else if(change==AudioManager.AUDIOFOCUS_LOSS||
                        change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT||
                        change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){
                    hasFocus=false;pausePlayer();
                    boolean permanent=change==AudioManager.AUDIOFOCUS_LOSS;
                    playback.focusLoss(permanent);
                    publish(PlaybackStateCompat.STATE_PAUSED,permanent?"Paused for another app · tap Play to continue":
                        "Paused for navigation, a call or another audio interruption");
                    schedulePausedTimeout();
                    if(permanent)abandonFocus();
                }
            },main).build();
        int answer=audio.requestAudioFocus(focus);
        if(answer==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){hasFocus=true;return true;}
        if(answer==AudioManager.AUDIOFOCUS_REQUEST_DELAYED){
            playback.focusLoss(false);publish(PlaybackStateCompat.STATE_PAUSED,"Waiting for phone audio");
            schedulePausedTimeout();return false;
        }
        playback.pause();abandonFocus();
        publish(PlaybackStateCompat.STATE_PAUSED,"Another app is using audio · tap Play to try again");
        schedulePausedTimeout();return false;
    }
    private void abandonFocus() {
        focusEpoch++;
        if(focus!=null&&audio!=null)audio.abandonAudioFocusRequest(focus);
        focus=null;hasFocus=false;
    }
    private void pauseByUser() {
        playback.pause();pausePlayer();abandonFocus();
        publish(PlaybackStateCompat.STATE_PAUSED,"Paused · tap Play to continue");schedulePausedTimeout();
    }
    private void pausePlayer() {
        if(player!=null&&playback.ready())try {if(player.isPlaying())player.pause();}
        catch(IllegalStateException ignored){}
    }
    private void schedulePausedTimeout(){main.removeCallbacks(pausedTimeout);main.postDelayed(pausedTimeout,PAUSED_TIMEOUT_MS);}
    private void stopPlayback(String message) {
        playback.stop();releaseWork();publish(PlaybackStateCompat.STATE_STOPPED,message);leaveForeground();
    }
    private void fail(String message) {
        playback.fail();releaseWork();publish(PlaybackStateCompat.STATE_ERROR,message);leaveForeground();
    }
    private void releaseWork() {
        main.removeCallbacks(preparationTimeout);main.removeCallbacks(pausedTimeout);
        if(loading!=null){loading.cancel(true);loading=null;}
        utterance=null;speech=null;
        if(tts!=null)tts.stop();
        if(player!=null){player.release();player=null;}
        if(rendered!=null){rendered.delete();rendered=null;}
        abandonFocus();
    }
    private void setMetadata(long duration) {
        MediaMetadataCompat.Builder metadata=new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,playback.selection())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,CarBriefingText.title(playback.selection()))
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,"NewsWire spoken news")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,status)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,duration);
        if(artwork!=null)metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,artwork);
        session.setMetadata(metadata.build());
    }
    private int stateCode() {
        switch(playback.phase()){
            case PLAYING:return PlaybackStateCompat.STATE_PLAYING;
            case PAUSED:return PlaybackStateCompat.STATE_PAUSED;
            case STOPPED:return PlaybackStateCompat.STATE_STOPPED;
            case ERROR:return PlaybackStateCompat.STATE_ERROR;
            case BUFFERING:return PlaybackStateCompat.STATE_BUFFERING;
            default:return PlaybackStateCompat.STATE_NONE;
        }
    }
    private void publish(int code,String message) {
        status=message;
        long position=0;
        if(player!=null&&playback.ready())try {position=player.getCurrentPosition();}
        catch(IllegalStateException ignored){}
        long actions=PlaybackStateCompat.ACTION_PLAY|PlaybackStateCompat.ACTION_PLAY_PAUSE|
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID|PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH|
            PlaybackStateCompat.ACTION_PAUSE|PlaybackStateCompat.ACTION_STOP;
        if(playback.ready())actions|=PlaybackStateCompat.ACTION_SEEK_TO;
        PlaybackStateCompat.Builder state=new PlaybackStateCompat.Builder().setActions(actions)
            .setState(code,position,code==PlaybackStateCompat.STATE_PLAYING?1:0,SystemClock.elapsedRealtime());
        if(code==PlaybackStateCompat.STATE_ERROR)state.setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR,message);
        session.setPlaybackState(state.build());
        if(foreground)getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification());
    }
    private PendingIntent openPhone() {
        return PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private PendingIntent control(String action,int request) {
        return PendingIntent.getService(this,request,new Intent(this,NewsWireMediaService.class)
            .setAction(action).putExtra("control",controlToken),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        boolean active=playback.wantsPlay()||playback.phase()==CarPlaybackState.Phase.PLAYING;
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_news_audio)
            .setContentTitle(CarBriefingText.title(playback.selection())).setContentText(status)
            .setContentIntent(openPhone()).setLargeIcon(artwork).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(active).setDeleteIntent(control(STOP,3))
            .addAction(active?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,
                active?"Pause":"Play",control(active?PAUSE:PLAY,1))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop",control(STOP,2))
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1)).build();
    }
    private boolean enterForeground() {
        try {
            startForegroundService(new Intent(this,NewsWireMediaService.class).setAction(KEEP).putExtra("control",controlToken));
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIFICATION_ID,notification());
            foreground=true;return true;
        }catch(RuntimeException blocked){fail("Android could not start playback. Open NewsWire on the phone, then try Play again.");return false;}
    }
    private void leaveForeground(){foreground=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private void clearOldAudio() {
        File[] files=getCacheDir().listFiles((dir,name)->name.startsWith("newswire-car-")&&name.endsWith(".wav"));
        if(files!=null)for(File file:files)file.delete();
    }
    @Override public void onDestroy() {
        closed=true;playback.stop();releaseWork();worker.shutdownNow();
        if(tts!=null){tts.shutdown();tts=null;}
        main.removeCallbacksAndMessages(null);
        if(session!=null){session.setActive(false);session.release();}
        stopForeground(STOP_FOREGROUND_REMOVE);clearOldAudio();super.onDestroy();
    }
}
