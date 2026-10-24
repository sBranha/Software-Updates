package com.newswave.live;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Long network work runs outside the widget BroadcastReceiver.
 * Uses saved ZIP, not silent background GPS tracking. No-network means
 * warning status is unavailable, never an unsafe all-clear.
 */
public final class WidgetRefreshWorker extends Worker {
    public WidgetRefreshWorker(@NonNull Context context,@NonNull WorkerParameters params){
        super(context,params);
    }
    @NonNull @Override public Result doWork(){
        Context context=getApplicationContext();
        SharedPreferences location=context.getSharedPreferences("newswire-location",Context.MODE_PRIVATE);
        SharedPreferences cache=context.getSharedPreferences(NewsWireWidget.PREF,Context.MODE_PRIVATE);
        String city=location.getString("city",""),region=location.getString("state","");
        double latitude=Double.longBitsToDouble(location.getLong("lat",
            Double.doubleToLongBits(Double.NaN)));
        double longitude=Double.longBitsToDouble(location.getLong("lon",
            Double.doubleToLongBits(Double.NaN)));
        if(Double.isNaN(latitude)||Double.isNaN(longitude)){
            cache.edit().putString("area","Set ZIP in NewsWire")
                .putString("temp","—").putString("condition","Open the app to set your area")
                .putString("alerts","NWS status not checked")
                .putString("headline","Saved ZIP required").putLong("checked_at",0).apply();
            NewsWireWidget.render(context);
            return Result.success();
        }
        try{
            MyAreaData.Snapshot data=MyAreaData.load(latitude,longitude,city,region);
            String warnings=!data.warningsAvailable?
                "⚠ NWS alert status unavailable":
                data.warnings.isEmpty()?"NWS reported no alerts at last check":
                "⚠ "+data.warnings.get(0);
            String headline=data.headlines.isEmpty()?
                "No local headlines returned":"NEWS · "+data.headlines.get(0).title;
            cache.edit().putString("area",data.area)
                .putString("temp",data.temperature).putString("condition",data.condition)
                .putString("alerts",warnings).putString("headline",headline)
                .putLong("checked_at",System.currentTimeMillis()).apply();
            NewsWireWidget.render(context);
            return Result.success();
        }catch(Exception error){
            android.util.Log.w("NewsWireWidget","Widget refresh unavailable",error);
            cache.edit().putString("alerts","⚠ Refresh unavailable; check official warnings")
                .putString("condition","Last data may be stale")
                .putLong("checked_at",0).apply();
            NewsWireWidget.render(context);
            return Result.retry();
        }
    }
}