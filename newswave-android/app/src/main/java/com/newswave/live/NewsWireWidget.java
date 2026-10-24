package com.newswave.live;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Resizable widget. Only the foreground app collects GPS; periodic checks
 * use the last recent foreground fix or the saved ZIP. */
public final class NewsWireWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH="com.newswire.live.action.REFRESH_WIDGET";
    static final String PREF="newswire-widget-cache";
    private static final long DATA_MAX_AGE_MS=60*60*1000L;
    @Override public void onReceive(Context context,Intent intent){
        super.onReceive(context,intent);
        if(intent!=null&&ACTION_REFRESH.equals(intent.getAction())){
            SharedPreferences cache=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
            cache.edit().putBoolean("refreshing",true)
                .putLong("requested_at",System.currentTimeMillis()).apply();
            safeRender(context);
            refresh(context);
        }
    }
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        safeRender(context);
        refresh(context);
    }
    @Override public void onEnabled(Context context){
        super.onEnabled(context);
        safeRender(context);
    }
    @Override public void onAppWidgetOptionsChanged(Context context,
            AppWidgetManager manager,int widgetId,android.os.Bundle newOptions){
        super.onAppWidgetOptionsChanged(context,manager,widgetId,newOptions);
        safeRender(context);
    }
    @Override public void onRestored(Context context,int[] oldIds,int[] newIds){
        super.onRestored(context,oldIds,newIds);
        safeRender(context);
        refresh(context);
    }
    static void safeRender(Context context){
        try{render(context);}
        catch(RuntimeException problem){
            android.util.Log.w("NewsWireWidget","Widget render unavailable",problem);
        }
    }
    static void refresh(Context context){
        try{
            OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(WidgetRefreshWorker.class).build();
            WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                "newswire-home-widget-refresh",ExistingWorkPolicy.REPLACE,work);
        }catch(RuntimeException problem){
            context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putBoolean("refreshing",false).putString("error","Refresh could not start").apply();
            safeRender(context);
            android.util.Log.w("NewsWireWidget","Deferred widget refresh unavailable",problem);
        }
    }
    static void render(Context context){
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        ComponentName component=new ComponentName(context,NewsWireWidget.class);
        int[] ids=manager.getAppWidgetIds(component);
        if(ids.length==0)return;
        SharedPreferences cache=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        WidgetAreaResolver.Area area=WidgetAreaResolver.resolve(context);
        boolean matching=area!=null&&area.key.equals(cache.getString("area_key",""));
        long now=System.currentTimeMillis();
        long checked=matching?cache.getLong("checked_at",0):0;
        boolean stale=checked==0||checked>now||now-checked>DATA_MAX_AGE_MS;
        long requested=cache.getLong("requested_at",0);
        boolean refreshing=cache.getBoolean("refreshing",false)
            &&requested>0&&requested<=now&&now-requested<2*60*1000L;
        String city=area==null?"Set ZIP in NewsWire":area.label+(area.fromGps?" · GPS":" · ZIP");
        String temp=matching?cache.getString("temp","—"):"—";
        String condition=matching?cache.getString("condition","Checking conditions"):
            "Updating current area";
        String alerts=!matching?"⚠ Checking local NWS alerts":
            stale?"⚠ Alert check outdated — refresh":
            cache.getString("alerts","⚠ NWS status not checked");
        String headline=matching?cache.getString("headline","Tap for local headlines"):
            "Checking local headlines";
        String time=refreshing?"⟳ Refreshing…":
            checked==0?(cache.getString("error","").isEmpty()?"Not yet checked":
                cache.getString("error","")):
            (stale?"STALE · Last checked ":"Checked ")+
                new SimpleDateFormat("h:mm a",Locale.US).format(new Date(checked));
        PendingIntent launch=PendingIntent.getActivity(context,840,
            new Intent(context,MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent retry=PendingIntent.getBroadcast(context,841,
            new Intent(context,NewsWireWidget.class).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        for(int id:ids){
            RemoteViews view=new RemoteViews(context.getPackageName(),R.layout.newswire_widget);
            view.setTextViewText(R.id.widget_location,city);
            view.setTextViewText(R.id.widget_weather,temp+" · "+condition);
            view.setTextViewText(R.id.widget_alert,alerts);
            view.setTextViewText(R.id.widget_headline,headline);
            view.setTextViewText(R.id.widget_checked,time);
            view.setTextViewText(R.id.widget_refresh,refreshing?"⟳ CHECKING":"↻ REFRESH");
            view.setOnClickPendingIntent(R.id.widget_root,launch);
            view.setOnClickPendingIntent(R.id.widget_refresh,retry);
            manager.updateAppWidget(id,view);
        }
    }
}