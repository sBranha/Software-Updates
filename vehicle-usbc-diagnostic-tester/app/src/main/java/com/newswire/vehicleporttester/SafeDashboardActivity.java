package com.newswire.vehicleporttester;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * v0.8.3: no USB accessory open, polling, reading, or writing.
 * The launcher has no way to start legacy active USB scanner services.
 * Speed/trip come solely from phone GPS. Tachometer is a DEMO needle.
 */
public final class SafeDashboardActivity extends Activity implements LocationListener {
    private static final int BACK=0xff080f1b, PANEL=0xff142235, EDGE=0xff263c50;
    private static final int TEXT=0xffeef5fc, MUTED=0xff92a9bc;
    private static final int CYAN=0xff38d6eb, ORANGE=0xffffb55d, GREEN=0xff61e2a5;
    private static final int REQ_GPS=803;
    private static final int REQ_CAMERA_TACH=860;
    private TextView linkStatus, gpsStatus, tripText, scanResult, touchResult, rpmValueText;
    private NeedleGauge gpsGauge, rpmGauge;
    private Button gpsButton;
    private SeekBar rpmSlider;
    private int demoRpm=0;
    private float cameraOpticalRpm=Float.NaN, cameraOpticalConfidence=0;
    private String cameraOpticalSource="not_tested";
    private LocationManager locationManager;
    private boolean locationEnabled=false, receivingGps=false, wasResumed=false;
    private Location previousFix;
    private double tripMiles=0, latestPhoneSpeedMph=Double.NaN;
    private String touchCheck="not_tested";
    private long startedAt=SystemClock.elapsedRealtime();
    private JSONObject lastSafeReport;
    private MotionTripLab motionLab;
    private VehicleVersionPanel vehicleVersionPanel;
    private ExplorerResearchPanel explorerResearchPanel;
    private BluetoothLinkPanel bluetoothLinkPanel;

    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        explorerResearchPanel=new ExplorerResearchPanel(this);
        buildScreen();
        refreshConnectionStatus();
    }
    @Override protected void onResume(){
        super.onResume();
        wasResumed=true;
        if(locationEnabled)beginGps();
        if(motionLab!=null)motionLab.onResume();
        if(vehicleVersionPanel!=null)vehicleVersionPanel.refresh();
        refreshConnectionStatus();
    }
    @Override protected void onPause(){
        stopGpsUpdates();
        if(motionLab!=null)motionLab.onPause();
        wasResumed=false;
        super.onPause();
    }

    private GradientDrawable panel(int color,int stroke){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(18));d.setStroke(dp(1),stroke);
        return d;
    }
    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private TextView text(String message,int sp,int color,boolean bold){
        TextView t=new TextView(this); t.setText(message); t.setTextColor(color);
        t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void gap(LinearLayout l,int h){
        View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));
    }
    private Button button(String label,int color){
        Button b=new Button(this);b.setText(label);b.setTextSize(13);
        b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(panel(color,color));
        b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),dp(10),dp(12),dp(10));
        return b;
    }
    private void buildScreen(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(BACK);
        LinearLayout body=vertical();body.setPadding(dp(18),dp(22),dp(18),dp(24));
        scroll.addView(body);setContentView(scroll);

        TextView kicker=text("VEHICLE CAPABILITY EXPLORER",12,CYAN,true);
        kicker.setLetterSpacing(0.10f);body.addView(kicker);
        gap(body,5);
        body.addView(text("Instrument Lab",30,TEXT,true));
        gap(body,3);
        body.addView(text("v0.8.7   •   NON-INTRUSIVE USB MODE",12,GREEN,true));
        gap(body,16);

        LinearLayout note=vertical();
        note.setPadding(dp(16),dp(14),dp(16),dp(14));
        note.setBackground(panel(0xff132f36,0xff24585b));
        note.addView(text("ANDROID AUTO CONNECTION PROTECTED",13,GREEN,true));
        gap(note,5);
        note.addView(text("This version NEVER opens or reads Android Auto's USB accessory. The Ford touchscreen should remain responsive.",12,TEXT,false));
        body.addView(note);
        gap(body,16);

        linkStatus=text("Checking USB connection…",14,TEXT,true);
        body.addView(linkStatus);
        gap(body,5);
        body.addView(text("Connected means Android sees a USB accessory — not that it can read Ford sensors.",11,MUTED,false));
        gap(body,13);
        vehicleVersionPanel=new VehicleVersionPanel(this);
        body.addView(vehicleVersionPanel.view());
        gap(body,12);
        Button profileButton=button("MY EXPLORER • FACTS & 35+ INVESTIGATION TOPICS",0xff1e5c75);
        body.addView(profileButton,new LinearLayout.LayoutParams(-1,dp(55)));
        profileButton.setOnClickListener(v->startActivity(
            new Intent(this,ExplorerResearchActivity.class)));
        gap(body,9);
        Button stormsButton=button("NWS WARNING OVERLAY • PHONE MAP TEST",0xff285477);
        body.addView(stormsButton,new LinearLayout.LayoutParams(-1,dp(55)));
        stormsButton.setOnClickListener(v->startActivity(
            new Intent(this,StormWarningMapLabActivity.class)));
        gap(body,12);
        bluetoothLinkPanel=new BluetoothLinkPanel(this);
        body.addView(bluetoothLinkPanel.view());
        gap(body,17);

        LinearLayout gauges=new LinearLayout(this);
        gauges.setOrientation(LinearLayout.HORIZONTAL);
        gpsGauge=new NeedleGauge(this,"PHONE GPS","MPH",140,CYAN);
        rpmGauge=new NeedleGauge(this,"ENGINE RPM","DEMO ×1000",8,ORANGE);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(206),1);
        left.rightMargin=dp(6);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(206),1);
        right.leftMargin=dp(6);
        gauges.addView(gpsGauge,left);gauges.addView(rpmGauge,right);body.addView(gauges);
        gap(body,9);

        LinearLayout tripPanel=vertical();
        tripPanel.setPadding(dp(16),dp(14),dp(16),dp(14));
        tripPanel.setBackground(panel(PANEL,EDGE));
        tripPanel.addView(text("PHONE-GPS TRIP — NOT VEHICLE ODOMETER",12,MUTED,true));
        gap(tripPanel,5);
        tripText=text("0.00 miles",22,TEXT,true);tripPanel.addView(tripText);
        body.addView(tripPanel);
        gap(body,13);

        gpsStatus=text("GPS is off. Speed and trip are phone-only measurements.",12,MUTED,false);
        body.addView(gpsStatus);
        gap(body,8);
        gpsButton=button("ENABLE PHONE GPS SPEED",0xff145d74);
        body.addView(gpsButton,new LinearLayout.LayoutParams(-1,dp(49)));
        gpsButton.setOnClickListener(v->toggleGps());
        gap(body,14);
        motionLab=new MotionTripLab(this);
        body.addView(motionLab.view());
        gap(body,15);

        LinearLayout demo=vertical();
        demo.setBackground(panel(PANEL,EDGE));
        demo.setPadding(dp(15),dp(13),dp(15),dp(13));
        demo.addView(text("RPM NEEDLE TEST — DEMO ONLY",13,ORANGE,true));
        gap(demo,5);
        demo.addView(text("Tap a preset or slide the orange dot. Watch the number and the needle change together. This is NOT live Ford engine RPM.",12,MUTED,false));
        gap(demo,9);
        rpmValueText=text("0 RPM • DEMO",22,ORANGE,true);
        demo.addView(rpmValueText);
        gap(demo,7);
        rpmSlider=new SeekBar(this);
        rpmSlider.setMax(8000);
        rpmSlider.setProgress(0);
        rpmSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(ORANGE));
        rpmSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(ORANGE));
        rpmSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seek,int value,boolean user){
                updateDemoRpm(value,false);
            }
            @Override public void onStartTrackingTouch(SeekBar seek){
                seek.getParent().requestDisallowInterceptTouchEvent(true);
            }
            @Override public void onStopTrackingTouch(SeekBar seek){
                seek.getParent().requestDisallowInterceptTouchEvent(false);
            }
        });
        rpmSlider.setOnTouchListener((view,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
                default:break;
            }
            return false;
        });
        demo.addView(rpmSlider,new LinearLayout.LayoutParams(-1,dp(50)));
        gap(demo,5);
        LinearLayout row1=new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addPreset(row1,"0 RPM",0);
        addPreset(row1,"800 RPM",800);
        addPreset(row1,"1,500 RPM",1500);
        demo.addView(row1);
        gap(demo,7);
        LinearLayout row2=new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addPreset(row2,"3,000 RPM",3000);
        addPreset(row2,"6,000 RPM",6000);
        demo.addView(row2);
        body.addView(demo);
        updateDemoRpm(0,true);
        gap(body,13);

        LinearLayout cameraPanel=vertical();
        cameraPanel.setPadding(dp(15),dp(13),dp(15),dp(15));
        cameraPanel.setBackground(panel(0xff152c38,0xff255763));
        cameraPanel.addView(text("REAL DASHBOARD RPM • PHONE CAMERA",13,CYAN,true));
        gap(cameraPanel,5);
        cameraPanel.addView(text("Optical estimate from your Explorer's cyan tachometer needle. Park, aim the rear camera at the LEFT gauge, then tap its CENTER, 0 tick and MAX tick. No OBD or USB control.",12,MUTED,false));
        gap(cameraPanel,8);
        Button cameraReader=button("OPEN CAMERA RPM READER",0xff176579);
        cameraPanel.addView(cameraReader,new LinearLayout.LayoutParams(-1,dp(51)));
        cameraReader.setOnClickListener(v->{
            Intent i=new Intent(this,CameraTachometerActivity.class);
            startActivityForResult(i,REQ_CAMERA_TACH);
        });
        body.addView(cameraPanel);
        gap(body,15);

        Button scan=button("SAFE CONNECTION SCAN",0xff147389);
        body.addView(scan,new LinearLayout.LayoutParams(-1,dp(52)));
        scan.setOnClickListener(v->safeScan(false));
        gap(body,9);
        Button shareReport=button("SAVE & SHARE REPORT",0xff1e7552);
        body.addView(shareReport,new LinearLayout.LayoutParams(-1,dp(55)));
        shareReport.setOnClickListener(v->safeScan(true));
        gap(body,9);
        scanResult=text("No scan yet. Safe scan checks connection metadata only.",12,MUTED,false);
        body.addView(scanResult);
        gap(body,14);

        Button touch=button("CHECK FORD TOUCHSCREEN",0xff244458);
        body.addView(touch,new LinearLayout.LayoutParams(-1,dp(49)));
        touch.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Touchscreen check — while parked")
            .setMessage("Leave this app open on the phone. On the Explorer screen, try scrolling the Android Auto app launcher or opening a normal app. Does the Ford screen still respond?")
            .setPositiveButton("YES, WORKS",(d,w)->setTouchResult("responsive_user_confirmed"))
            .setNegativeButton("NO, FROZEN",(d,w)->setTouchResult("frozen_user_reported"))
            .setNeutralButton("CANCEL",(d,w)->{})
            .show());
        gap(body,7);
        touchResult=text("Ford touchscreen: not yet tested with v0.8.7.",12,MUTED,false);
        body.addView(touchResult);
        gap(body,17);
        body.addView(text("REAL FORD RPM / ODOMETER: NOT CONNECTED",12,ORANGE,true));
        gap(body,3);
        body.addView(text("Android Auto does not expose actual engine-RPM or odometer readings. PHONE GPS is real phone data. RPM presets are DEMO; CAMERA RPM is an optical estimate after calibration, never a Ford ECU reading.",12,MUTED,false));
    }

    private void addPreset(LinearLayout row,String label,int rpm){
        Button b=button(label,0xff2c4358);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);
        lp.setMargins(dp(2),0,dp(2),0);
        row.addView(b,lp);
        b.setOnClickListener(v->updateDemoRpm(rpm,true));
    }
    private void updateDemoRpm(int rpm,boolean setSlider){
        demoRpm=Math.max(0,Math.min(8000,rpm));
        // Returning to a demo preset explicitly overrides an old camera estimate.
        if(rpmValueText!=null)
            rpmValueText.setText(String.format(Locale.US,"%,d RPM • DEMO",demoRpm));
        if(rpmGauge!=null)
            rpmGauge.setReading(demoRpm/1000f,"DEMO ONLY",true);
        if(setSlider && rpmSlider!=null && rpmSlider.getProgress()!=demoRpm)
            rpmSlider.setProgress(demoRpm);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_CAMERA_TACH&&resultCode==RESULT_OK&&data!=null){
            float rpm=data.getFloatExtra("optical_rpm",Float.NaN);
            if(Float.isFinite(rpm)&&rpm>=0&&rpm<=8000){
                cameraOpticalRpm=rpm;
                cameraOpticalConfidence=data.getFloatExtra("optical_confidence",0);
                cameraOpticalSource=data.getStringExtra("optical_source");
                if(cameraOpticalSource==null)cameraOpticalSource="camera_optical";
                rpmGauge.setReading(rpm/1000f,"CAMERA EST.",true);
                rpmValueText.setText(String.format(Locale.US,
                    "%,.0f RPM • CAMERA ESTIMATE",rpm));
                scanResult.setText("Camera estimated RPM from tachometer photo/preview. This is not ECU data. Tap SAFE CONNECTION SCAN to export metadata.");
            }
        }
    }

    private void setTouchResult(String status){
        touchCheck=status;
        boolean ok="responsive_user_confirmed".equals(status);
        touchResult.setText(ok?"Ford screen: YOU CONFIRMED TOUCH WORKS":"Ford screen: YOU REPORTED A FREEZE");
        touchResult.setTextColor(ok?GREEN:ORANGE);
        scanResult.setText("Touchscreen result recorded. Tap SAFE CONNECTION SCAN to export this result.");
    }

    private void refreshConnectionStatus(){
        try{
            UsbManager usb=(UsbManager)getSystemService(USB_SERVICE);
            UsbAccessory[] a=usb==null?null:usb.getAccessoryList();
            boolean active=a!=null&&a.length>0;
            linkStatus.setText(active?"USB accessory detected • monitoring only"
                :"No USB accessory detected • safe idle");
            linkStatus.setTextColor(active?GREEN:MUTED);
        }catch(Exception ex){
            linkStatus.setText("USB metadata unavailable: "+ex.getClass().getSimpleName());
        }
    }

    private void safeScan(boolean shareAfterSave){
        try{
            JSONObject j=new JSONObject();
            j.put("report_format","Vehicle Capability Explorer safe dashboard scan v2");
            j.put("app_version","0.8.7");
            j.put("phone_motion_lab",motionLab==null?JSONObject.NULL:motionLab.snapshot());
            j.put("software_versions",vehicleVersionPanel==null?JSONObject.NULL:vehicleVersionPanel.snapshot());
            j.put("explorer_vehicle_research",explorerResearchPanel==null?JSONObject.NULL:explorerResearchPanel.snapshot());
            j.put("storm_overlay_status",new JSONObject().put("ford_native_map_overlay","not_supported_by_public_api").put("newswire_android_auto_weather_map","separate_future_build").put("phone_warning_map_experiment","nws_alert_polygons_not_storm_tracks"));
            j.put("bluetooth_audio_link_check",bluetoothLinkPanel==null?JSONObject.NULL:bluetoothLinkPanel.snapshot());
            j.put("scan_mode","safe_metadata_only_no_usb_accessory_open");
            j.put("utc",iso());
            j.put("phone_model",Build.MANUFACTURER+" "+Build.MODEL);
            j.put("android_api",Build.VERSION.SDK_INT);
            j.put("usb_open_attempts",0);
            j.put("usb_read_attempts",0);
            j.put("usb_write_attempts",0);
            j.put("vehicle_commands_sent",0);
            j.put("raw_android_auto_traffic_captured",false);
            j.put("ford_sync_internal_access_confirmed",false);
            j.put("vehicle_rpm_source","not_available");
            j.put("vehicle_odometer_source","not_available");
            j.put("phone_gps_speed_enabled",locationEnabled);
            j.put("phone_gps_speed_mph",Double.isFinite(latestPhoneSpeedMph)?latestPhoneSpeedMph:JSONObject.NULL);
            j.put("phone_gps_trip_miles",tripMiles);
            j.put("rpm_gauge_source","user_controlled_demo_presets_or_slider");
            j.put("rpm_demo_value_only_not_from_vehicle",demoRpm);
            j.put("camera_optical_rpm_estimate",Float.isFinite(cameraOpticalRpm)?cameraOpticalRpm:JSONObject.NULL);
            j.put("camera_optical_confidence",cameraOpticalConfidence);
            j.put("camera_optical_source",cameraOpticalSource);
            j.put("camera_rpm_is_derived_from_phone_camera_not_vehicle_sensor",true);
            j.put("touchscreen_user_test",touchCheck);
            j.put("touchscreen_test_is_user_reported_not_electronically_verified",true);
            UsbManager usb=(UsbManager)getSystemService(USB_SERVICE);
            UsbAccessory[] accessories=usb==null?null:usb.getAccessoryList();
            JSONArray arr=new JSONArray();
            if(accessories!=null){
                for(UsbAccessory a:accessories){
                    arr.put(new JSONObject()
                        .put("manufacturer",a.getManufacturer())
                        .put("model",a.getModel())
                        .put("version",a.getVersion())
                        .put("android_permission",usb!=null&&usb.hasPermission(a)));
                }
            }
            j.put("usb_accessories_visible",arr);
            j.put("usb_host_devices_count",usb==null?0:usb.getDeviceList().size());
            try{
                BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
                BluetoothAdapter adapter=bm==null?null:bm.getAdapter();
                boolean permitted=Build.VERSION.SDK_INT<31 ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
                j.put("bluetooth_permission_granted",permitted);
                j.put("bluetooth_hardware_present",adapter!=null);
                if(adapter!=null&&permitted)j.put("bluetooth_enabled",adapter.isEnabled());
            }catch(Exception e){j.put("bluetooth_status","unavailable");}
            j.put("interpretation","Passive phone-side metadata only. This scan does not hijack Android Auto or read any ECU, Ford SYNC firmware, engine RPM or odometer.");
            lastSafeReport=j;
            refreshConnectionStatus();

            String fileName="Vehicle-Capability-Explorer-v0.8.7-SAFE-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".json";
            ContentValues cv=new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME,fileName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE,"application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/VehiclePortTester");
            Uri dest=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
            if(dest==null)throw new IllegalStateException("Downloads insert failed");
            try(OutputStream out=getContentResolver().openOutputStream(dest)){
                if(out==null)throw new IllegalStateException("Downloads stream missing");
                out.write(j.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            scanResult.setText("SAFE SCAN COMPLETE • USB not opened\nSaved: Downloads/VehiclePortTester/"+fileName);
            scanResult.setTextColor(GREEN);
            if(shareAfterSave){
                Intent share=new Intent(Intent.ACTION_SEND);
                share.setType("application/json");
                share.putExtra(Intent.EXTRA_STREAM,dest);
                share.setClipData(android.content.ClipData.newUri(
                    getContentResolver(),"Explorer capability report",dest));
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try{startActivity(Intent.createChooser(share,"Share Explorer report"));}
                catch(Exception e){
                    scanResult.setText("REPORT SAVED but share sheet couldn't open. Use Samsung My Files > Download > VehiclePortTester > "+fileName);
                }
            }
        }catch(Exception ex){
            scanResult.setText("Scan export failed: "+ex.getClass().getSimpleName()+" — "+ex.getMessage());
            scanResult.setTextColor(ORANGE);
        }
    }
    private static String iso(){
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private void toggleGps(){
        if(locationEnabled){
            locationEnabled=false;stopGpsUpdates();
            latestPhoneSpeedMph=Double.NaN;gpsGauge.setReading(0,"GPS OFF",false);
            gpsStatus.setText("Phone GPS off. No vehicle speed or RPM is read.");
            gpsButton.setText("ENABLE PHONE GPS SPEED");
            return;
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION},REQ_GPS);
            return;
        }
        locationEnabled=true;gpsButton.setText("DISABLE PHONE GPS");
        beginGps();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==BluetoothLinkPanel.PERMISSION_REQUEST&&bluetoothLinkPanel!=null
                &&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)
            bluetoothLinkPanel.check();
        if(request==REQ_GPS){
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                locationEnabled=true;
                gpsButton.setText("DISABLE PHONE GPS");
                if(wasResumed)beginGps();
            }else gpsStatus.setText("GPS permission declined. Speed remains unavailable.");
        }
    }
    private void beginGps(){
        if(!locationEnabled||receivingGps||locationManager==null)return;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                gpsStatus.setText("Enable Location/GPS in Android settings to use phone speed.");
                return;
            }
            previousFix=null;
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);
            receivingGps=true;
            gpsStatus.setText("Waiting for phone GPS fix • no vehicle ECU connection");
            gpsGauge.setReading(0,"WAITING GPS",false);
        }catch(Exception ex){
            gpsStatus.setText("Phone GPS unavailable: "+ex.getClass().getSimpleName());
        }
    }
    private void stopGpsUpdates(){
        if(locationManager!=null&&receivingGps){
            try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        }
        receivingGps=false;previousFix=null;
    }
    @Override public void onLocationChanged(Location loc){
        if(!locationEnabled||loc==null)return;
        if(motionLab!=null)motionLab.onGps(loc);
        if(!loc.hasAccuracy()||loc.getAccuracy()>40){
            gpsStatus.setText("Waiting for accurate phone GPS (<40 m).");
            return;
        }
        if(loc.hasSpeed()&&loc.getSpeed()>=0&&loc.getSpeed()<90){
            latestPhoneSpeedMph=loc.getSpeed()*2.2369362921;
            gpsGauge.setReading((float)latestPhoneSpeedMph,"PHONE GPS",true);
            gpsStatus.setText(String.format(Locale.US,
                "PHONE GPS • accuracy %.0f m • not vehicle speed sensor",
                loc.getAccuracy()));
        }else{
            latestPhoneSpeedMph=Double.NaN;
            gpsGauge.setReading(0,"NO SPEED FIX",false);
            gpsStatus.setText("Phone GPS fix received; speed unavailable.");
        }
        if(previousFix!=null&&loc.getElapsedRealtimeNanos()>previousFix.getElapsedRealtimeNanos()){
            float delta=previousFix.distanceTo(loc);
            long elapsedNs=loc.getElapsedRealtimeNanos()-previousFix.getElapsedRealtimeNanos();
            double seconds=elapsedNs/1000000000.0;
            if(seconds>=0.5 && seconds<30 &&delta>=2&&delta<seconds*50
                &&previousFix.getAccuracy()<=40){
                tripMiles+=delta/1609.344;
                tripText.setText(String.format(Locale.US,"%.2f miles",tripMiles));
            }
        }
        previousFix=loc;
    }
    @Override public void onProviderEnabled(String s){if(locationEnabled)beginGps();}
    @Override public void onProviderDisabled(String s){
        gpsStatus.setText("GPS disabled by phone. Enable Location to continue.");
        gpsGauge.setReading(0,"GPS OFF",false);
    }

    private static final class NeedleGauge extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String title,unit;
        private final float maximum;
        private final int color;
        private float value;
        private String state="NO DATA";
        private boolean valid=false;
        NeedleGauge(Context ctx,String title,String unit,float max,int color){
            super(ctx);this.title=title;this.unit=unit;this.maximum=max;this.color=color;
        }
        void setReading(float v,String label,boolean valid){
            this.value=Math.max(0,Math.min(maximum,v));this.state=label;this.valid=valid;
            invalidate();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float scale=getResources().getDisplayMetrics().density;
            float w=getWidth(),h=getHeight();
            p.setColor(PANEL);p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(0,0,w,h,18*scale,18*scale,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(scale);
            p.setColor(EDGE);c.drawRoundRect(scale/2,scale/2,w-scale/2,h-scale/2,
                18*scale,18*scale,p);
            float cx=w/2,cy=h*0.50f;
            float r=Math.min(w*0.38f,h*0.33f);
            RectF arc=new RectF(cx-r,cy-r,cx+r,cy+r);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(9*scale);p.setColor(0xff263a50);
            c.drawArc(arc,145,250,false,p);
            p.setColor(color);
            c.drawArc(arc,145,250*value/maximum,false,p);
            for(int i=0;i<=10;i++){
                double a=Math.toRadians(145+i*25);
                float xx=(float)Math.cos(a),yy=(float)Math.sin(a);
                p.setStrokeWidth(i%5==0?2.4f*scale:scale);
                p.setColor(i%5==0?TEXT:MUTED);
                c.drawLine(cx+(r-14*scale)*xx,cy+(r-14*scale)*yy,
                    cx+(r-7*scale)*xx,cy+(r-7*scale)*yy,p);
            }
            double radians=Math.toRadians(145+250*value/maximum);
            p.setColor(color);p.setStrokeWidth(3*scale);
            c.drawLine(cx,cy,cx+(float)Math.cos(radians)*(r-20*scale),
                cy+(float)Math.sin(radians)*(r-20*scale),p);
            p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,5*scale,p);
            p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(TEXT);p.setTextSize(11*scale);
            c.drawText(title,cx,21*scale,p);
            p.setTextSize(24*scale);
            String number=valid?(maximum==8?String.format(Locale.US,"%.1f",value):
                String.format(Locale.US,"%.0f",value)):"—";
            c.drawText(number,cx,cy+42*scale,p);
            p.setColor(color);p.setTextSize(9*scale);
            c.drawText(unit,cx,cy+55*scale,p);
            p.setColor(MUTED);p.setTextSize(10*scale);
            c.drawText(state,cx,h-13*scale,p);
        }
    }
}
