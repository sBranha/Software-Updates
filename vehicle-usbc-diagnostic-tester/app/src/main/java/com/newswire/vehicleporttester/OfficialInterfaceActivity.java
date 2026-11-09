package com.newswire.vehicleporttester;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * v0.8.9: checks permissions and publicly queryable phone-side readiness only.
 * No UsbManager.openAccessory, no USB I/O, no SDL transport/registration,
 * no diagnostic/vehicle commands, and no vehicle sensor data.
 */
public final class OfficialInterfaceActivity extends Activity {
    private static final int BG=0xff081321, WHITE=0xffeef6ff, MUTED=0xffa4b9ce,
        TEAL=0xff59dbe5, GREEN=0xff64e2b1;
    private static final int SAVE=9089;
    private static final String PREF="official_interface_v089";
    private static final String[] NAMES={
        "Android Auto package","Bluetooth hardware","Bluetooth runtime permission",
        "Bluetooth enabled","Ford paired-device visibility","USB accessory metadata",
        "SmartDeviceLink router discovery","SmartDeviceLink registered session",
        "Android Auto Car Hardware session","Ford ECU and vehicle-sensor access"
    };
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final JSONArray checks=new JSONArray();
    private TextView status,results,manual;
    private ProgressBar progress;
    private Button scanButton,saveButton;
    private boolean running=false;
    private int index=0;
    private String startedUtc="";
    private String finishedUtc="";
    private final Runnable step=new Runnable(){
        @Override public void run(){
            if(!running||isFinishing())return;
            if(index>=NAMES.length){complete();return;}
            final int stepIndex=index;
            status.setText("Checking "+(stepIndex+1)+"/"+NAMES.length+": "+NAMES[stepIndex]);
            try{checks.put(runCheck(stepIndex));}
            catch(Exception e){checks.put(entry(stepIndex,"CHECK_ERROR","Android phone",
                "Check failed: "+e.getClass().getSimpleName()+"; no vehicle query was attempted."));}
            index++;
            progress.setProgress((index*100)/NAMES.length);
            StringBuilder s=new StringBuilder();
            for(int k=0;k<checks.length();k++){
                JSONObject c=checks.optJSONObject(k);
                if(c!=null)s.append(k+1).append(". ").append(c.optString("title"))
                    .append("\n   ").append(c.optString("status")).append(" — ")
                    .append(c.optString("detail")).append("\n\n");
            }
            results.setText(s.toString());
            // Each displayed stage corresponds to one completed real check, not simulated activity.
            handler.postDelayed(this,100);
        }
    };
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView label(String t,int size,int color){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(0,dp(5),0,dp(5));return v;
    }
    private Button button(String name){
        Button b=new Button(this);b.setText(name);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(0xff176d7a));return b;
    }
    private static String utc(){
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date());
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(18),dp(19),dp(18),dp(28));
        scroll.addView(v);setContentView(scroll);
        v.addView(label("OFFICIAL INTERFACE READINESS",23,TEAL));
        v.addView(label("Vehicle Capability Explorer v0.8.9",13,GREEN));
        v.addView(label("PARKED ONLY. Phone-side checks are not proof of a Ford AppLink or Android Auto Car Hardware session. No USB accessory is opened and no vehicle commands are sent.",13,WHITE));
        status=label("Not scanned in this session.",14,WHITE);v.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgress(0);v.addView(progress);
        scanButton=button("RUN 10 SAFE READINESS CHECKS");v.addView(scanButton);
        scanButton.setOnClickListener(x->startScan());
        saveButton=button("SAVE REPORT JSON");
        saveButton.setEnabled(false);v.addView(saveButton);
        saveButton.setOnClickListener(x->saveJson());
        v.addView(label("APP LINK • OBSERVATION FROM FORD SCREEN",15,TEAL));
        manual=label("",13,MUTED);v.addView(manual);
        Button record=button("RECORD FIND MOBILE APPS RESULT");v.addView(record);
        record.setOnClickListener(x->recordManual());
        v.addView(label("READINESS RESULTS • source and status shown separately",15,TEAL));
        results=label("Tap RUN to perform fresh checks.",13,WHITE);v.addView(results);
        v.addView(label("Important: a visible Bluetooth or USB accessory does not establish an SDL session or permit engine RPM, odometer, fuel, TPMS, or diagnostic data. Use only Ford-approved interfaces for later active testing.",12,MUTED));
        updateManual();
    }
    private SharedPreferences prefs(){return getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    private void updateManual(){
        String value=prefs().getString("ford_screen_result","not_recorded");
        manual.setText("Ford Apps > Find Mobile Apps: "+value+
            "\nSource: user observation, not electronically verified"+
            "\nRecorded UTC: "+prefs().getString("ford_screen_recorded_utc","not_recorded"));
    }
    private void recordManual(){
        final String[] options={"Not tested yet","Find Mobile Apps screen appears",
            "Ford displays No Mobile Apps Found","Find Mobile Apps option is missing",
            "Ford reports an error","Another result (photograph needed)"};
        new AlertDialog.Builder(this).setTitle("What does your parked Explorer show?")
            .setSingleChoiceItems(options,-1,(d,which)->{
                prefs().edit().putString("ford_screen_result",options[which])
                    .putString("ford_screen_recorded_utc",utc()).apply();
                updateManual();d.dismiss();
                status.setText("Ford screen observation recorded as user-entered evidence.");
            }).setNegativeButton("Cancel",null).show();
    }
    private JSONObject entry(int i,String state,String source,String detail){
        JSONObject j=new JSONObject();
        try{j.put("id",i).put("title",NAMES[i]).put("status",state)
            .put("source",source).put("detail",detail).put("checked_at_utc",utc());}
        catch(Exception ignored){}return j;
    }
    private boolean btPermission(){
        return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    }
    private JSONObject runCheck(int i){
        try{
            PackageManager pm=getPackageManager();
            switch(i){
                case 0:
                    try{
                        android.content.pm.PackageInfo x=pm.getPackageInfo("com.google.android.projection.gearhead",0);
                        return entry(i,"INSTALLED_PHONE","Android PackageManager",
                            "Android Auto version "+x.versionName+" is on this phone; not live projection.");
                    }catch(PackageManager.NameNotFoundException e){
                        return entry(i,"NOT_VISIBLE","Android PackageManager",
                            "Android Auto package not visible; this does not establish car support.");
                    }
                case 1:
                    BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
                    return entry(i,bm!=null&&bm.getAdapter()!=null?"PRESENT_PHONE":"NOT_DETECTED",
                        "Android BluetoothManager","Phone Bluetooth hardware; not Ford vehicle data.");
                case 2:
                    return entry(i,btPermission()?"GRANTED":"PERMISSION_REQUIRED",
                        "Android permission check","BLUETOOTH_CONNECT is needed to inspect bonded devices; not an ECU permission.");
                case 3:
                    BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
                    BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
                    if(adapter==null)return entry(i,"NO_ADAPTER","Android BluetoothAdapter","No phone Bluetooth adapter.");
                    if(!btPermission())return entry(i,"PERMISSION_REQUIRED","Android BluetoothAdapter","Cannot verify Bluetooth state without granted permission.");
                    return entry(i,adapter.isEnabled()?"ENABLED_PHONE":"OFF_PHONE",
                        "Android BluetoothAdapter","Bluetooth radio state on phone, not an AppLink transport.");
                case 4:
                    BluetoothManager b=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
                    BluetoothAdapter ba=b==null?null:b.getAdapter();
                    if(ba==null||!btPermission())return entry(i,"NOT_READABLE","Android BluetoothAdapter",
                        "Grant Bluetooth access to view paired names; no scan or pairing attempted.");
                    Set<BluetoothDevice> paired=ba.getBondedDevices();
                    boolean ford=false;
                    if(paired!=null)for(BluetoothDevice device:paired){
                        String n=device.getName();
                        if(n!=null&&(n.toLowerCase(Locale.US).contains("ford")||
                            n.toLowerCase(Locale.US).contains("sync")))ford=true;
                    }
                    return entry(i,ford?"FORD_NAME_PAIRED":"FORD_NAME_NOT_FOUND",
                        "Android bonded Bluetooth devices","Paired name only; NOT proof of a live SDL connection. No MAC addresses exported.");
                case 5:
                    UsbManager usb=(UsbManager)getSystemService(USB_SERVICE);
                    UsbAccessory[] aa=usb==null?null:usb.getAccessoryList();
                    return entry(i,aa!=null&&aa.length>0?"METADATA_VISIBLE":"NOT_DETECTED",
                        "Android UsbManager","Accessory count "+(aa==null?0:aa.length)+". Zero USB open, read or write operations.");
                case 6:
                    Intent router=new Intent("com.smartdevicelink.router.service");
                    List<ResolveInfo> one=pm.queryIntentServices(router,0);
                    List<ResolveInfo> two=pm.queryIntentServices(new Intent("sdl.router.startservice"),0);
                    int n=(one==null?0:one.size())+(two==null?0:two.size());
                    return entry(i,n>0?"ROUTER_INTENT_VISIBLE":"ROUTER_NOT_VISIBLE",
                        "Android PackageManager intent discovery",
                        n>0?"Possible SDL router-service intent matches: "+n+"; not a Ford session.":
                        "No SDL router service visible to this app. This does NOT prove SYNC lacks AppLink.");
                case 7:
                    return entry(i,"NOT_INTEGRATED","App build configuration",
                        "No SDL SDK, registered app ID, or authorized session is active; no vehicle-data RPC made.");
                case 8:
                    return entry(i,"NOT_INTEGRATED","App build configuration",
                        "No CarAppService or app-projected hardware session; normal Activity cannot verify vehicle Car Hardware.");
                default:
                    return entry(i,"NO_AUTHORIZED_LINK","Passive scan configuration",
                        "No live Ford speed, RPM, odometer, fuel, tire pressure or ECU diagnostic reading.");
            }
        }catch(SecurityException e){
            return entry(i,"PERMISSION_DENIED","Android OS","Permission denied; no fallback to raw USB.");
        }catch(Exception e){
            return entry(i,"CHECK_ERROR","Android OS","Check error: "+e.getClass().getSimpleName());
        }
    }
    private void startScan(){
        if(running)return;
        running=true;index=0;startedUtc=utc();finishedUtc="";
        while(checks.length()>0)checks.remove(checks.length()-1);
        progress.setProgress(0);saveButton.setEnabled(false);scanButton.setEnabled(false);
        results.setText("");handler.post(step);
    }
    private void complete(){
        running=false;finishedUtc=utc();
        scanButton.setEnabled(true);saveButton.setEnabled(true);
        status.setText("Completed "+checks.length()+" actual phone-side checks at "+finishedUtc+
            ". No AppLink/Car Hardware session or Ford ECU readings verified.");
        try{prefs().edit().putString("last_scan",makeReport().toString()).apply();}
        catch(Exception ignored){}
    }
    private JSONObject makeReport(){
        JSONObject j=new JSONObject();
        try{
            j.put("report_format","Vehicle Capability Explorer official interface readiness v1");
            j.put("app_version","0.8.9");
            j.put("started_at_utc",startedUtc);
            j.put("finished_at_utc",finishedUtc);
            j.put("checks_complete",checks.length());
            j.put("checks",new JSONArray(checks.toString()));
            j.put("ford_screen_observation",prefs().getString("ford_screen_result","not_recorded"));
            j.put("ford_screen_observation_source","user_reported_not_electronically_verified");
            j.put("ford_screen_recorded_utc",prefs().getString("ford_screen_recorded_utc","not_recorded"));
            j.put("usb_accessory_opened",false);
            j.put("usb_commands_sent",0);
            j.put("car_hardware_session_verified",false);
            j.put("sdl_session_verified",false);
            j.put("vehicle_ecu_sensor_data_verified",false);
            j.put("privacy","No VIN, MAC address, serial number or raw USB traffic collected.");
        }catch(Exception ignored){}
        return j;
    }
    public static JSONObject lastReport(Context ctx){
        String s=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getString("last_scan","");
        if(s.isEmpty()){
            JSONObject o=new JSONObject();
            try{o.put("status","not_run").put("app_version","0.8.9");}
            catch(Exception ignored){}return o;
        }
        try{return new JSONObject(s);}
        catch(Exception e){return new JSONObject();}
    }
    private void saveJson(){
        if(running||checks.length()!=NAMES.length)return;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,
            "Vehicle-Capability-Explorer-v0.8.9-INTERFACES-"+new SimpleDateFormat(
                "yyyyMMdd-HHmmss",Locale.US).format(new Date())+".json");
        startActivityForResult(i,SAVE);
    }
    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==SAVE&&res==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new IllegalStateException("No output stream");
                out.write(makeReport().toString(2).getBytes(StandardCharsets.UTF_8));
                status.setText("Report saved successfully. No vehicle commands sent.");
            }catch(Exception e){
                status.setText("Save failed: "+e.getClass().getSimpleName());
            }
        }
    }
    @Override protected void onDestroy(){
        running=false;handler.removeCallbacks(step);super.onDestroy();
    }
}
