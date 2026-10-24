package com.newswave.live;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Fetches widget information without collecting location in the background.
 * In case of provider failures, the widget never presents an all-clear.
 */
public final class WidgetRefreshWorker extends Worker {
    public WidgetRefreshWorker(@NonNull Context context,@NonNull WorkerParameters params){
        super(context,params);
    }
    static void publish(Context context,WidgetAreaResolver.Area area,MyAreaData.Snapshot data){
        if(area==null||data==null)return;
        WidgetAreaResolver.Area active=WidgetAreaResolver.resolve(context);
        if(active==null||!active.key.equals(area.key))return; // out-of-order result
        String warnings=!data.warningsAvailable?
            "⚠ NWS alert status unavailable":
            data.warnings.isEmpty()?"NWS reported no alerts at last check":
            "⚠ "+data.warnings.get(0);
        String headline=data.headlines.isEmpty()?
            "No local headlines returned":"NEWS · "+data.headlines.get(0).title;
        SharedPreferences cache=context.getSharedPreferences(NewsWireWidget.PREF,Context.MODE_PRIVATE);
        cache.edit().putString("area_key",area.key)
            .putString("area",data.area)
            .putString("temp",data.temperature)
            .putString("condition",data.condition)
            .putString("alerts",warnings).putString("headline",headline)
            .putLong("checked_at",data.checkedAt)
            .putBoolean("refreshing",false).putString("error","").apply();
        NewsWireWidget.safeRender(context);
    }
    @NonNull @Override public Result doWork(){
        Context context=getApplicationContext();
        WidgetAreaResolver.Area area=WidgetAreaResolver.resolve(context);
        SharedPreferences cache=context.getSharedPreferences(NewsWireWidget.PREF,Context.MODE_PRIVATE);
        if(area==null){
            cache.edit().remove("area_key").putString("area","Set ZIP in NewsWire")
                .putString("temp","—").putString("condition","Open app to set area")
                .putString("alerts","⚠ NWS status not checked")
                .putString("headline","Set ZIP to see local news")
                .putString("error","Location not set")
                .putLong("checked_at",0).putBoolean("refreshing",false).apply();
            NewsWireWidget.safeRender(context);
            return Result.success();
        }
        try{
            MyAreaData.Snapshot data=MyAreaData.load(area.latitude,area.longitude,
                area.city,area.region);
            publish(context,area,data);
            if(!data.warningsAvailable&&!data.conditionsAvailable&&getRunAttemptCount()<2)
                return Result.retry();
            return Result.success();
        }catch(Exception error){
            android.util.Log.w("NewsWireWidget","Widget refresh unavailable",error);
            WidgetAreaResolver.Area active=WidgetAreaResolver.resolve(context);
            if(active!=null&&active.key.equals(area.key)){
                cache.edit().putString("area_key",area.key)
                    .putString("alerts","⚠ NWS check unavailable — open official alerts")
                    .putString("condition","Weather refresh unavailable")
                    .putString("error","Refresh failed")
                    .putLong("checked_at",0).putBoolean("refreshing",false).apply();
                NewsWireWidget.safeRender(context);
            }
            return getRunAttemptCount()<2?Result.retry():Result.failure();
        }
    }
}