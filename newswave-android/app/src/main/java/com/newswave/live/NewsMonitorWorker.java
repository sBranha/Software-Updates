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
import androidx.work.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Opt-in periodic alerts. Android may delay work; this is not push delivery. */
public final class NewsMonitorWorker extends Worker {
    private static final String IMPORTANT="newswire_important_news_v2";
    private static final String DIGEST="newswire_news_digest_v2";
    private static final String WORK_NAME="newswire-local-news-digest";
    private static final String[] TOPICS={"breaking","schools","local","national"};
    public NewsMonitorWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    public static void schedule(Context context,boolean enabled){
        WorkManager work=WorkManager.getInstance(context);
        if(!enabled){work.cancelUniqueWork(WORK_NAME);return;}
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(NewsMonitorWorker.class,15,TimeUnit.MINUTES)
            .setConstraints(constraints).build();
        work.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request);
    }
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();
        SharedPreferences settings=ctx.getSharedPreferences("newswire-news-alerts",Context.MODE_PRIVATE);
        if(!settings.getBoolean("enabled",false))return Result.success();
        if(Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            !=PackageManager.PERMISSION_GRANTED)return Result.success();
        NotificationManager manager=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(IMPORTANT,"Important NewsWire news",NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(DIGEST,"NewsWire headline digest",NotificationManager.IMPORTANCE_DEFAULT));
        WidgetAreaResolver.Area area=WidgetAreaResolver.resolve(ctx);
        String city=area==null?"":area.city,region=area==null?"":area.region;
        boolean succeeded=false,attempted=false;
        for(int i=0;i<TOPICS.length;i++){
            String topic=TOPICS[i];boolean national="national".equals(topic);
            if(!settings.getBoolean("topic_"+topic,true))continue;
            if(!national&&(city.isEmpty()||region.isEmpty()))continue;
            attempted=true;
            try{
                List<LocalNewspapers.Headline> stories=national?LocalNewspapers.nationalHeadlines():
                    LocalNewspapers.topicHeadlines(city,region,topic);
                succeeded=true;
                String place=national?"US":city+"|"+region;
                String state="v2_"+topic+"_"+place;
                Set<String> seen=new HashSet<>(settings.getStringSet(state+"_seen",Collections.emptySet()));
                if(!settings.getBoolean(state+"_seeded",false)){
                    for(LocalNewspapers.Headline h:stories)seen.add(h.url);
                    settings.edit().putStringSet(state+"_seen",seen).putBoolean(state+"_seeded",true).apply();
                    continue;
                }
                long now=System.currentTimeMillis();
                long cooldown=("breaking".equals(topic)||national)?45*60*1000L:
                    "schools".equals(topic)?2*60*60*1000L:3*60*60*1000L;
                for(LocalNewspapers.Headline h:stories){
                    String suffix=" - "+h.source;
                    String filterTitle=h.title.endsWith(suffix)?h.title.substring(0,h.title.length()-suffix.length()):h.title;
                    if(!NewsAlertPolicy.qualifies(topic,filterTitle)
                        ||!NewsAlertPolicy.deliverable(h.publishedMillis,now,
                            settings.getLong(state+"_delivered",0),cooldown,seen.contains(h.url)))continue;
                    boolean urgent="breaking".equals(topic)||national;
                    notify(ctx,manager,331+i,urgent,national?"Important national news":
                        "schools".equals(topic)?"School update near "+city:"breaking".equals(topic)?
                        "Important local news near "+city:"New local headline near "+city,
                        h.title+" · "+h.source,null);
                    // Only actual delivery consumes a story; cooldown candidates remain eligible.
                    seen.add(h.url);
                    if(seen.size()>200){
                        Set<String> currentUrls=new HashSet<>();
                        for(LocalNewspapers.Headline currentStory:stories)currentUrls.add(currentStory.url);
                        seen.retainAll(currentUrls);
                    }
                    settings.edit().putStringSet(state+"_seen",seen).putLong(state+"_delivered",now).apply();
                    break;
                }
            }catch(Exception error){android.util.Log.w("NewsWireNews","News check unavailable: "+topic,error);}
        }
        if(settings.getBoolean("topic_faa",true)){
            attempted=true;
            try{
                List<FaaStatusParser.GroundStop> stops=FaaStatusParser.parse(faaXml(),System.currentTimeMillis());
                Set<String> previous=new HashSet<>(settings.getStringSet("faa_active",Collections.emptySet()));
                Set<String> current=new HashSet<>();
                for(FaaStatusParser.GroundStop stop:stops)current.add(stop.key);
                if(settings.getBoolean("faa_seeded",false)){
                    for(FaaStatusParser.GroundStop stop:stops)if(!previous.contains(stop.key)){
                        notify(ctx,manager,340,true,stop.headline(),
                            "Official FAA NAS status"+(stop.end.isEmpty()?"":" · scheduled end "+stop.end)
                            +". Check FAA for details. An airport stop does not mean a nationwide stop.",
                            "https://nasstatus.faa.gov/");
                    }
                }
                settings.edit().putBoolean("faa_seeded",true).putStringSet("faa_active",current)
                    .putLong("faa_checked_at",System.currentTimeMillis()).apply();
                succeeded=true;
            }catch(Exception error){android.util.Log.w("NewsWireNews","Official FAA status unavailable",error);}
        }
        if(succeeded)settings.edit().putLong("checked_at",System.currentTimeMillis()).apply();
        return !attempted||succeeded?Result.success():Result.retry();
    }
    private static void notify(Context ctx,NotificationManager manager,int id,boolean urgent,
                               String title,String detail,String url){
        Intent launch=url==null?new Intent(ctx,MainActivity.class).putExtra("open_news",true):
            new Intent(ctx,ArticleReaderActivity.class).putExtra("url",url).putExtra("title","Official FAA NAS status");
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap=PendingIntent.getActivity(ctx,id,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.notify(id,new NotificationCompat.Builder(ctx,urgent?IMPORTANT:DIGEST)
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(detail)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(detail)).setContentIntent(tap)
            .setPriority(urgent?NotificationCompat.PRIORITY_HIGH:NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true).build());
    }
    private static String faaXml() throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL("https://nasstatus.faa.gov/api/airport-status-information").openConnection();
        try{
            conn.setConnectTimeout(7000);conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent","NewsWire/0.7.16 (+https://github.com/sBranha/Software-Updates)");
            if(conn.getResponseCode()!=200)throw new java.io.IOException("FAA HTTP "+conn.getResponseCode());
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()){
                byte[] block=new byte[4096];int n;
                while((n=in.read(block))!=-1){
                    if(out.size()+n>1024*1024)throw new java.io.IOException("FAA response too large");
                    out.write(block,0,n);
                }
            }
            return out.toString("UTF-8");
        }finally{conn.disconnect();}
    }
}
