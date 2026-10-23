package com.newswave.live;

import android.Manifest;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.Calendar;

/** Opt-in once-per-day morning notification. WorkManager cannot guarantee an exact minute.
 * Checks every two hours and sends in the local morning window, once per date.
 * Never claims the absence of weather alerts if NWS is unavailable.
 */
public final class MorningBriefingWorker extends Worker {
    private static final String NAME="newswire-good-morning";
    private static final String CHANNEL="newswire_morning_brief";
    private static final String PREF="newswire-morning";
    public MorningBriefingWorker(@NonNull Context ctx,@NonNull WorkerParameters p){super(ctx,p);}
    static boolean enabled(Context ctx){
        return ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("enabled",false);
    }
    static void configure(Context ctx,boolean enable){
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putBoolean("enabled",enable).apply();
        WorkManager work=WorkManager.getInstance(ctx);
        if(!enable){work.cancelUniqueWork(NAME);return;}
        Constraints requirements=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(
            MorningBriefingWorker.class,2,TimeUnit.HOURS)
            .setConstraints(requirements).build();
        work.enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.UPDATE,request);
    }
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();
        SharedPreferences pref=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(!pref.getBoolean("enabled",false))return Result.success();
        if(Build.VERSION.SDK_INT>=33 &&ctx.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            return Result.success();
        Calendar local=Calendar.getInstance();
        int hour=local.get(Calendar.HOUR_OF_DAY);
        // Schedule is approximate, from background Android constraints.
        if(hour<6||hour>11)return Result.success();
        String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        if(day.equals(pref.getString("last_day","")))return Result.success();
        SharedPreferences location=ctx.getSharedPreferences("newswire-location",Context.MODE_PRIVATE);
        String city=location.getString("city",""),state=location.getString("state","");
        double lat=Double.longBitsToDouble(location.getLong("lat",
            Double.doubleToLongBits(Double.NaN)));
        double lon=Double.longBitsToDouble(location.getLong("lon",
            Double.doubleToLongBits(Double.NaN)));
        if(Double.isNaN(lat)||Double.isNaN(lon))return Result.success();
        try{
            MyAreaData.Snapshot data=MyAreaData.load(lat,lon,city,state);
            String title="Good morning"+(city.isEmpty()?"":", "+city)+" · NewsWire";
            StringBuilder body=new StringBuilder();
            if(data.conditionsAvailable)body.append(data.temperature).append(" · ")
                .append(data.condition).append(". ");
            else body.append("Weather observations unavailable. ");
            if(!data.warningsAvailable)body.append("NWS alerts unavailable; check official warnings. ");
            else if(!data.warnings.isEmpty())
                body.append("NWS: ").append(data.warnings.get(0)).append(". ");
            if(!data.headlines.isEmpty())
                body.append("Local: ").append(data.headlines.get(0).title);
            else body.append("Tap for local headlines and today's weather.");
            Intent open=new Intent(ctx,MorningBriefingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap=PendingIntent.getActivity(ctx,643,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            NotificationManager manager=(NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Good Morning NewsWire",NotificationManager.IMPORTANCE_DEFAULT));
            manager.notify(642,new NotificationCompat.Builder(ctx,CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher).setContentTitle(title)
                .setContentText(body.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString()))
                .setContentIntent(tap).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).build());
            pref.edit().putString("last_day",day).apply();
            return Result.success();
        }catch(Exception ex){
            android.util.Log.w("NewsWireMorning","Morning briefing unavailable",ex);
            return Result.retry();
        }
    }
}