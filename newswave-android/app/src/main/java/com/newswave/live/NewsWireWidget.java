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

/** A user-addable home-screen widget: cached ZIP-based live data and
 * last-checked timestamp. Broadcast callbacks are short; network calls run in WorkManager.
 */
public final class NewsWireWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH="com.newswire.live.action.REFRESH_WIDGET";
    static final String PREF="newswire-widget-cache";
    @Override public void onReceive(Context context,Intent intent){
        super.onReceive(context,intent);
        if(intent!=null&&ACTION_REFRESH.equals(intent.getAction())){
            safeRender(context);
            refresh(context);
        }
    }
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        // Draw immediately using cached text. Widget pinning should not depend
        // on network access or asynchronous WorkManager initialization.
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
            android.util.Log.w("NewsWireWidget","Widget initial render unavailable",problem);
        }
    }
    static void refresh(Context context){
        try{
            OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(WidgetRefreshWorker.class).build();
            WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                "newswire-home-widget-refresh",ExistingWorkPolicy.REPLACE,work);
        }catch(RuntimeException problem){
            // A worker failure must never crash the launcher placement flow.
            android.util.Log.w("NewsWireWidget","Deferred widget refresh unavailable",problem);
        }
    }
    static void render(Context context){
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        ComponentName component=new ComponentName(context,NewsWireWidget.class);
        int[] ids=manager.getAppWidgetIds(component);
        if(ids.length==0)return;
        SharedPreferences cache=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        SharedPreferences location=context.getSharedPreferences("newswire-location",Context.MODE_PRIVATE);
        String city=cache.getString("area",
            location.getString("city","Set ZIP in NewsWire"));
        String temp=cache.getString("temp","—");
        String condition=cache.getString("condition","Weather awaiting check");
        String alerts=cache.getString("alerts","NWS alerts not yet checked");
        String headline=cache.getString("headline","Tap for local headlines");
        long checked=cache.getLong("checked_at",0);
        String time=checked==0?"Not yet checked":"Checked "+
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
            view.setOnClickPendingIntent(R.id.widget_root,launch);
            view.setOnClickPendingIntent(R.id.widget_refresh,retry);
            manager.updateAppWidget(id,view);
        }
    }
}