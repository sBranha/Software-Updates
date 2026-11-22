package com.newswave.live;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Behavior tests for the same state/formatting code used by the Android media service. */
public final class CarAudioTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        CarPlaybackState state=new CarPlaybackState();
        check(state.phase()==CarPlaybackState.Phase.IDLE&&!state.wantsPlay(),"Connecting must not autoplay");
        long first=state.load(CarBriefingText.LOCAL);
        check(state.wantsPlay()&&state.phase()==CarPlaybackState.Phase.BUFFERING,"Tap requests buffering");
        state.pause();check(!state.wantsPlay(),"Pause during loading cancels autoplay");
        check(state.prepared(first,100)&&state.phase()==CarPlaybackState.Phase.PAUSED,"Late preparation stays paused");
        state.requestPlay();state.playing();state.focusLoss(false);
        check(!state.wantsPlay()&&state.phase()==CarPlaybackState.Phase.PAUSED,"Navigation pauses");
        state.focusLoss(false);check(state.focusGain(),"Repeated navigation interruption still resumes");
        state.playing();state.focusLoss(false);state.pause();
        check(!state.focusGain(),"User pause prevents auto resume after a call");
        state.requestPlay();state.playing();state.focusLoss(true);
        check(!state.focusGain(),"Permanent loss does not resume");
        state.requestPlay();state.playing();state.focusLoss(false);state.stop();
        check(!state.focusGain()&&!state.current(first),"Stop invalidates pending focus and synthesis callbacks");
        long local=state.load(CarBriefingText.LOCAL),national=state.load(CarBriefingText.NATIONAL);
        check(!state.prepared(local,500)&&state.prepared(national,600),"Switching selection rejects old results");
        check(state.selection().equals(CarBriefingText.NATIONAL),"Selection persists");
        check(state.fresh(600)&&state.fresh(600+CarPlaybackState.RESUME_MAX_MS),"Fresh resume boundary");
        check(!state.fresh(599)&&!state.fresh(601+CarPlaybackState.RESUME_MAX_MS),"Expired or invalid-clock briefing reloads");
        state.fail();check(!state.current(national)&&!state.wantsPlay()&&!state.ready(),"Failure invalidates all pending work");
        boolean rejected=false;try{state.load("arbitrary-url");}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Only fixed media IDs accepted");
        check(CarBriefingText.NATIONAL.equals(CarBriefingText.search("play NATIONAL news")),"Voice search national");
        check(CarBriefingText.LOCAL.equals(CarBriefingText.search(null)),"Default voice play is local");
        long now=1_790_000_000_000L;
        List<CarBriefingText.Story> stories=Arrays.asList(
            new CarBriefingText.Story("Fresh report - WAFF","WAFF",now-60_000),
            new CarBriefingText.Story("Too old","Old",now-NewsFreshness.DAY-1),
            new CarBriefingText.Story("Future report","Future",now+1),
            new CarBriefingText.Story("No date","Unknown",0));
        String unknown=CarBriefingText.format(CarBriefingText.LOCAL,"Albertville",null,stories,now);
        check(unknown.contains("WAFF. Fresh report. Published 1 min ago"),"Attribution, suffix cleanup and age spoken");
        check(!unknown.contains("Too old")&&!unknown.contains("Future report")&&!unknown.contains("No date"),"Reject stale/future/undated news");
        check(unknown.contains("lookup unavailable")&&!unknown.contains("no active alerts"),"Unknown warnings are never all-clear");
        CarBriefingText.Weather estimate=new CarBriefingText.Weather("71°F","Sunny","—",true,true,0,Collections.emptyList());
        String estimated=CarBriefingText.format(CarBriefingText.LOCAL,"Albertville",estimate,Collections.emptyList(),now);
        check(estimated.contains("Estimated conditions: 71 degrees Fahrenheit")&&!estimated.contains("Wind —"),"Forecast and missing wind wording");
        check(estimated.contains("no active alerts at this check")&&estimated.contains("No recent local headlines"),"Successful empty feeds described precisely");
        CarBriefingText.Weather warning=new CarBriefingText.Weather("71°F","Rain","5 mph",true,true,now-600_000,
            Arrays.asList("Tornado Warning","Flash Flood Warning","Severe Thunderstorm Warning","Other"));
        String danger=CarBriefingText.format(CarBriefingText.LOCAL,"Albertville",warning,stories,now);
        check(danger.contains("Station weather observation")&&danger.contains("Observed at"),"Station time disclosed");
        check(danger.contains("Tornado Warning")&&danger.contains("Follow official warning instructions")&&!danger.contains("Other"),"Warnings bounded, instructions retained");
        List<CarBriefingText.Story> longStories=new ArrayList<>();
        for(int i=0;i<30;i++)longStories.add(new CarBriefingText.Story("Z".repeat(20_000),"S".repeat(2000),now));
        String bounded=CarBriefingText.format(CarBriefingText.LOCAL,"A".repeat(30_000),warning,longStories,now);
        check(bounded.length()<=CarBriefingText.MAX_CHARS,"Untrusted feed strings cannot exceed TTS budget");
        String nationalText=CarBriefingText.format(CarBriefingText.NATIONAL,"private place",warning,stories,now);
        check(nationalText.contains("National headline")&&!nationalText.contains("private place")&&!nationalText.contains("Tornado"),"National briefing needs no local weather/location");
        check(!CarBriefingText.clean("<b>Hello</b>\nworld",100).contains("<"),"Clean source markup/control characters");
        System.out.println("Car audio behavior tests PASS ("+checks+" checks)");
    }
}
