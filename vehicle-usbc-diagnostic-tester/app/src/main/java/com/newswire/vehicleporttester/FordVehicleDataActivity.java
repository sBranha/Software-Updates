package com.newswire.vehicleporttester;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Ford Vehicle Data v0.9.0. Explicit, parked-only, read-only generic OBD-II discovery.
 * DOES NOT connect to SYNC's Android Auto USB accessory, send actuator controls,
 * write ECU configuration, clear DTCs, or claim Ford enhanced module access.
 * The user must pair a Bluetooth Classic OBD-II adapter in Android Settings first.
 */
public final class FordVehicleDataActivity extends Activity {
    private static final int PERM=9090, SAVE=9091;
    private static final int BG=0xff081322, PANEL=0xff132539, EDGE=0xff2b4961;
    private static final int WHITE=0xffecf5ff, MUTED=0xffa6bbd0, CYAN=0xff40d8e9, GREEN=0xff64e2ac, ORANGE=0xffffb85c;
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final ArrayList<BluetoothDevice> devices=new ArrayList<>();
    private TextView chosen, status, detail, capability;
    private Button connect, disconnect, save, choose;
    private ProgressBar progress;
    private BluetoothDevice selected;
    private volatile BluetoothSocket socket;
    private volatile boolean running=false, cancel=false;
    private JSONObject report;
    private final JSONArray readings=new JSONArray();
    private int requestCount=0;

    private int dp(int d){return (int)(getResources().getDisplayMetrics().density*d+0.5f);}
    private LinearLayout column(){
        LinearLayout v=new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(5),0,dp(5));return t;
    }
    private void gap(LinearLayout l,int height){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private GradientDrawable background(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(14));d.setStroke(dp(1),EDGE);return d;
    }
    private Button button(String s,int color){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setTextSize(14);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        return b;
    }
    private static String utc(){
        SimpleDateFormat d=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);
        d.setTimeZone(TimeZone.getTimeZone("UTC"));return d.format(new Date());
    }
    private static void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(Exception ignored){}}
    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout v=column();v.setPadding(dp(18),dp(17),dp(18),dp(28));
        sc.addView(v);setContentView(sc);
        v.addView(text("FORD VEHICLE DATA",25,CYAN,true));
        v.addView(text("Vehicle Capability Explorer 0.9.0",13,GREEN,true));
        gap(v,8);
        LinearLayout warning=column();warning.setPadding(dp(13),dp(10),dp(13),dp(10));warning.setBackground(background(PANEL));
        warning.addView(text("REAL ECU DATA — NOT PHONE GPS",15,WHITE,true));
        warning.addView(text("Park, set the parking brake and turn ignition ON. Pair a compatible Bluetooth Classic OBD-II adapter plugged into the Explorer diagnostic port. The regular Ford SYNC/Android Auto Bluetooth or USB connection does NOT grant engine-computer access.",13,MUTED,false));
        warning.addView(text("Only diagnostic read queries are sent. No codes are erased, ECU settings changed, or vehicle systems actuated.",12,GREEN,false));
        v.addView(warning);gap(v,12);
        choose=button("1  CHOOSE PAIRED OBD-II ADAPTER",0xff1c7287);v.addView(choose);
        chosen=text("No OBD-II adapter selected.",13,MUTED,false);v.addView(chosen);
        connect=button("2  CONNECT & READ VEHICLE SENSORS",0xff177b63);v.addView(connect);
        connect.setEnabled(false);
        disconnect=button("DISCONNECT / CANCEL",0xff77522c);v.addView(disconnect);
        disconnect.setEnabled(false);
        status=text("Not connected. No Ford ECU values have been read.",14,WHITE,true);v.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);v.addView(progress);
        detail=text("",12,MUTED,false);v.addView(detail);
        gap(v,9);
        v.addView(text("Actual data returned by the vehicle",17,CYAN,true));
        capability=text("Nothing verified yet. Values appear here ONLY after a real ECU responds.",13,WHITE,false);
        v.addView(capability);
        gap(v,12);
        save=button("SAVE VEHICLE DISCOVERY JSON",0xff42607e);save.setEnabled(false);
        v.addView(save);
        gap(v,11);
        v.addView(text("Ford module coverage",16,CYAN,true));
        v.addView(text("Generic OBD-II can test engine/emissions sensor PIDs, vehicle speed, some fuel data and stored powertrain diagnostic trouble codes. Tire-by-tire pressure, ABS, airbags, transmission details, doors, odometer, service history and other Ford-specific modules require additional Ford-aware diagnostic access. Those modules remain UNVERIFIED, not \"unsupported by Ford.\"",13,MUTED,false));
        v.addView(text("No simulated readings. No VIN or Bluetooth hardware address is included in exported reports. Bluetooth LE-only adapters require a different connection driver and are not supported in this test.",12,ORANGE,false));
        choose.setOnClickListener(w->chooseAdapter());
        connect.setOnClickListener(w->confirmConnect());
        disconnect.setOnClickListener(w->stopSession());
        save.setOnClickListener(w->saveReport());
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},PERM);
        }else{status.setText("Choose a previously paired Bluetooth Classic OBD-II adapter.");}
    }
    private void chooseAdapter(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},PERM);return;
        }
        devices.clear();
        try{
            BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){
                status.setText("Bluetooth is off/unavailable. Turn it on and pair an OBD-II adapter first.");return;
            }
            Set<BluetoothDevice> paired=adapter.getBondedDevices();
            if(paired!=null)for(BluetoothDevice dev:paired){
                String name=dev.getName();
                if(name==null)name="Unknown paired device";
                String lower=name.toLowerCase(Locale.US);
                // Do not offer Ford SYNC/head-unit audio profile as an ELM diagnostic adapter.
                if(lower.contains("sync")||lower.contains("ford audio")||lower.contains("ford sync"))continue;
                if(dev.getType()==BluetoothDevice.DEVICE_TYPE_LE)continue;
                devices.add(dev);
            }
            if(devices.isEmpty()){
                status.setText("No paired Bluetooth Classic devices. Pair an OBD-II adapter in Android Bluetooth settings, then retry.");return;
            }
            String[] labels=new String[devices.size()];
            for(int i=0;i<devices.size();i++){
                String name=devices.get(i).getName();
                labels[i]=(name==null?"Unnamed paired device":name)+" (Bluetooth Classic)";
            }
            new AlertDialog.Builder(this).setTitle("Select ONLY your OBD-II adapter — not Ford SYNC")
                .setItems(labels,(dialog,index)->{
                    selected=devices.get(index);
                    chosen.setText("Chosen: "+labels[index]+"\nNot yet connected to a vehicle computer.");
                    connect.setEnabled(!running);
                }).setNegativeButton("Cancel",null).show();
        }catch(SecurityException e){status.setText("Bluetooth permission denied.");}
        catch(Exception e){status.setText("Bluetooth pairing discovery failed: "+e.getClass().getSimpleName());}
    }
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] grant){
        super.onRequestPermissionsResult(req,permissions,grant);
        if(req==PERM){
            boolean ok=grant.length>0&&grant[0]==PackageManager.PERMISSION_GRANTED;
            status.setText(ok?"Bluetooth access granted. Select your OBD-II adapter.":"Bluetooth access is required to use a paired OBD-II adapter.");
        }
    }
    private void confirmConnect(){
        if(selected==null||running)return;
        new AlertDialog.Builder(this).setTitle("Begin read-only Ford ECU test?")
            .setMessage("Only do this while PARKED, with parking brake set and ignition on. Use a Bluetooth Classic OBD-II adapter. This test will ask for supported engine PIDs and stored trouble codes. It does not erase codes or control vehicle systems.")
            .setPositiveButton("CONNECT",(d,w)->begin())
            .setNegativeButton("Cancel",null).show();
    }
    private void begin(){
        if(running||selected==null)return;
        cancel=false;running=true;requestCount=0;
        while(readings.length()>0)readings.remove(readings.length()-1);
        report=new JSONObject();
        put(report,"report_format","Vehicle Capability Explorer Ford OBD-II discovery v1");
        put(report,"app_version","0.9.3");
        put(report,"started_at_utc",utc());
        put(report,"transport","Bluetooth Classic RFCOMM Serial Port Profile");
        put(report,"source","actual_ECU_replies_only");
        put(report,"adapter_name",selected.getName()==null?"unnamed":selected.getName());
        put(report,"vehicle_profile","owner_reports_2021_Ford_Explorer_Limited");
        put(report,"control_commands_sent",0);
        put(report,"dtc_clear_commands_sent",0);
        put(report,"usb_accessory_opened",false);
        put(report,"ford_specific_modules_verified",false);
        put(report,"vin_or_bluetooth_address_exported",false);
        progress.setProgress(0);
        capability.setText("Awaiting genuine vehicle replies. All readings unverified until response.");
        connect.setEnabled(false);choose.setEnabled(false);disconnect.setEnabled(true);save.setEnabled(false);
        status.setText("Opening Bluetooth diagnostic link...");
        Thread worker=new Thread(this::doDiagnostics,"Ford-OBD-readonly");
        worker.start();
    }
    private void ui(String s,int completed,int total){
        runOnUiThread(()->{
            if(isFinishing())return;
            status.setText(s);
            progress.setProgress(total==0?0:Math.min(100,completed*100/total));
            if(report!=null)detail.setText("Diagnostic requests completed: "+requestCount+" • control commands: 0");
        });
    }
    private String command(InputStream in,OutputStream out,String cmd) throws Exception {
        if(cancel)throw new InterruptedException("Cancelled");
        // Empty any pending response from the previous command.
        while(in.available()>0)in.read();
        out.write((cmd+"\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        requestCount++;
        StringBuilder s=new StringBuilder();
        long start=SystemClock.elapsedRealtime();
        long timeout=(cmd.equals("0100")||cmd.equals("ATSP0"))?6500:4000;
        while(SystemClock.elapsedRealtime()-start<timeout){
            if(cancel)throw new InterruptedException("Cancelled");
            int ready=in.available();
            while(ready-->0){
                int c=in.read();if(c<0)break;
                if(c=='>')return s.toString();
                if(s.length()<2048)s.append((char)c);
            }
            Thread.sleep(35);
        }
        return s.toString();
    }
    private static byte[] decode(String response,String prefix,int length) {
        if(response==null)return null;
        for(String line:response.toUpperCase(Locale.US).split("[\\r\\n]+")){
            if(line.contains("NO DATA")||line.contains("ERROR")||line.contains("UNABLE"))continue;
            // Keep only standard hexadecimal OBD response bytes. ELM spaces/headers are allowed.
            String h=line.replaceAll("[^0-9A-F]","");
            int at=h.indexOf(prefix);
            if(at<0||h.length()<at+prefix.length()+length*2)continue;
            byte[] out=new byte[length];
            try{
                for(int i=0;i<length;i++)out[i]=(byte)Integer.parseInt(h.substring(at+prefix.length()+i*2,at+prefix.length()+i*2+2),16);
                return out;
            }catch(Exception ignored){}
        }
        return null;
    }
    private static String value(int pid,byte[] b){
        final int a=b[0]&255;
        final int c=b.length>1?(b[1]&255):0;
        switch(pid){
            case 0x04:return String.format(Locale.US,"%.1f%% calculated engine load",a*100.0/255);
            case 0x05:return (a-40)+" °C coolant";
            case 0x0C:return String.format(Locale.US,"%.0f RPM",(a*256+c)/4.0);
            case 0x0D:return String.format(Locale.US,"%.1f mph",(a*0.621371));
            case 0x0F:return (a-40)+" °C intake air";
            case 0x10:return String.format(Locale.US,"%.2f g/s mass airflow",(a*256+c)/100.0);
            case 0x11:return String.format(Locale.US,"%.1f%% throttle position",a*100.0/255);
            case 0x2F:return String.format(Locale.US,"%.1f%% fuel tank level",a*100.0/255);
            case 0x42:return String.format(Locale.US,"%.2f V control module",(a*256+c)/1000.0);
            case 0x5E:return String.format(Locale.US,"%.2f L/hr engine fuel rate",(a*256+c)/20.0);
            default:return "received";
        }
    }
    private static String troubleCodes(String raw){
        if(raw==null||raw.trim().isEmpty())return "NOT_VERIFIED";
        if(raw.toUpperCase(Locale.US).contains("NO DATA"))return "NO_DATA";
        StringBuilder list=new StringBuilder();
        for(String line:raw.toUpperCase(Locale.US).split("[\\r\\n]+")){
            String h=line.replaceAll("[^0-9A-F]","");
            int start=h.indexOf("43");
            if(start<0)continue;
            String data=h.substring(start+2);
            // A vehicle may reply with a DTC count byte or ISO-TP segmentation;
            // return inconclusive instead of claiming no faults if decoding is ambiguous.
            if(data.length()<4)continue;
            for(int i=0;i+4<=data.length()&&list.length()<120;i+=4){
                try{
                    int x=Integer.parseInt(data.substring(i,i+2),16);
                    int y=Integer.parseInt(data.substring(i+2,i+4),16);
                    if(x==0&&y==0)continue;
                    char type="PCBU".charAt((x>>6)&3);
                    int second=(x>>4)&3;
                    String code=String.format(Locale.US,"%c%d%01X%02X",type,second,x&15,y);
                    if(list.length()>0)list.append(", ");
                    list.append(code);
                }catch(Exception ignored){}
            }
        }
        return list.length()>0?list.toString():"RESPONSE_RECEIVED_NO_DECODED_CODES";
    }
    private void append(JSONObject entry,String summary){
        readings.put(entry);
        runOnUiThread(()->{
            if(isFinishing())return;
            String old=capability.getText().toString();
            if(old.startsWith("Awaiting genuine"))old="";
            capability.setText(old+summary+"\n");
        });
    }
    private void doDiagnostics(){
        final int[] pids={0x0C,0x0D,0x05,0x2F,0x42,0x0F,0x10,0x11,0x04,0x5E};
        final int[] lengths={2,1,1,1,2,1,2,1,1,2};
        final String[] names={"Engine RPM","Vehicle speed from ECU","Engine coolant temperature",
            "Fuel tank level","ECU control-module voltage","Intake air temperature",
            "Mass airflow","Throttle position","Calculated engine load","Fuel rate"};
        final int total=4+pids.length+1;
        int done=0;boolean verified=false;
        try{
            BluetoothSocket link=selected.createRfcommSocketToServiceRecord(SPP);
            socket=link;
            link.connect();
            if(cancel)throw new InterruptedException("Cancelled");
            ui("Adapter connected. Initializing ELM327-compatible serial diagnostics...",done,total);
            InputStream in=link.getInputStream();
            OutputStream out=link.getOutputStream();
            // Configuration commands go to the adapter only; NOT ECU actuator/writes.
            command(in,out,"ATE0");command(in,out,"ATL0");command(in,out,"ATS0");
            command(in,out,"ATH0");command(in,out,"ATSP0");
            String identity=command(in,out,"ATI");
            put(report,"adapter_identification",identity.trim().replaceAll("[^\\p{Alnum} .,_-]"," ").substring(0,Math.min(120,identity.trim().replaceAll("[^\\p{Alnum} .,_-]"," ").length())));
            String supported00=command(in,out,"0100");
            byte[] mask=decode(supported00,"4100",4);
            done++;ui("Checking supported engine PIDs...",done,total);
            put(report,"obd_mode_01_supported_00",mask!=null?"RESPONSE_VERIFIED":"NO_VALID_RESPONSE");
            if(mask!=null){
                verified=true;
                append(new JSONObject().put("name","OBD-II service 01").put("status","VERIFIED_ECU_REPLY"),
                    "✓ OBD-II engine ECU responded to supported-PID query.");
            }else{
                append(new JSONObject().put("name","OBD-II service 01").put("status","NOT_VERIFIED"),
                    "— No valid ECU response to mode 01, PID 00.");
            }
            String proto=command(in,out,"ATDP");
            done++;ui("Reading detected OBD protocol...",done,total);
            put(report,"adapter_reported_protocol",proto.replaceAll("[\\r\\n>]+"," ").trim());
            String mask20=command(in,out,"0120");
            done++;ui("Checking additional supported PIDs...",done,total);
            put(report,"obd_mode_01_supported_20",decode(mask20,"4120",4)!=null?"RESPONSE_VERIFIED":"NOT_VERIFIED");
            String mask40=command(in,out,"0140");
            done++;ui("Checking extended generic PIDs...",done,total);
            put(report,"obd_mode_01_supported_40",decode(mask40,"4140",4)!=null?"RESPONSE_VERIFIED":"NOT_VERIFIED");
            for(int i=0;i<pids.length;i++){
                int pid=pids[i];
                ui("Reading "+names[i]+" from actual ECU...",done,total);
                String raw=command(in,out,String.format(Locale.US,"01%02X",pid));
                byte[] bytes=decode(raw,String.format(Locale.US,"41%02X",pid),lengths[i]);
                JSONObject entry=new JSONObject();
                put(entry,"pid",String.format(Locale.US,"01%02X",pid));
                put(entry,"name",names[i]);put(entry,"queried_at_utc",utc());
                if(bytes!=null){
                    String measurement=value(pid,bytes);
                    put(entry,"status","VERIFIED_ECU_VALUE");put(entry,"reading",measurement);
                    verified=true;append(entry,"✓ "+names[i]+": "+measurement+" [ECU]");
                }else{
                    String state=raw.toUpperCase(Locale.US).contains("NO DATA")?"NO_DATA":"NO_VALID_REPLY";
                    put(entry,"status",state);
                    append(entry,"— "+names[i]+": "+state+" (not confirmed)");
                }
                done++;ui("Completed "+names[i],done,total);
            }
            ui("Reading stored powertrain trouble codes (NO erase command)...",done,total);
            String dtcs=command(in,out,"03");
            String decoded=troubleCodes(dtcs);
            JSONObject dtcEntry=new JSONObject();
            put(dtcEntry,"name","Stored powertrain DTCs (mode 03)");
            put(dtcEntry,"status",decoded);
            put(dtcEntry,"queried_at_utc",utc());
            append(dtcEntry,"Stored powertrain codes: "+decoded);
            done++;ui("Vehicle read-only session complete.",done,total);
            put(report,"real_vehicle_ecu_data_verified",verified);
            put(report,"session_result",verified?"ECU_RESPONSE_VERIFIED":"ADAPTER_LINK_ONLY_NO_VERIFIED_ECU_REPLY");
        }catch(InterruptedException e){
            put(report,"session_result","CANCELLED");
            ui("Discovery cancelled. No ECU controls were used.",done,total);
        }catch(Exception e){
            put(report,"session_result","CONNECTION_OR_PROTOCOL_ERROR");
            put(report,"error_type",e.getClass().getSimpleName());
            ui("Could not establish/finish OBD link: "+e.getClass().getSimpleName(),done,total);
        }finally{
            closeSocket();
            put(report,"finished_at_utc",utc());
            put(report,"diagnostic_requests_and_adapter_commands_sent",requestCount);
            put(report,"readings",readings);
            // Preserve only real ECU values for a clearly labeled SYNC snapshot.
            try {
                boolean sensorVerified=false;
                for(int n=0;n<readings.length();n++){
                    JSONObject item=readings.optJSONObject(n);
                    if(item!=null && "VERIFIED_ECU_VALUE".equals(item.optString("status"))){
                        sensorVerified=true;break;
                    }
                }
                if(sensorVerified){
                    getSharedPreferences("ford_obd_verified",MODE_PRIVATE).edit()
                        .putString("last_verified_readings",readings.toString())
                        .putLong("last_verified_at_ms",System.currentTimeMillis()).apply();
                }
            }catch(Exception ignored){}
            runOnUiThread(()->{
                running=false;connect.setEnabled(selected!=null);
                choose.setEnabled(true);disconnect.setEnabled(false);
                save.setEnabled(report!=null);
                if(report!=null)detail.setText("Completed: "+requestCount+" adapter/diagnostic queries. Save JSON to send back for analysis.");
            });
        }
    }
    private void closeSocket(){
        BluetoothSocket s=socket;socket=null;
        if(s!=null)try{s.close();}catch(Exception ignored){}
    }
    private void stopSession(){cancel=true;closeSocket();status.setText("Stopping vehicle diagnostic session...");}
    private void saveReport(){
        if(report==null||running)return;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"Vehicle-Capability-Explorer-v0.9.3-FORD-DATA-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".json");
        startActivityForResult(i,SAVE);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==SAVE&&result==RESULT_OK&&data!=null&&data.getData()!=null&&report!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new IllegalStateException("No output stream");
                out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
                status.setText("Saved genuine vehicle discovery report.");
            }catch(Exception e){status.setText("Could not save report: "+e.getClass().getSimpleName());}
        }
    }
    @Override protected void onDestroy(){cancel=true;closeSocket();super.onDestroy();}
}
