package com.newswave.live;

/** Main-thread playback decisions, independent of Android and of asynchronous engines. */
final class CarPlaybackState {
    static final long RESUME_MAX_MS=15*60*1000L;
    enum Phase { IDLE, BUFFERING, PLAYING, PAUSED, STOPPED, ERROR }
    private long generation;
    private long preparedAt;
    private boolean prepared,playRequested,resumeAfterFocus;
    private String selection=CarBriefingText.LOCAL;
    private Phase phase=Phase.IDLE;

    long load(String id) {
        if(!CarBriefingText.playable(id))throw new IllegalArgumentException("Unknown briefing");
        generation++;selection=id;prepared=false;playRequested=true;resumeAfterFocus=false;
        phase=Phase.BUFFERING;return generation;
    }
    long token(){return generation;}
    boolean current(long token){return token==generation;}
    String selection(){return selection;}
    Phase phase(){return phase;}
    boolean wantsPlay(){return playRequested;}
    boolean ready(){return prepared;}
    boolean prepared(long token,long now) {
        if(!current(token))return false;
        prepared=true;preparedAt=now;
        phase=playRequested?Phase.BUFFERING:Phase.PAUSED;return true;
    }
    boolean fresh(long now){return prepared&&now>=preparedAt&&now-preparedAt<=RESUME_MAX_MS;}
    void requestPlay(){playRequested=true;resumeAfterFocus=false;phase=Phase.BUFFERING;}
    void playing(){phase=Phase.PLAYING;}
    void pause(){playRequested=false;resumeAfterFocus=false;phase=Phase.PAUSED;}
    void focusLoss(boolean permanent) {
        resumeAfterFocus=!permanent&&(resumeAfterFocus||playRequested||phase==Phase.PLAYING);
        playRequested=false;phase=Phase.PAUSED;
    }
    boolean focusGain() {
        if(!resumeAfterFocus)return false;
        resumeAfterFocus=false;playRequested=true;phase=Phase.BUFFERING;return true;
    }
    void stop(){clear(Phase.STOPPED);}
    void fail(){clear(Phase.ERROR);}
    private void clear(Phase next) {
        generation++;prepared=false;playRequested=false;resumeAfterFocus=false;phase=next;
    }
}
