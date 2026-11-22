package com.newswave.live;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;
import android.os.Process;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media.MediaResultCapture;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.ShadowTextToSpeech;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.shadows.util.DataSource;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,36})
@LooperMode(LooperMode.Mode.PAUSED)
public class NewsWireMediaServiceTest {
    public static class TestService extends NewsWireMediaService {
        int loads;
        String requested;
        long requestToken;
        @Override void loadBriefing(long token,String id){loads++;requested=id;requestToken=token;}
    }
    private ServiceController<TestService> controller;
    private TestService service;
    private MediaPlayer player;
    @Before public void setUp(){
        controller=Robolectric.buildService(TestService.class).create();service=controller.get();
        ShadowTextToSpeech.addLanguageAvailability(Locale.US);
        ShadowMediaPlayer.setCreateListener((created,shadow)->player=created);
        ShadowMediaPlayer.setMediaInfoProvider(source->new ShadowMediaPlayer.MediaInfo(120_000,0));
    }
    @After public void tearDown(){controller.destroy();}
    private void idle(){shadowOf(Looper.getMainLooper()).idle();}
    // Robolectric doesn't deliver framework media Binder transport calls. Exercise the
    // callback actually registered on MediaSessionCompat, not a separate fake callback.
    private MediaSessionCompat.Callback controls(){return ReflectionHelpers.getField(
        ReflectionHelpers.getField(service.session,"mImpl"),"mCallback");}
    private void play(String id){controls().onPlayFromMediaId(id,null);idle();}
    private File prepareSpeech(){
        TextToSpeech voice=ShadowTextToSpeech.getLastTextToSpeechInstance();assertNotNull(voice);
        ShadowTextToSpeech speech=shadowOf(voice);
        speech.simulateSynthesizeToFileResult(TextToSpeech.SUCCESS);
        speech.getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();
        service.dataReady(service.requestToken,"NewsWire. Test briefing.");
        // The TTS shadow registers a one-second clip; give this test a two-minute
        // clip before the posted rendering callback opens it with MediaPlayer.
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(speech.getLastSynthesizeToFile().getAbsolutePath()),
            new ShadowMediaPlayer.MediaInfo(120_000,0));
        idle();
        return speech.getLastSynthesizeToFile();
    }

    @Test public void browsingIsPlayableAndNeverStartsAudio(){
        assertNotNull(service.onGetRoot(service.getPackageName(),Process.myUid(),null));
        MediaResultCapture<List<MediaBrowserCompat.MediaItem>> result=new MediaResultCapture<>();
        service.onLoadChildren(CarBriefingText.ROOT,result);
        assertEquals(2,result.value.size());
        assertEquals(CarBriefingText.LOCAL,result.value.get(0).getMediaId());
        assertEquals("Play My News",result.value.get(0).getDescription().getTitle());
        assertTrue(result.value.get(0).isPlayable());
        assertEquals(CarBriefingText.NATIONAL,result.value.get(1).getMediaId());
        assertEquals(0,service.loads);
        assertEquals(PlaybackStateCompat.STATE_STOPPED,service.session.getController().getPlaybackState().getState());
        assertFalse(service.session.isActive());
    }
    @Test public void unknownBrowserIsDenied(){
        assertNull(service.onGetRoot("unrelated.app",Process.myUid()+1,null));
        assertNull(service.onGetRoot("com.google.android.projection.gearhead",Process.myUid(),null));
    }
    @Test public void carPlayAndPauseAndStopReachTheService(){
        play(CarBriefingText.LOCAL);
        assertEquals(1,service.loads);assertEquals(CarBriefingText.LOCAL,service.requested);
        assertEquals(PlaybackStateCompat.STATE_BUFFERING,service.session.getController().getPlaybackState().getState());
        long token=service.requestToken;
        controls().onPause();idle();
        assertFalse(service.playback.wantsPlay());
        assertEquals(PlaybackStateCompat.STATE_PAUSED,service.session.getController().getPlaybackState().getState());
        controls().onStop();idle();
        assertFalse(service.playback.current(token));
        assertEquals(PlaybackStateCompat.STATE_STOPPED,service.session.getController().getPlaybackState().getState());
        service.dataReady(token,"Late news");idle();assertEquals(CarPlaybackState.Phase.STOPPED,service.playback.phase());
    }
    @Test public void switchingCarSelectionInvalidatesPreviousRequest(){
        play(CarBriefingText.LOCAL);long previous=service.requestToken;
        play(CarBriefingText.NATIONAL);
        assertEquals(2,service.loads);assertEquals(CarBriefingText.NATIONAL,service.requested);
        assertFalse(service.playback.current(previous));
        MediaMetadataCompat metadata=ReflectionHelpers.getField(ReflectionHelpers.getField(service.session,"mImpl"),"mMetadata");
        assertEquals("National News",metadata.getDescription().getTitle());
    }
    @Test public void voicePlaySelectsNationalAndDefaultPlaySelectsLocal(){
        controls().onPlay();idle();
        assertEquals(CarBriefingText.LOCAL,service.requested);
        controls().onPlayFromSearch("national news",null);idle();
        assertEquals(CarBriefingText.NATIONAL,service.requested);
    }
    @Test public void unknownIdCannotPlayAnArbitraryUrl(){
        play("https://untrusted.invalid/audio");assertEquals(0,service.loads);
        assertEquals(PlaybackStateCompat.STATE_ERROR,service.session.getController().getPlaybackState().getState());
    }
    @Test public void preparationHasABoundedTimeout(){
        play(CarBriefingText.LOCAL);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(91));
        assertEquals(PlaybackStateCompat.STATE_ERROR,service.session.getController().getPlaybackState().getState());
        assertFalse(service.playback.wantsPlay());
    }
    @Test public void unsolicitedStartIntentCannotStartPlayback(){
        int restart=service.onStartCommand(new Intent(service,NewsWireMediaService.class).setAction("play"),0,1);
        assertEquals(android.app.Service.START_NOT_STICKY,restart);
        assertEquals(0,service.loads);assertFalse(service.playback.wantsPlay());
    }
    @Test public void searchBrowseDoesNotAutoplay(){
        MediaResultCapture<List<MediaBrowserCompat.MediaItem>> result=new MediaResultCapture<>();
        service.onSearch("national news",null,result);
        assertEquals(CarBriefingText.NATIONAL,result.value.get(0).getMediaId());assertEquals(0,service.loads);
    }
    @Test public void destroyingServiceInvalidatesWork(){
        play(CarBriefingText.LOCAL);long token=service.requestToken;
        controller.destroy();assertFalse(service.playback.current(token));
        controller=Robolectric.buildService(TestService.class).create();service=controller.get();
        assertFalse(service.playback.wantsPlay());assertEquals(0,service.loads);
    }
    @Test public void preparedSpeechPlaysPausesAtPositionAndCleansUp(){
        play(CarBriefingText.LOCAL);File wav=prepareSpeech();
        assertNotNull(wav);assertTrue(wav.isFile());assertNotNull(player);assertTrue(player.isPlaying());
        assertEquals(PlaybackStateCompat.STATE_PLAYING,service.session.getController().getPlaybackState().getState());
        MediaMetadataCompat metadata=ReflectionHelpers.getField(ReflectionHelpers.getField(service.session,"mImpl"),"mMetadata");
        assertEquals("Playing your news briefing",metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE));
        controls().onSeekTo(30_000);idle();controls().onPause();idle();
        assertFalse(player.isPlaying());assertEquals(30_000,player.getCurrentPosition());
        controls().onPlay();idle();assertTrue(player.isPlaying());assertEquals(30_000,player.getCurrentPosition());
        assertEquals(1,service.loads);
        controls().onStop();idle();assertFalse(wav.exists());
        assertEquals(ShadowMediaPlayer.State.END,shadowOf(player).getState());
    }
    @Test public void navigationPausesAndResumesWithoutLosingPosition(){
        play(CarBriefingText.LOCAL);prepareSpeech();controls().onSeekTo(10_000);idle();
        AudioManager audio=(AudioManager)service.getSystemService(android.content.Context.AUDIO_SERVICE);
        AudioManager.OnAudioFocusChangeListener focus=shadowOf(audio).getLastAudioFocusRequest().listener;
        assertNotNull(focus);
        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);idle();assertFalse(player.isPlaying());
        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);idle();assertTrue(player.isPlaying());
        assertEquals(10_000,player.getCurrentPosition());
        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);idle();
        controls().onPause();idle();focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);idle();
        assertFalse(player.isPlaying());assertFalse(service.playback.wantsPlay());
    }
    @Test public void pauseWhileRenderingDoesNotPlayWhenPreparationFinishes(){
        play(CarBriefingText.LOCAL);controls().onPause();idle();prepareSpeech();
        assertNotNull(player);assertFalse(player.isPlaying());assertFalse(service.playback.wantsPlay());
        assertEquals(PlaybackStateCompat.STATE_PAUSED,service.session.getController().getPlaybackState().getState());
    }
    @Test public void completionRemovesPrivateAudioAndStops(){
        play(CarBriefingText.NATIONAL);File wav=prepareSpeech();
        shadowOf(player).invokeCompletionListener();idle();
        assertFalse(wav.exists());assertEquals(CarPlaybackState.Phase.STOPPED,service.playback.phase());
    }
    @Test public void failedSpeechIsAnErrorAndNeverPlays(){
        play(CarBriefingText.LOCAL);
        TextToSpeech voice=ShadowTextToSpeech.getLastTextToSpeechInstance();
        ShadowTextToSpeech speech=shadowOf(voice);speech.simulateSynthesizeToFileResult(TextToSpeech.ERROR_SYNTHESIS);
        speech.getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();
        service.dataReady(service.requestToken,"Test briefing");idle();
        assertEquals(CarPlaybackState.Phase.ERROR,service.playback.phase());assertNull(player);
        assertFalse(speech.getLastSynthesizeToFile().exists());
    }
    @Test public void audioDisconnectPausesWithoutAutomaticResume(){
        play(CarBriefingText.LOCAL);prepareSpeech();
        service.sendBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));idle();
        assertFalse(player.isPlaying());assertFalse(service.playback.wantsPlay());
        assertEquals(PlaybackStateCompat.STATE_PAUSED,service.session.getController().getPlaybackState().getState());
    }
    @Test public void lastCarBrowserDisconnectPausesTheBriefing(){
        play(CarBriefingText.LOCAL);prepareSpeech();
        service.onUnbind(new Intent("android.media.browse.MediaBrowserService"));idle();
        assertFalse(player.isPlaying());assertFalse(service.playback.wantsPlay());
    }
}
