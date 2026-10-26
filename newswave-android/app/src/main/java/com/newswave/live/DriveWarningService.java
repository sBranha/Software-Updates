package com.newswave.live;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Explicitly opt-in, user-started foreground location monitoring.
 * Checks active NWS tornado, severe thunderstorm and flash flood warnings at the present location and at
 * points projected along the device's current course. It does not know
 * the navigation route and MUST NOT imply that no warning means safety.
 * No driving-map blocking dialogs. Optional phone speech requests audio focus.
 */
public final class DriveWarningService extends Service implements LocationListener {
    public static final String ACTION_STOP="com.newswire.live.STOP_DRIVE_MONITOR";
    public static final String PREF="newswire-drive-alerts";
    private static final String TAG="NewsWireDrive";
    private static final String MONITOR_CHANNEL="newswire_drive_monitor";
    private static final String WARNING_CHANNEL="newswire_tornado_warning";
    private static final int ONGOING_ID=840;
    private static final int WARNING_ID=841;
    private static final long POLL_MS=90L*1000; // More timely NWS checks while explicitly driving
    private static final long REPEAT_MS=20L*60*1000;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private volatile boolean active=false,requesting=false;
    private long lastPoll=0,lastNotice=0;
    private String lastNoticeKey="";
    private NotificationManager notifications;
    private DrivingAlertSpeaker voice;
    @Override public void onCreate(){
        super.onCreate();
        notifications=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        voice=new DrivingAlertSpeaker(this);
        notifications.createNotificationChannel(new NotificationChannel(MONITOR_CHANNEL,
            "NewsWire driving weather monitoring",NotificationManager.IMPORTANCE_LOW));
        NotificationChannel danger=new NotificationChannel(WARNING_CHANNEL,
            "Severe weather warnings while driving",NotificationManager.IMPORTANCE_HIGH);
        danger.enableVibration(true);
        danger.setDescription("Official NWS severe weather warnings for your location or heading");
        notifications.createNotificationChannel(danger);
    }
    private PendingIntent launch(){
        Intent go=new Intent(this,MainActivity.class);
        go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this,845,go,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification monitoringNotice(String detail){
        Intent stop=new Intent(this,DriveWarningService.class).setAction(ACTION_STOP);
        PendingIntent end=PendingIntent.getService(this,846,stop,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("NewsWire · Drive Alerts on")
            .setContentText(detail)
            .setContentIntent(launch())
            .addAction(0,"Stop alerts",end)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private void setMonitoringStatus(String status){
        notifications.notify(ONGOING_ID,monitoringNotice(status));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){
            getSharedPreferences(PREF,MODE_PRIVATE).edit().putBoolean("enabled",false).apply();
            end();
            return START_NOT_STICKY;
        }
        if(getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("enabled",false)==false){
            end();return START_NOT_STICKY;
        }
        if(Build.VERSION.SDK_INT>=23
            &&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            getSharedPreferences(PREF,MODE_PRIVATE).edit().putBoolean("enabled",false).apply();
            end();return START_NOT_STICKY;
        }
        try{
            if(Build.VERSION.SDK_INT>=29)
                startForeground(ONGOING_ID,monitoringNotice("Watching for official NWS warnings"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(ONGOING_ID,monitoringNotice("Watching for official NWS warnings"));
            active=true;
            locationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
            for(String provider:locationManager.getProviders(true)){
                try{locationManager.requestLocationUpdates(provider,5000,25f,this,Looper.getMainLooper());}
                catch(Exception e){Log.w(TAG,"Location provider unavailable",e);}
            }
            Location best=null;
            for(String provider:locationManager.getProviders(true)){
                try{
                    Location loc=locationManager.getLastKnownLocation(provider);
                    if(loc!=null&&(best==null||loc.getTime()>best.getTime()))best=loc;
                }catch(Exception ignored){}
            }
            if(best!=null&&System.currentTimeMillis()-best.getTime()<90000)onLocationChanged(best);
        }catch(Exception e){
            Log.e(TAG,"Could not start Drive Alerts",e);
            getSharedPreferences(PREF,MODE_PRIVATE).edit().putBoolean("enabled",false).apply();
            end();return START_NOT_STICKY;
        }
        return START_NOT_STICKY; // never restart monitoring without explicit user action
    }
    private void end(){
        active=false;
        if(locationManager!=null)try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        if(voice!=null){voice.shutdown();voice=null;}
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onLocationChanged(Location location){
        if(!active||location==null||!location.hasAccuracy()
            ||location.getAccuracy()>150f || System.currentTimeMillis()-location.getTime()>90000L)return;
        long now=System.currentTimeMillis();
        if(now-lastPoll<POLL_MS||requesting)return;
        lastPoll=now;requesting=true;
        final Location fix=new Location(location);
        network.execute(()->{
            try{inspect(fix);}
            catch(Exception error){
                Log.w(TAG,"NWS driving alert check unavailable",error);
                if(active)main.post(()->setMonitoringStatus("NWS check unavailable; use official alerts"));
            }finally{requesting=false;}
        });
    }
    private static Location projected(Location origin,double meters){
        return projected(origin,meters,origin.getBearing());
    }
    private static Location projected(Location origin,double meters,float heading){
        // Spherical great-circle projection from GPS course, NOT a road route.
        double radius=6371000.0, bearing=Math.toRadians(heading);
        double a=Math.toRadians(origin.getLatitude()),o=Math.toRadians(origin.getLongitude());
        double d=meters/radius;
        double destLat=Math.asin(Math.sin(a)*Math.cos(d)+Math.cos(a)*Math.sin(d)*Math.cos(bearing));
        double destLon=o+Math.atan2(Math.sin(bearing)*Math.sin(d)*Math.cos(a),
            Math.cos(d)-Math.sin(a)*Math.sin(destLat));
        Location point=new Location("heading-projection");
        point.setLatitude(Math.toDegrees(destLat));
        point.setLongitude(Math.toDegrees(destLon));
        return point;
    }
    private static Location sideSample(Location origin,double aheadMeters,double offsetMeters,boolean left){
        Location ahead=projected(origin,aheadMeters);
        float sideAngle=origin.getBearing()+(left?-90f:90f);
        return projected(ahead,offsetMeters,sideAngle);
    }
    private Set<String> severeWarnings(Location location) throws Exception{
        String coord=String.format(Locale.US,"%.5f,%.5f",
            location.getLatitude(),location.getLongitude());
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL("https://api.weather.gov/alerts/active?point="+coord)
                .openConnection();
            connection.setConnectTimeout(6500);connection.setReadTimeout(9000);
            connection.setRequestProperty("Accept","application/geo+json");
            connection.setRequestProperty("User-Agent",
                "NewsWire Drive Alerts/0.5.1 (https://github.com/sBranha/Software-Updates)");
            if(connection.getResponseCode()!=200)
                throw new IllegalStateException("NWS HTTP "+connection.getResponseCode());
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()){
                byte[] block=new byte[8192];int n;
                while((n=input.read(block))!=-1){
                    if(bytes.size()+n>512*1024)throw new IllegalStateException("NWS alert response too large");
                    bytes.write(block,0,n);
                }
            }
            JSONObject data=new JSONObject(bytes.toString("UTF-8"));
            JSONArray features=data.optJSONArray("features");
            if(features==null)throw new IllegalStateException("NWS features missing");
            Set<String> result=new HashSet<>();
            for(int i=0;i<features.length();i++){
                JSONObject item=features.optJSONObject(i);
                if(item==null)continue;
                JSONObject props=item.optJSONObject("properties");
                if(props==null)continue;
                String event=props.optString("event","");
                if(!"Tornado Warning".equalsIgnoreCase(event)
                    &&!"Severe Thunderstorm Warning".equalsIgnoreCase(event)
                    &&!"Flash Flood Warning".equalsIgnoreCase(event))continue;
                if("Cancel".equalsIgnoreCase(props.optString("messageType","")))continue;
                if(!"Actual".equalsIgnoreCase(props.optString("status","Actual")))continue;
                String id=item.optString("id",props.optString("id",""));
                if(!id.isEmpty())result.add(event+"|"+id);
            }
            return result;
        }finally{if(connection!=null)connection.disconnect();}
    }
    private static String mostUrgent(Set<String> alerts){
        for(String kind:new String[]{"Tornado Warning","Flash Flood Warning",
                                     "Severe Thunderstorm Warning"}){
            for(String key:alerts)if(key.startsWith(kind+"|"))return key;
        }
        return null;
    }
    private static String warningType(String key){
        int separator=key.indexOf('|');
        return separator<0?"NWS Warning":key.substring(0,separator);
    }
    private void inspect(Location fix) throws Exception{
        Set<String> current=severeWarnings(fix);
        if(!active)return;
        String atLocation=mostUrgent(current);
        if(atLocation!=null){
            String event=warningType(atLocation);
            emit("IN_WARNING",atLocation,
                event+" at your location",
                "Official NWS "+event+" covers your current position. "+
                ("Tornado Warning".equals(event)?"Seek sturdy shelter immediately.":
                "Flash Flood Warning".equals(event)?"Never drive through flooded roads.":
                "Monitor official guidance and seek shelter if threatened."),
                "Use extreme caution. "+event+" at your current location. "+
                ("Tornado Warning".equals(event)?"Seek sturdy shelter immediately.":
                "Flash Flood Warning".equals(event)?"Never drive into floodwater.":
                "Follow official warnings and find safe shelter if needed."));
            return;
        }
        // Straight-line heading is not a navigation route or storm-motion forecast.
        if(fix.hasBearing()&&fix.hasSpeed()&&fix.getSpeed()>=5f
            &&fix.getAccuracy()<=100f){
            for(double meters:new double[]{1609,4828,9656}){
                Location point=projected(fix,meters);
                Set<String> future=severeWarnings(point);
                if(!active)return;
                future.removeAll(current);
                String ahead=mostUrgent(future);
                if(ahead!=null){
                    String event=warningType(ahead);
                    String miles=String.format(Locale.US,"%.0f",meters/1609.344);
                    emit("AHEAD",ahead,event+" area ahead on your heading",
                        "Your current direction of travel may enter an official NWS "+event+
                        " area in approximately "+miles+
                        " miles. This is not a route or storm-track prediction. Check official guidance.",
                        "Use extreme caution. "+event+" area ahead, approximately "+
                        miles+" miles in your direction of travel. "+
                        "This describes a warning area, not the tornado's exact location.");
                    return;
                }
            }
            // Only describe the warning *area* to the left or right of the
            // straight GPS course. Side samples cannot locate the tornado
            // itself or predict an actual road crossing.
            for(double meters:new double[]{4828,9656}){
                Set<String> left=severeWarnings(sideSample(fix,meters,1200,true));
                Set<String> right=severeWarnings(sideSample(fix,meters,1200,false));
                if(!active)return;
                left.removeAll(current);right.removeAll(current);
                String onLeft=mostUrgent(left),onRight=mostUrgent(right);
                String miles=String.format(Locale.US,"%.0f",meters/1609.344);
                if(onLeft==null && onRight==null)continue;
                if(onLeft!=null && onRight!=null){
                    // If both sides are within warning polygons, avoid giving
                    // a misleading left-versus-right claim.
                    String event=warningType(onLeft);
                    emit("BOTH_SIDES",onLeft,event+" areas near your heading",
                         "Official NWS "+event+" areas detected on both sides of your straight-ahead heading, "+
                         "approximately "+miles+" miles ahead. Not a road-route or storm-track prediction.",
                         "Use extreme caution. NWS "+event+
                         " areas are near both sides of your direction of travel, approximately "+
                         miles+" miles ahead. This is not the exact storm location.");
                    return;
                }
                boolean isLeft=onLeft!=null;
                String key=isLeft?onLeft:onRight,side=isLeft?"left":"right";
                String event=warningType(key);
                emit("SIDE_"+side.toUpperCase(Locale.US),key,
                     event+" area to your "+side+" ahead",
                     "Official NWS "+event+" area sampled to the "+side+" of your "+
                     "current straight-ahead heading, about "+miles+
                     " miles ahead. This is not a road route or tornado location.",
                     "Use extreme caution. "+event+" area to your "+side+
                     " of the direction you're traveling, approximately "+
                     miles+" miles ahead. The actual storm location may differ.");
                return;
            }
        }
        if(active)main.post(()->setMonitoringStatus(
            "Monitoring NWS · last check completed; alerts can change"));
    }
    private void emit(String kind,String alertId,String title,String description,String spoken){
        long now=System.currentTimeMillis();
        String key=kind+"|"+alertId;
        if(key.equals(lastNoticeKey)&&now-lastNotice<REPEAT_MS)return;
        lastNoticeKey=key;lastNotice=now;
        if(!active)return;
        main.post(()->{
            if(!active)return;
            Notification notice=new NotificationCompat.Builder(this,WARNING_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(description)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(description))
                .setContentIntent(launch())
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true).build();
            notifications.notify(WARNING_ID,notice);
            if(voice!=null)voice.announce(spoken);
            setMonitoringStatus(title);
        });
    }
    @Nullable @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        active=false;
        if(locationManager!=null)try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        network.shutdownNow();
        if(voice!=null){voice.shutdown();voice=null;}
        super.onDestroy();
    }
}
