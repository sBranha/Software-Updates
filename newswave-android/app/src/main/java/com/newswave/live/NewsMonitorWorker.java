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
import java.util.concurrent.TimeUnit;

/** Opt-in, rate-limited local-headline notifications, shown on the phone.
 * Never overlays the Android Auto weather map or acts as a driving warning.
 */
public final class NewsMonitorWorker extends Worker {
    private static final String CHANNEL_ID="newswire_local_headlines";
    private static final String WORK_NAME="newswire-local-news-digest";
    private static final long MIN_ALERT_GAP=4L*60*60*1000;
    public NewsMonitorWorker(@NonNull Context context,@NonNull WorkerParameters params) {
        super(context,params);
    }
    public static void schedule(Context context,boolean enabled) {
        WorkManager work=WorkManager.getInstance(context);
        if(!enabled) {work.cancelUniqueWork(WORK_NAME);return;}
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(NewsMonitorWorker.class,60,TimeUnit.MINUTES)
            .setConstraints(constraints).build();
        work.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.KEEP,request);
    }
    @NonNull @Override public Result doWork() {
        Context ctx=getApplicationContext();
        SharedPreferences settings=ctx.getSharedPreferences("newswire-news-alerts",Context.MODE_PRIVATE);
        if(!settings.getBoolean("enabled",false))return Result.success();
        SharedPreferences location=ctx.getSharedPreferences("newswire-location",Context.MODE_PRIVATE);
        String city=location.getString("city",""),region=location.getString("state","");
        if(city.isEmpty()||region.isEmpty())return Result.success();
        if(Build.VERSION.SDK_INT>=33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            return Result.success();
        String place=city+"|"+region;
        try {
            List<LocalNewspapers.Headline> headlines=LocalNewspapers.headlines(city,region);
            if(headlines.isEmpty())return Result.success();
            LocalNewspapers.Headline latest=headlines.get(0);
            String current=String.valueOf(latest.url.hashCode());
            String key=place+"|"+current;
            String previous=settings.getString("last_seen_key","");
            if(key.equals(previous))return Result.success();
            long now=System.currentTimeMillis();
            boolean baseline=settings.getBoolean("seeded",false);
            String oldPlace=settings.getString("place","");
            settings.edit().putString("last_seen_key",key).putString("place",place)
                .putBoolean("seeded",true).apply();
            if(!baseline||!place.equals(oldPlace))return Result.success();
            if(now-settings.getLong("last_alert_at",0)<MIN_ALERT_GAP)return Result.success();
            settings.edit().putLong("last_alert_at",now).apply();
            NotificationManager manager=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                "NewsWire local headlines",NotificationManager.IMPORTANCE_DEFAULT));
            Intent launch=new Intent(ctx,MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap=PendingIntent.getActivity(ctx,331,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            String title="New local headline near "+city;
            Notification note=new NotificationCompat.Builder(ctx,CHANNEL_ID)
                .setSmallIcon(com.newswave.live.R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(latest.title)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(latest.title+" · "+latest.source))
                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).build();
            manager.notify(331,note);
            return Result.success();
        }catch(Exception ex){android.util.Log.w("NewsWireNews","RSS digest unavailable",ex);return Result.retry();}
    }
}
