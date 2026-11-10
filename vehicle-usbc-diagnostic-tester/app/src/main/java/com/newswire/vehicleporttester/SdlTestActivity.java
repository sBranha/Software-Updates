package com.newswire.vehicleporttester;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class SdlTestActivity extends Activity {
    private static final int ASK_BT=919, ASK_NOTIFY=920, SAVE=921;
    private TextView state, detail, auditInfo;
    private boolean requestedAudit=false;
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshLoop=new Runnable(){
        @Override public void run(){refresh();uiHandler.postDelayed(this,750);}
    };
    private int dp(int d){return (int)(d*getResources().getDisplayMetrics().density+0.5f);}
    private Button button(String title){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff19798b));
        return b;
    }
    private TextView label(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,dp(9),0,dp(9));return t;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xff081421);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff081421);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18),dp(18),dp(18),dp(30));scroll.addView(layout);setContentView(scroll);
        layout.addView(label("FORD SYNC 3 • VEHICLE DATA",23,0xff39dded));
        layout.addView(label("Vehicle Explorer v0.9.4 • SmartDeviceLink",13,0xff66e2ad));
        layout.addView(label("ONE TAP checks SYNC 3, seven Ford fields, cloud authorization and OBD-II availability. Real progress, one final report and no simulated values.",15,Color.WHITE));
        layout.addView(label("1. PARK the Explorer. Pair your phone with SYNC 3 Bluetooth.\n2. Allow Bluetooth permissions in this app.\n3. Tap START FORD APPLINK below.\n4. In the vehicle, leave Android Auto if necessary and open SYNC 3 > Apps > Find Mobile Apps.\n5. Open Vehicle Explorer and select TEST EVERYTHING. You may also run it from this phone.",14,0xffbed1e0));
        layout.addView(label("Android Auto projection and SYNC 3 AppLink are different interfaces. Ford may reject each data request even while the app is visible. Each field is tested independently, and DISALLOWED means OEM permission denied.",13,0xffffc674));
        Button testEverything=button("TEST EVERYTHING - ONE TAP");layout.addView(testEverything);
        testEverything.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff0b785e));
        auditInfo=label("Audit not run yet.",15,0xff56e2c3);layout.addView(auditInfo);
        testEverything.setOnClickListener(v->checkAndStart(true));
        Button start=button("START FORD APPLINK CONNECTION");layout.addView(start);
        Button stop=button("STOP APPLINK TEST");layout.addView(stop);
        Button refresh=button("REFRESH ACTUAL CONNECTION STATUS");layout.addView(refresh);
        state=label("Not started.",16,0xff4ce6c0);layout.addView(state);
        detail=label("No vehicle communication confirmed.",14,Color.WHITE);layout.addView(detail);
        Button observation=button("RECORD WHAT IS ON THE FORD SCREEN");layout.addView(observation);
        observation.setOnClickListener(v->{
            String[] options={"No Mobile Apps Found","Vehicle Explorer not listed","Vehicle Explorer is listed",
                "Vehicle Explorer opened on Ford screen","Vehicle Explorer opened but display empty"};
            new AlertDialog.Builder(this).setTitle("What did SYNC 3 display?")
                .setItems(options,(dialog,which)->{
                    prefs().edit().putString("ford_screen_observation",options[which])
                        .putLong("ford_screen_observed_at_ms",System.currentTimeMillis()).apply();
                    refresh();
                }).setNegativeButton("Cancel",null).show();
        });
        Button save=button("SAVE SYNC 3 CONNECTION REPORT");layout.addView(save);
        start.setOnClickListener(v->checkAndStart(false));
        stop.setOnClickListener(v->{
            stopService(new Intent(this,SdlService.class));
            SdlService.record(this,"MANUAL_STOP","AppLink test stopped by user");
            refresh();
        });
        refresh.setOnClickListener(v->refresh());
        save.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE,"Vehicle-Explorer-v0.9.4-ONE-TAP-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".json");
            startActivityForResult(i,SAVE);
        });
        refresh();
        if(getIntent()!=null && getIntent().getBooleanExtra("start_full_audit",false))
            uiHandler.post(()->checkAndStart(true));
    }
    private void checkAndStart(){checkAndStart(false);}
    private void checkAndStart(boolean launchAudit){
        requestedAudit=launchAudit;
        if(Build.VERSION.SDK_INT>=31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},ASK_BT);return;
        }
        if(Build.VERSION.SDK_INT>=33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},ASK_NOTIFY);return;
        }
        try{
            Intent svc=new Intent(this,SdlService.class);
            if(launchAudit)svc.setAction(SdlService.ACTION_TEST_ALL);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);
            refresh();
        }catch(Exception ex){
            SdlService.record(this,"START_FAILED",ex.getClass().getSimpleName()+": "+ex.getMessage());
            refresh();
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] results){
        super.onRequestPermissionsResult(request,names,results);
        if((request==ASK_BT||request==ASK_NOTIFY)&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)checkAndStart(requestedAudit);
        else refresh();
    }
    private SharedPreferences prefs(){return getSharedPreferences("sdl_discovery_v091",MODE_PRIVATE);}
    private void refresh(){
        android.content.SharedPreferences a=getSharedPreferences(OneTapAudit.PREF,MODE_PRIVATE);
        if(auditInfo!=null) auditInfo.setText("ONE TAP: "+a.getString("status","NOT_RUN")+
            "   "+a.getInt("complete",0)+"/7 vehicle fields\n"+
            a.getString("stage","Tap TEST EVERYTHING to start")+
            "\nFord cloud: "+a.getString("fordconnect_cloud","NOT_TESTED")+
            "\nOBD-II: "+a.getString("obd2_live","NOT_TESTED"));
        String x=prefs().getString("state","NOT_STARTED");
        state.setText("ACTUAL SDL STATUS: "+x);
        detail.setText("HMI level: "+prefs().getString("hmi_level","NOT_RECEIVED")+
                "\nFord screen ever in foreground: "+prefs().getBoolean("hmi_ever_full",false)+
                "\nFord screen observation: "+prefs().getString("ford_screen_observation","not_recorded")+
                "\nRecent detail: "+prefs().getString("detail","No connection registered.")+
                "\nRecorded events: "+countEvents());
    }
    private int countEvents(){
        try{return new org.json.JSONArray(prefs().getString("event_history","[]")).length();}
        catch(Exception ignored){return 0;}
    }
    private JSONObject export(){
        JSONObject j=new JSONObject();
        try{
            j.put("format","Vehicle Explorer SYNC 3 AppLink connection report v1");
            j.put("version","0.9.4");
            j.put("saved_at_epoch_ms",System.currentTimeMillis());
            j.put("app_link_state",prefs().getString("state","NOT_STARTED"));
            j.put("app_link_detail",prefs().getString("detail",""));
            j.put("hmi_level",prefs().getString("hmi_level","NOT_RECEIVED"));
            j.put("hmi_ever_full",prefs().getBoolean("hmi_ever_full",false));
            j.put("ford_screen_observation",prefs().getString("ford_screen_observation","not_recorded"));
            j.put("ford_screen_observed_at_ms",prefs().getLong("ford_screen_observed_at_ms",0));
            j.put("event_history",new org.json.JSONArray(prefs().getString("event_history","[]")));
            android.content.SharedPreferences fp=getSharedPreferences("ford_data_v093",MODE_PRIVATE);
            JSONObject fields=new JSONObject();
            for(String key:new String[]{"rpm","speed","fuel_level","odometer","engine_oil_life","tire_pressure","body_information"}){
                fields.put(key,new JSONObject()
                    .put("permission",fp.getString("permission_"+key,"NOT_TESTED"))
                    .put("value",fp.getString("value_"+key,""))
                    .put("sample_time_ms",fp.getLong("sample_time_"+key,0)));
            }
            j.put("ford_field_results",fields);
            j.put("obd_verified_snapshot_present",!getSharedPreferences("ford_obd_verified",MODE_PRIVATE)
                    .getString("last_verified_readings","").isEmpty());
            j.put("app_link_event_epoch_ms",prefs().getLong("updated_at_ms",0));
            j.put("sdl_development_app_id",SdlService.APP_ID);
            j.put("oem_approval_verified",false);
            j.put("usb_accessory_takeover_attempted",false);
            j.put("vehicle_settings_modified",false);
        }catch(Exception ignored){}
        return j;
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==SAVE&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new IllegalStateException("No stream");
                out.write(export().toString(2).getBytes(StandardCharsets.UTF_8));
                new AlertDialog.Builder(this).setMessage("SYNC 3 connection report saved.").setPositiveButton("OK",null).show();
            }catch(Exception ex){new AlertDialog.Builder(this).setMessage("Save failed: "+ex.getClass().getSimpleName()).setPositiveButton("OK",null).show();}
        }
    }
    @Override protected void onResume(){
        super.onResume();
        uiHandler.removeCallbacks(refreshLoop);
        if(state!=null){refresh();uiHandler.postDelayed(refreshLoop,750);}
    }
    @Override protected void onPause(){
        uiHandler.removeCallbacks(refreshLoop);
        super.onPause();
    }
}
