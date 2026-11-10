package com.newswire.vehicleporttester;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.rpc.GetVehicleData;
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import java.util.concurrent.atomic.AtomicBoolean;

/** One user action runs every available read-only test. Ford cloud credentials
 * are NOT fabricated or extracted from Ford's private mobile app.
 * Every OBD/cloud unavailable status is marked explicitly as NOT TESTED.
 */
final class OneTapAudit {
    static final String PREF = "ford_audit_v094";
    static final String[] KEYS={"rpm","speed","fuel_level","odometer",
            "engine_oil_life","tire_pressure","body_information"};
    private static final String[] LABELS={"Engine RPM","Vehicle speed","Fuel level",
            "Odometer","Oil life","Tire status","Body information"};
    private final Context context;
    private final SdlManager sdl;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean running=false;
    private int current=0;
    private final SharedPreferences preferences;

    OneTapAudit(Context ctx, SdlManager sdlManager){
        context=ctx.getApplicationContext();sdl=sdlManager;
        preferences=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static void waiting(Context ctx){
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString("status","WAITING_FOR_SYNC")
                .putString("stage","Waiting for SYNC 3 to register")
                .putInt("complete",0).putInt("total",7)
                .putLong("started_ms",System.currentTimeMillis())
                .putLong("ended_ms",0).apply();
    }
    static void noSync(Context ctx){
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString("status","NO_SYNC_CONNECTION")
                .putString("stage","No live Ford AppLink session; no vehicle query sent")
                .putLong("ended_ms",System.currentTimeMillis()).apply();
    }
    boolean isRunning(){return running;}
    void start(){
        if(running){display("TEST ALREADY RUNNING",current+"/7 complete","Wait for the result");return;}
        running=true;current=0;
        SharedPreferences.Editor e=preferences.edit()
            .putString("status","RUNNING")
            .putString("stage","Checking SYNC and data interfaces")
            .putInt("complete",0).putInt("total",7).putInt("verified",0)
            .putInt("disallowed",0).putInt("other",0)
            .putLong("started_ms",System.currentTimeMillis())
            .putLong("ended_ms",0)
            .putString("sync_transport","SMARTDEVICELINK_BLUETOOTH_REGISTERED")
            .putString("fordconnect_cloud",FordConnectStatusActivity.cloudState())
            .putString("fordconnect_phone_internet",FordConnectStatusActivity.internetState(context))
            .putString("fordconnect_oauth","NOT_AUTHORIZED")
            .putString("fordconnect_api_response","NOT_TESTED_NO_APPROVED_CLIENT")
            .putString("ford_app_data","NOT_ACCESSIBLE_VIA_ANOTHER_APP_SESSION")
            .putString("obd2_live","NOT_TESTED_NO_OBD2_ADAPTER_CONNECTED")
            .putString("usb","NOT_TOUCHED_ANDROID_AUTO_UNINTERRUPTED");
        boolean granted=Build.VERSION.SDK_INT<31 ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        e.putString("bluetooth_permission",granted?"GRANTED":"MISSING");
        SharedPreferences sdlPrefs=context.getSharedPreferences("sdl_discovery_v091",Context.MODE_PRIVATE);
        e.putString("sync_hmi_at_start",sdlPrefs.getString("hmi_level","NOT_REPORTED"));
        boolean hasSnapshot=!context.getSharedPreferences("ford_obd_verified",Context.MODE_PRIVATE)
                .getString("last_verified_readings","").isEmpty();
        e.putString("obd2_saved_snapshot",hasSnapshot?"PRIOR_VERIFIED_SNAPSHOT":"NO_VERIFIED_SNAPSHOT");
        long manualAt=context.getSharedPreferences(FordConnectStatusActivity.MANUAL_PREFS,Context.MODE_PRIVATE)
            .getLong("saved_ms",0);
        e.putString("ford_app_manual_snapshot",manualAt>0?"USER_ENTERED_SNAPSHOT":"NONE");
        e.putLong("ford_app_manual_snapshot_ms",manualAt);
        for(String key:KEYS) e.putString("result_"+key,"PENDING").putString("value_"+key,"");
        e.apply();
        SdlService.record(context,"ALL_TEST_START",
                "One-tap Ford scan; 7 single-field ECU requests, cloud and OBD status");
        display("TEST EVERYTHING STARTED","SYNC 3 connected; 7 vehicle fields","Ford cloud authorization checked");
        handler.postDelayed(this::testNext,300);
    }
    private void display(String line2,String line3,String line4){
        try{
            sdl.getScreenManager().setTextField1("VEHICLE EXPLORER 0.9.5");
            sdl.getScreenManager().setTextField2(line2);
            sdl.getScreenManager().setTextField3(line3);
            sdl.getScreenManager().setTextField4(line4);
        }catch(Exception e){SdlService.record(context,"ALL_TEST_SCREEN_ERROR",e.getClass().getSimpleName());}
    }
    private GetVehicleData request(int which){
        GetVehicleData r=new GetVehicleData();
        switch(which){
            case 0:r.setRpm(true);break;
            case 1:r.setSpeed(true);break;
            case 2:r.setFuelLevel(true);break;
            case 3:r.setOdometer(true);break;
            case 4:r.setEngineOilLife(true);break;
            case 5:r.setTirePressure(true);break;
            case 6:r.setBodyInformation(true);break;
            default:throw new IllegalArgumentException("Bad field");
        }
        return r;
    }
    private static String value(int which,GetVehicleDataResponse r){
        switch(which){
            case 0:return r.getRpm()==null?null:r.getRpm()+" rpm";
            case 1:return r.getSpeed()==null?null:r.getSpeed()+" km/h";
            case 2:return r.getFuelLevel()==null?null:r.getFuelLevel()+" %";
            case 3:return r.getOdometer()==null?null:r.getOdometer()+" km";
            case 4:return r.getEngineOilLife()==null?null:r.getEngineOilLife()+" %";
            case 5:return r.getTirePressure()==null?null:"Vehicle returned tire status";
            case 6:return r.getBodyInformation()==null?null:"Vehicle returned body information";
            default:return null;
        }
    }
    private void testNext(){
        if(!running)return;
        if(current>=KEYS.length){finish();return;}
        final int which=current;
        final String label=LABELS[which];
        preferences.edit().putString("stage","Testing "+label)
                .putInt("current_request",which+1).apply();
        display("TESTING "+(which+1)+"/7: "+label,"Reading through Ford AppLink","Awaiting actual response");
        SdlService.record(context,"ALL_TEST_FIELD",label+" "+(which+1)+"/7");
        GetVehicleData req=request(which);
        final AtomicBoolean finished=new AtomicBoolean(false);
        Runnable timeout=()->{
            if(finished.compareAndSet(false,true))
                completeOne(which,"TIMEOUT","", "No reply within 10 seconds");
        };
        handler.postDelayed(timeout,10000);
        req.setOnRPCResponseListener(new OnRPCResponseListener(){
            @Override public void onResponse(int correlationId,RPCResponse response){
                handler.post(()->{
                    if(!finished.compareAndSet(false,true))return;
                    handler.removeCallbacks(timeout);
                    if(response==null){
                        completeOne(which,"NO_RESPONSE","","No Ford response");
                    }else if(!Boolean.TRUE.equals(response.getSuccess())){
                        String result=String.valueOf(response.getResultCode());
                        completeOne(which,result,"","Ford rejected "+label);
                    }else if(response instanceof GetVehicleDataResponse){
                        String found=value(which,(GetVehicleDataResponse)response);
                        if(found!=null)completeOne(which,"VERIFIED",found,"Confirmed Ford data");
                        else completeOne(which,"EMPTY_FIELD","","Reply returned no "+label);
                    }else{
                        completeOne(which,"UNEXPECTED_RESPONSE","","Reply not a GetVehicleDataResponse");
                    }
                });
            }
        });
        try{sdl.sendRPC(req);}
        catch(Exception error){
            if(finished.compareAndSet(false,true)){
                handler.removeCallbacks(timeout);
                completeOne(which,"SEND_ERROR","","Send failed: "+error.getClass().getSimpleName());
            }
        }
    }
    private void completeOne(int which,String result,String value,String detail){
        if(!running || which!=current)return;
        String key=KEYS[which],label=LABELS[which];
        preferences.edit().putString("result_"+key,result)
                .putString("value_"+key,value)
                .putString("detail_"+key,detail)
                .putLong("time_"+key,System.currentTimeMillis())
                .putInt("complete",which+1).apply();
        SdlService.record(context,"ALL_TEST_RESULT",label+": "+result+(value.isEmpty()?"":" "+value));
        current=which+1;
        display("RESULT "+current+"/7: "+label,result,
                current==7?"Preparing final report":"Continuing to next field");
        handler.postDelayed(this::testNext,500);
    }
    private void finish(){
        if(!running)return;
        int verified=0,disallowed=0,other=0;
        for(String key:KEYS){
            String status=preferences.getString("result_"+key,"NOT_TESTED");
            if("VERIFIED".equals(status))verified++;
            else if("DISALLOWED".equals(status))disallowed++;
            else other++;
        }
        preferences.edit().putString("status","COMPLETE")
                .putString("stage","Finished 7/7 individual vehicle queries")
                .putInt("complete",7).putInt("verified",verified)
                .putInt("disallowed",disallowed).putInt("other",other)
                .putLong("ended_ms",System.currentTimeMillis()).apply();
        running=false;
        display("TEST COMPLETE 7/7",
                verified+" REAL / "+disallowed+" DENIED / "+other+" OTHER",
                "Open phone to save full report");
        SdlService.record(context,"ALL_TEST_COMPLETE",
                verified+" verified; "+disallowed+" disallowed; "+other+" other");
    }
    void showLast(){
        String state=preferences.getString("status","NOT_RUN");
        if("RUNNING".equals(state)){
            display("TEST RUNNING "+preferences.getInt("complete",0)+"/7",
                preferences.getString("stage","Running"),"Please wait for completion");
        }else if("COMPLETE".equals(state)){
            display("LAST TEST COMPLETE",
                preferences.getInt("verified",0)+" verified / "+preferences.getInt("disallowed",0)+" denied",
                "Saved test details are on phone");
        }else{
            display("TEST STATUS: "+state,"Tap TEST EVERYTHING","No data has been assumed");
        }
    }
}
