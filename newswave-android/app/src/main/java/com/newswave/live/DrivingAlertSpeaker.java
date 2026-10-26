package com.newswave.live;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;

/**
 * Optional phone-side speech for explicitly enabled Drive Alerts.
 * Transient audio focus requests a pause from other compliant media apps,
 * and abandoning focus permits them to resume. Some players may behave differently.
 * No Android Auto audio or route-integration guarantee is implied.
 */
final class DrivingAlertSpeaker implements TextToSpeech.OnInitListener {
    private static final String TAG="NewsWireDriveVoice";
    private static final long MAX_SPEECH_MS=25000L;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final AudioAttributes attributes=new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private TextToSpeech tts;
    private AudioFocusRequest focus;
    private boolean ready=false,closed=false;
    private String pending=null,currentId=null;
    private int sequence=0;

    DrivingAlertSpeaker(Context app){
        audio=(AudioManager)app.getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
        tts=new TextToSpeech(app.getApplicationContext(),this);
    }

    @Override public void onInit(int status){
        main.post(()->{
            if(closed||tts==null)return;
            ready=status==TextToSpeech.SUCCESS;
            if(!ready){Log.w(TAG,"Speech engine unavailable; visual alert remains active");return;}
            if(tts.setLanguage(Locale.US)==TextToSpeech.LANG_MISSING_DATA
                || tts.isLanguageAvailable(Locale.US)==TextToSpeech.LANG_NOT_SUPPORTED){
                ready=false;
                Log.w(TAG,"US English speech data unavailable");
                return;
            }
            tts.setSpeechRate(1.02f);
            tts.setAudioAttributes(attributes);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){}
                @Override public void onDone(String id){main.post(()->finish(id));}
                @Override public void onError(String id){main.post(()->finish(id));}
            });
            if(pending!=null){String message=pending;pending=null;begin(message);}
        });
    }

    void announce(String message){
        main.post(()->{
            if(closed||message==null||message.isEmpty())return;
            if(!ready){pending=message;return;}
            begin(message);
        });
    }

    private void begin(String message){
        if(closed||tts==null)return;
        if(currentId!=null){
            tts.stop();
            currentId=null;
            abandonFocus();
        }
        if(audio==null)return;
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(change->{
                // Notification remains present if another call or app takes audio focus.
                if(change==AudioManager.AUDIOFOCUS_LOSS){
                    main.post(()->{if(tts!=null)tts.stop();finish(currentId);});
                }
            }).build();
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            focus=null;
            Log.w(TAG,"Audio focus denied; notification delivered without speech");
            return;
        }
        String id="newswire-drive-alert-"+(++sequence);
        currentId=id;
        if(tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,id)!=TextToSpeech.SUCCESS){
            finish(id);return;
        }
        main.postDelayed(()->finish(id),MAX_SPEECH_MS);
    }

    private void finish(String id){
        if(id==null||!id.equals(currentId))return;
        currentId=null;
        abandonFocus();
    }

    private void abandonFocus(){
        if(focus!=null&&audio!=null){
            audio.abandonAudioFocusRequest(focus);
            focus=null;
        }
    }

    void shutdown(){
        main.post(()->{
            closed=true;pending=null;
            if(tts!=null){tts.stop();tts.shutdown();tts=null;}
            currentId=null;
            abandonFocus();
        });
    }
}
