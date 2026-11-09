package com.newswire.vehicleporttester;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Data sources are strictly phone sensors. No vehicle USB, Ford API, or ECU data.
 * Direction is course over ground, so it is unavailable when stationary.
 */
final class MotionTripLab implements SensorEventListener {
    private final SensorManager manager;
    private final Sensor acceleration;
    private final boolean linear;
    private final LinearLayout panel;
    private final TextView direction,altitude,peak,average,motion,quality;
    private boolean listening=false;
    private long lastFixNanos=0;
    private double weightedSpeedMps=0,movingSeconds=0,peakSpeedMps=0;
    private double bearing=Double.NaN, altitudeFeet=Double.NaN, motionMps2=Double.NaN;
    private int gpsSamples=0,motionSamples=0;
    MotionTripLab(Activity activity){
        manager=(SensorManager)activity.getSystemService(Context.SENSOR_SERVICE);
        Sensor found=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if(found==null&&manager!=null)found=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        acceleration=found;
        linear=found!=null&&found.getType()==Sensor.TYPE_LINEAR_ACCELERATION;
        panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);
        int dp=(int)(activity.getResources().getDisplayMetrics().density*14+0.5);
        panel.setPadding(dp,dp,dp,dp);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xff132b3a);bg.setStroke(1,0xff2d5464);bg.setCornerRadius(dp);
        panel.setBackground(bg);
        panel.addView(text(activity,"PHONE MOTION & TRIP LAB",15,0xff46dbed,true));
        panel.addView(text(activity,"GPS bearing is travel direction (when moving). The accelerometer measures PHONE motion, not vehicle acceleration.",11,0xff9bb4c5,false));
        direction=line(activity,"TRAVEL HEADING: —");
        altitude=line(activity,"GPS ELEVATION: —");
        peak=line(activity,"TOP GPS SPEED: —");
        average=line(activity,"AVERAGE MOVING GPS SPEED: —");
        motion=line(activity,"PHONE MOTION: —");
        quality=line(activity,acceleration==null?"Motion sensor unavailable":(linear?
            "Using linear acceleration sensor":"Using accelerometer including gravity"));
        Button reset=new Button(activity);reset.setText("RESET PHONE TRIP STATS");
        reset.setAllCaps(false);reset.setTextColor(Color.WHITE);
        reset.setBackgroundTintList(ColorStateList.valueOf(0xff25566d));
        reset.setOnClickListener(v->reset());
        panel.addView(reset,new LinearLayout.LayoutParams(-1,dp*4));
    }
    LinearLayout view(){return panel;}
    private TextView text(Activity activity,String label,int size,int color,boolean bold){
        TextView t=new TextView(activity);t.setText(label);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView line(Activity context,String value){
        TextView t=text(context,value,14,Color.WHITE,true);
        t.setPadding(0,8,0,4);panel.addView(t);return t;
    }
    void onResume(){
        if(listening||manager==null||acceleration==null)return;
        listening=manager.registerListener(this,acceleration,SensorManager.SENSOR_DELAY_UI);
    }
    void onPause(){
        if(listening&&manager!=null)manager.unregisterListener(this);
        listening=false;
    }
    void reset(){
        weightedSpeedMps=0;movingSeconds=0;peakSpeedMps=0;
        lastFixNanos=0;bearing=Double.NaN;altitudeFeet=Double.NaN;
        gpsSamples=0;motionSamples=0;motionMps2=Double.NaN;
        direction.setText("TRAVEL HEADING: —");
        altitude.setText("GPS ELEVATION: —");
        peak.setText("TOP GPS SPEED: —");
        average.setText("AVERAGE MOVING GPS SPEED: —");
        motion.setText("PHONE MOTION: —");
    }
    void onGps(Location fix){
        if(fix==null||!fix.hasAccuracy()||fix.getAccuracy()>40)return;
        gpsSamples++;
        if(fix.hasAltitude()){
            altitudeFeet=fix.getAltitude()*3.280839895;
            altitude.setText(String.format(Locale.US,"GPS ELEVATION: %.0f ft (phone)",altitudeFeet));
        }
        if(fix.hasBearing()&&fix.hasSpeed()&&fix.getSpeed()>=1.5f){
            bearing=((fix.getBearing()%360)+360)%360;
            direction.setText(String.format(Locale.US,
                "TRAVEL HEADING: %.0f° %s • phone GPS",bearing,cardinal(bearing)));
        }else{
            bearing=Double.NaN;
            direction.setText("TRAVEL HEADING: — (requires motion)");
        }
        if(fix.hasSpeed()&&fix.getSpeed()>=0&&fix.getSpeed()<90){
            double speed=fix.getSpeed();
            peakSpeedMps=Math.max(peakSpeedMps,speed);
            peak.setText(String.format(Locale.US,"TOP GPS SPEED: %.1f mph",peakSpeedMps*2.2369363));
            long now=fix.getElapsedRealtimeNanos();
            if(lastFixNanos>0&&now>lastFixNanos){
                double dt=(now-lastFixNanos)/1.e9;
                if(dt<=10&&speed>=0.8){
                    movingSeconds+=dt;weightedSpeedMps+=dt*speed;
                }
            }
            lastFixNanos=now;
            average.setText(movingSeconds>0?
                String.format(Locale.US,"AVERAGE MOVING GPS SPEED: %.1f mph",weightedSpeedMps/movingSeconds*2.2369363):
                "AVERAGE MOVING GPS SPEED: —");
        }
    }
    private String cardinal(double deg){
        String[] directions={"N","NE","E","SE","S","SW","W","NW"};
        return directions[((int)Math.round(deg/45))%8];
    }
    @Override public void onSensorChanged(SensorEvent event){
        if(event.values.length<3)return;
        double x=event.values[0],y=event.values[1],z=event.values[2];
        motionMps2=Math.sqrt(x*x+y*y+z*z);
        motionSamples++;
        if(motionSamples%6==0)
            motion.setText(String.format(Locale.US,"PHONE MOTION: %.2f m/s² %s",
                motionMps2,linear?"(gravity removed)":"(includes gravity)"));
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    JSONObject snapshot(){
        JSONObject j=new JSONObject();
        try{
            j.put("data_source","phone_location_and_inertial_sensors_only");
            j.put("heading_degrees",Double.isFinite(bearing)?bearing:JSONObject.NULL);
            j.put("heading_requires_moving",true);
            j.put("gps_elevation_feet",Double.isFinite(altitudeFeet)?altitudeFeet:JSONObject.NULL);
            j.put("peak_gps_speed_mph",peakSpeedMps*2.2369363);
            j.put("average_moving_gps_speed_mph",movingSeconds>0?weightedSpeedMps/movingSeconds*2.2369363:JSONObject.NULL);
            j.put("moving_sample_seconds",movingSeconds);
            j.put("phone_acceleration_magnitude_mps2",Double.isFinite(motionMps2)?motionMps2:JSONObject.NULL);
            j.put("motion_includes_gravity",!linear);
            j.put("gps_samples",gpsSamples);
            j.put("motion_samples",motionSamples);
            j.put("vehicle_speed_access_confirmed",false);
            j.put("vehicle_accelerometer_access_confirmed",false);
        }catch(Exception ignored){}
        return j;
    }
}
