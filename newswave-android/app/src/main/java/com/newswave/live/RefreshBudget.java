package com.newswave.live;

import java.io.IOException;

/** A monotonic, shared deadline across sequential calls on one worker thread. */
final class RefreshBudget {
    private static final ThreadLocal<Long> END=new ThreadLocal<>();
    static void begin(long millis){END.set(System.nanoTime()+millis*1_000_000L);}
    static void clear(){END.remove();}
    static int timeout(int ceilingMillis) throws IOException {
        if(Thread.currentThread().isInterrupted())throw new IOException("Request cancelled");
        Long end=END.get();
        if(end==null)return ceilingMillis;
        long remaining=(end-System.nanoTime())/1_000_000L;
        if(remaining<=0)throw new IOException("Refresh deadline reached");
        return (int)Math.max(1,Math.min(ceilingMillis,remaining));
    }
}
