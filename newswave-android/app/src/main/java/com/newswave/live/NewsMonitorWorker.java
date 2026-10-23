package com.newswave.live;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * User-configurable, phone-only local-news alerts.
 * Background checks are opportunistic (not guaranteed instant) and
 * never add popup controls to an Android Auto weather map.
 * Content is attributed RSS, not a proprietary breaking-news verification system.
 */
public final class NewsMonitorWorker extends Worker {
    private static final String CHANNEL_ID="newswire_smart_headlines";
    private static final String WORK_NAME="newswire-local-news-digest";
    private static final long MAX_STORY_AGE=12L*60*60*1000;
    private static final String[] TOPICS={"breaking","schools","local"};
    public NewsMonitorWorker(@NonNull Context context,@NonNull WorkerParameters params) {
        super(context,params);
    }
    public static void schedule(Context context,boolean enabled) {
        WorkManager work=WorkManager.getInstance(context);
        if(!enabled) {work.cancelUniqueWork(WORK_NAME);return;}
        Constraints constraints=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(
            NewsMonitorWorker.class,30,TimeUnit.MINUTES)
            .setConstraints(constraints).build();
        work.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request);
    }
    private static boolean qualifyingHeadline(String topic,String title) {
        String lower=title.toLowerCase(Locale.US);
        if("breaking".equals(topic))
            return lower.matches(".*\\b(breaking|emergency|evacuat\\w*|urgent|tornado warning|shooting|missing child)\\b.*");
        if("schools".equals(topic))
            return lower.matches(".*(school.*(clos\\w*|delay\\w*)|(clos\\w*|delay\\w*).*school).*");
        return true;
    }
    @NonNull @Override public Result doWork() {
        Context ctx=getApplicationContext();
        SharedPreferences settings=ctx.getSharedPreferences("newswire-news-alerts",Context.MODE_PRIVATE);
        if(!settings.getBoolean("enabled",false))return Result.success();
        SharedPreferences location=ctx.getSharedPreferences("newswire-location",Context.MODE_PRIVATE);
        String city=location.getString("city",""),region=location.getString("state","");
        if(city.isEmpty()||region.isEmpty())return Result.success();
        if(Build.VERSION.SDK_INT>=33
          &&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
              !=PackageManager.PERMISSION_GRANTED)return Result.success();
        NotificationManager manager=(NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
            "NewsWire smart news alerts",NotificationManager.IMPORTANCE_HIGH));
        String place=city+"|"+region;
        long now=System.currentTimeMillis();
        boolean feedSucceeded=false;
        for(int i=0;i<TOPICS.length;i++){
            String topic=TOPICS[i];
            if(!settings.getBoolean("topic_"+topic,true))continue;
            try{
                List<LocalNewspapers.Headline> articles=LocalNewspapers.topicHeadlines(
                    city,region,topic);
                feedSucceeded=true;
                LocalNewspapers.Headline chosen=null;
                for(LocalNewspapers.Headline story:articles){
                    if(story.publishedMillis<=0 || story.publishedMillis>now+300000
                        || now-story.publishedMillis>MAX_STORY_AGE)continue;
                    if(!qualifyingHeadline(topic,story.title))continue;
                    chosen=story;break;
                }
                if(chosen==null)continue;
                String key=place+"|"+chosen.url;
                String old=settings.getString("seen_"+topic,"");
                if(key.equals(old))continue;
                boolean seeded=settings.getBoolean("seeded_"+topic,false);
                String oldPlace=settings.getString("place_"+topic,"");
                settings.edit().putString("seen_"+topic,key).putString("place_"+topic,place)
                    .putBoolean("seeded_"+topic,true).apply();
                if(!seeded||!place.equals(oldPlace))continue; // never announce backlog
                long cooldown="breaking".equals(topic)?45*60*1000L:
                    "schools".equals(topic)?2*60*60*1000L:3*60*60*1000L;
                if(now-settings.getLong("alert_at_"+topic,0)<cooldown)continue;
                settings.edit().putLong("alert_at_"+topic,now).apply();
                Intent launch=new Intent(ctx,MainActivity.class);
                launch.putExtra("open_news",true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent tap=PendingIntent.getActivity(ctx,331+i,launch,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                String heading="breaking".equals(topic)?"Important local news near "+city:
                    "schools".equals(topic)?"School update near "+city:
                    "New local headline near "+city;
                Notification note=new NotificationCompat.Builder(ctx,CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(heading)
                    .setContentText(chosen.title)
                    .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(chosen.title+" · "+chosen.source))
                    .setContentIntent(tap).setAutoCancel(true)
                    .setPriority("breaking".equals(topic)?
                        NotificationCompat.PRIORITY_HIGH:NotificationCompat.PRIORITY_DEFAULT)
                    .build();
                manager.notify(331+i,note);
            }catch(Exception ex){
                android.util.Log.w("NewsWireNews","RSS alert check failed for "+topic,ex);
            }
        }
        return feedSucceeded?Result.success():Result.retry();
    }
}
