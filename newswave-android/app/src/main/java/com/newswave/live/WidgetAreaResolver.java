package com.newswave.live;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

/** Widget geography: use a recent location obtained while the phone app is open,
 * then fall back to the user's stored ZIP. The widget itself never starts GPS.
 */
final class WidgetAreaResolver {
    static final String PREF="newswire-widget-follow";
    static final long GPS_MAX_AGE_MS=30*60*1000L;
    static final class Area {
        final double latitude, longitude;
        final String city,region,key,label;
        final boolean fromGps;
        Area(double lat,double lon,String city,String region,boolean fromGps) {
            latitude=lat;longitude=lon;
            this.city=city==null?"":city.trim();
            this.region=region==null?"":region.trim();
            this.fromGps=fromGps;
            key=String.format(Locale.US,"%.3f:%.3f:%s",lat,lon,fromGps?"gps":"zip");
            label=this.city.isEmpty()?(fromGps?"Current location":"Saved ZIP"):
                this.city+(this.region.isEmpty()?"":", "+this.region);
        }
    }
    static boolean valid(double lat,double lon) {
        return Double.isFinite(lat)&&Double.isFinite(lon)&&
            lat>=-90&&lat<=90&&lon>=-180&&lon<=180;
    }
    static long bits(SharedPreferences p,String name) {
        return p.getLong(name,Double.doubleToLongBits(Double.NaN));
    }
    static Area resolve(Context context) {
        long now=System.currentTimeMillis();
        SharedPreferences gps=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        double lat=Double.longBitsToDouble(bits(gps,"latitude"));
        double lon=Double.longBitsToDouble(bits(gps,"longitude"));
        long seen=gps.getLong("seen_at",0);
        if(valid(lat,lon)&&seen>0&&seen<=now&&now-seen<=GPS_MAX_AGE_MS) {
            return new Area(lat,lon,gps.getString("city",""),
                gps.getString("region",""),true);
        }
        SharedPreferences zip=context.getSharedPreferences("newswire-location",Context.MODE_PRIVATE);
        lat=Double.longBitsToDouble(bits(zip,"lat"));
        lon=Double.longBitsToDouble(bits(zip,"lon"));
        if(!valid(lat,lon))return null;
        return new Area(lat,lon,zip.getString("city",""),zip.getString("state",""),false);
    }
    static void rememberForegroundFix(Context context,double lat,double lon,
                                      String city,String region) {
        if(!valid(lat,lon))return;
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putLong("latitude",Double.doubleToLongBits(lat))
            .putLong("longitude",Double.doubleToLongBits(lon))
            .putString("city",city==null?"":city)
            .putString("region",region==null?"":region)
            .putLong("seen_at",System.currentTimeMillis()).apply();
    }
}