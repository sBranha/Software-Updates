package com.newswave.live;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;
import android.os.Process;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media.MediaResultCapture;
import java.time.Duration;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
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
    @Before public void setUp(){controller=Robolectric.buildService(TestService.class).create();service=controller.get();}
    @After public void tearDown(){controller.destroy();}
    private void idle(){shadowOf(Looper.getMainLooper()).idle();}
    private void play(String id){service.session.getController().getTransportControls().playFromMediaId(id,null);idle();}

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
        assertEquals(PlaybackStateCompat.STATE_NONE,service.session.getController().getPlaybackState().getState());
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
        service.session.getController().getTransportControls().pause();idle();
        assertFalse(service.playback.wantsPlay());
        assertEquals(PlaybackStateCompat.STATE_PAUSED,service.session.getController().getPlaybackState().getState());
        service.session.getController().getTransportControls().stop();idle();
        assertFalse(service.playback.current(token));
        assertEquals(PlaybackStateCompat.STATE_STOPPED,service.session.getController().getPlaybackState().getState());
        service.dataReady(token,"Late news");idle();assertEquals(CarPlaybackState.Phase.STOPPED,service.playback.phase());
    }
    @Test public void switchingCarSelectionInvalidatesPreviousRequest(){
        play(CarBriefingText.LOCAL);long previous=service.requestToken;
        play(CarBriefingText.NATIONAL);
        assertEquals(2,service.loads);assertEquals(CarBriefingText.NATIONAL,service.requested);
        assertFalse(service.playback.current(previous));
        assertEquals("National News",service.session.getController().getMetadata().getDescription().getTitle());
    }
    @Test public void voicePlaySelectsNationalAndDefaultPlaySelectsLocal(){
        service.session.getController().getTransportControls().play();idle();
        assertEquals(CarBriefingText.LOCAL,service.requested);
        service.session.getController().getTransportControls().playFromSearch("national news",null);idle();
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
}
