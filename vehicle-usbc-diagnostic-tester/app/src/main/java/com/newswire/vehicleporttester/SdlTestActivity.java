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
    private TextView state, detail;
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
        layout.addView(label("FORD SYNC 3 • ON-SCREEN TEST",23,0xff39dded));
        layout.addView(label("Vehicle Explorer v0.9.1 • SmartDeviceLink",13,0xff66e2ad));
        layout.addView(label("PURPOSE: Make Vehicle Explorer show on the car's SYNC 3 Apps screen, not just the phone. The initial Ford screen is a simple text/menu interface, not the full custom concept dashboard.",15,Color.WHITE));
        layout.addView(label("1. PARK the Explorer. Pair your phone with SYNC 3 Bluetooth.\n2. Allow Bluetooth permissions in this app.\n3. Tap START FORD APPLINK below.\n4. In the vehicle, leave Android Auto if necessary and open SYNC 3 > Apps > Find Mobile Apps.\n5. Look for 'Vehicle Explorer'. If shown, open it and use its menu > Read Ford data.",14,0xffbed1e0));
        layout.addView(label("Android Auto projection and SYNC 3 AppLink are different interfaces. Ford may require a registered/approved AppLink ID before showing this test app or allowing vehicle-data requests. A failure to appear does NOT mean Ford vehicle modules lack the requested data.",13,0xffffc674));
        Button start=button("START FORD APPLINK CONNECTION");layout.addView(start);
        Button stop=button("STOP APPLINK TEST");layout.addView(stop);
        Button refresh=button("REFRESH ACTUAL CONNECTION STATUS");layout.addView(refresh);
        state=label("Not started.",16,0xff4ce6c0);layout.addView(state);
        detail=label("No vehicle communication confirmed.",14,Color.WHITE);layout.addView(detail);
        Button save=button("SAVE SYNC 3 CONNECTION REPORT");layout.addView(save);
        start.setOnClickListener(v->checkAndStart());
        stop.setOnClickListener(v->{
            stopService(new Intent(this,SdlService.class));
            SdlService.record(this,"MANUAL_STOP","AppLink test stopped by user");
            refresh();
        });
        refresh.setOnClickListener(v->refresh());
        save.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE,"Vehicle-Explorer-v0.9.1-SYNC3-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".json");
            startActivityForResult(i,SAVE);
        });
        refresh();
    }
    private void checkAndStart(){
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
            if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);
            refresh();
        }catch(Exception ex){
            SdlService.record(this,"START_FAILED",ex.getClass().getSimpleName()+": "+ex.getMessage());
            refresh();
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] results){
        super.onRequestPermissionsResult(request,names,results);
        if((request==ASK_BT||request==ASK_NOTIFY)&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)checkAndStart();
        else refresh();
    }
    private SharedPreferences prefs(){return getSharedPreferences("sdl_discovery_v091",MODE_PRIVATE);}
    private void refresh(){
        String x=prefs().getString("state","NOT_STARTED");
        state.setText("ACTUAL SDL STATUS: "+x);
        detail.setText(prefs().getString("detail","Not connected to SYNC 3. No Ford vehicle readings verified.")
                +"\nLast event timestamp (ms): "+prefs().getLong("updated_at_ms",0)
                +"\nNo sensor values are simulated.");
    }
    private JSONObject export(){
        JSONObject j=new JSONObject();
        try{
            j.put("format","Vehicle Explorer SYNC 3 AppLink connection report v1");
            j.put("version","0.9.1");
            j.put("saved_at_epoch_ms",System.currentTimeMillis());
            j.put("app_link_state",prefs().getString("state","NOT_STARTED"));
            j.put("app_link_detail",prefs().getString("detail",""));
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
    @Override protected void onResume(){super.onResume();if(state!=null)refresh();}
}
