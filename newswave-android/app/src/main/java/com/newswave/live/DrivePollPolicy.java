package com.newswave.live;

/** Decisions are independent of whether the latest accurate GPS fix moved. */
final class DrivePollPolicy {
    static final long INTERVAL_MS=90_000L,MAX_FIX_AGE_MS=120_000L;
    static boolean due(boolean active,boolean inFlight,long now,long previous,
                       long fixAgeMillis,float accuracy) {
        return active&&!inFlight&&fixAgeMillis>=0&&fixAgeMillis<=MAX_FIX_AGE_MS
            &&accuracy>=0&&accuracy<=150f
            &&(previous==0||now-previous>=INTERVAL_MS);
    }
}
