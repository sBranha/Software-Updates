package com.newswire.vehicleporttester;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.lifecycle.LifecycleConfigurationUpdate;
import com.smartdevicelink.managers.screen.menu.MenuCell;
import com.smartdevicelink.managers.screen.menu.MenuSelectionListener;
import com.smartdevicelink.proxy.rpc.GetVehicleData;
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import org.json.JSONArray;
import org.json.JSONObject;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.Language;
import com.smartdevicelink.proxy.rpc.enums.TriggerSource;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.enums.TransportType;
import com.smartdevicelink.util.SystemInfo;
import java.util.Collections;
import java.util.Vector;

/** Genuine SDL service. Does not imitate Android Auto media/nav categories.
 * The test registration ID is NOT production-certified or approved for Ford
 * vehicle data. An OEM policy can reject either registration or GetVehicleData.
 */
public final class SdlService extends Service {
    static final String APP_ID = "VCEXP2026TEST";
    static final String APP_NAME = "Vehicle Explorer";
    private volatile SdlManager manager;

    public static synchronized void record(Context c, String state, String detail) {
        android.content.SharedPreferences prefs =
                c.getSharedPreferences("sdl_discovery_v091", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        try {
            JSONArray history = new JSONArray(prefs.getString("event_history", "[]"));
            history.put(new JSONObject().put("time_ms", now).put("state", state)
                    .put("detail", detail == null ? "" : detail));
            JSONArray bounded = new JSONArray();
            for (int i = Math.max(0, history.length() - 45); i < history.length(); i++)
                bounded.put(history.getJSONObject(i));
            prefs.edit().putString("event_history", bounded.toString())
                    .putString("state", state).putString("detail", detail == null ? "" : detail)
                    .putLong("updated_at_ms", now).apply();
        } catch (Exception ex) {
            prefs.edit().putString("state", state).putString("detail", detail)
                    .putLong("updated_at_ms", now).apply();
        }
    }
    private void onHmiNotification(RPCNotification notification) {
        if (!(notification instanceof OnHMIStatus)) return;
        OnHMIStatus event = (OnHMIStatus) notification;
        HMILevel level = event.getHmiLevel();
        String name = level == null ? "UNKNOWN" : level.toString();
        android.content.SharedPreferences prefs = getSharedPreferences("sdl_discovery_v091", MODE_PRIVATE);
        prefs.edit().putString("hmi_level", name)
                .putBoolean("hmi_ever_full", level == HMILevel.HMI_FULL || prefs.getBoolean("hmi_ever_full", false))
                .apply();
        record(this, "HMI_" + name, "Actual Ford head-unit HMI level changed: " + name);
        if (level == HMILevel.HMI_FULL) showFirstScreen();
    }
    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("sdl_vehicle_explorer",
                    "Vehicle Explorer AppLink", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, channel.getId())
                    .setContentTitle("Vehicle Explorer - SYNC 3")
                    .setContentText("Checking Ford AppLink connection")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details).build();
            startForeground(913, notification);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (manager != null) return START_STICKY;
        record(this, "STARTING", "Waiting for an authorized SmartDeviceLink session.");
        try {
            MultiplexTransportConfig transport = new MultiplexTransportConfig(
                    this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
            transport.setPrimaryTransports(Collections.singletonList(TransportType.BLUETOOTH));
            Vector<AppHMIType> types = new Vector<>();
            types.add(AppHMIType.INFORMATION);
            SdlManagerListener listener = new SdlManagerListener() {
                @Override public void onStart() {
                    record(SdlService.this, "SDL_SDK_STARTED",
                            "SDL manager reported startup. Check the HMI level before claiming the app is visible.");
                    showFirstScreen();
                }
                @Override public void onDestroy() {
                    record(SdlService.this, "DISCONNECTED", "AppLink manager disconnected.");
                    stopSelf();
                }
                @Override public void onError(String info, Exception error) {
                    record(SdlService.this, "SDL_ERROR", (info==null?"unknown error":info)
                            + (error==null?"":": "+error.getClass().getSimpleName()));
                }
                @Override public LifecycleConfigurationUpdate managerShouldUpdateLifecycle(
                        Language language, Language hmiLanguage) { return null; }
                @Override public boolean onSystemInfoReceived(SystemInfo info) { return true; }
            };
            SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
            builder.setAppTypes(types);
            builder.setTransportType(transport);
            java.util.Map<FunctionID, OnRPCNotificationListener> notifications = new java.util.HashMap<>();
            notifications.put(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
                @Override public void onNotified(RPCNotification notification) {
                    onHmiNotification(notification);
                }
            });
            builder.setRPCNotificationListeners(notifications);
            manager = builder.build();
            manager.start();
        } catch(Exception e) {
            record(this, "START_FAILED", e.getClass().getSimpleName());
            stopSelf();
        }
        return START_STICKY;
    }
    private void showFirstScreen() {
        if (manager == null) return;
        try {
            manager.getScreenManager().setTextField1("VEHICLE EXPLORER 0.9.1");
            manager.getScreenManager().setTextField2("SYNC 3 AppLink connection OK");
            manager.getScreenManager().setTextField3("Open menu: Read Ford data");
            manager.getScreenManager().setTextField4("No vehicle readings yet");
            MenuCell read = new MenuCell("Read Ford data", null, null, null,
                    null, Collections.singletonList("read ford data"),
                    new MenuSelectionListener() {
                        @Override public void onTriggered(TriggerSource trigger) {
                            requestVehicleData();
                        }
                    });
            MenuCell about = new MenuCell("Connection status", null, null, null,
                    null, Collections.singletonList("connection status"),
                    new MenuSelectionListener() {
                        @Override public void onTriggered(TriggerSource trigger) {
                            showFirstScreenFields("SYNC 3 AppLink active","Vehicle data: not confirmed",
                                    "Developer test ID / OEM policy");
                        }
                    });
            manager.getScreenManager().setMenu(java.util.Arrays.asList(read, about));
        } catch(Exception e){
            record(this,"SCREEN_ERROR",e.getClass().getSimpleName());
        }
    }
    private void showFirstScreenFields(String line2,String line3,String line4) {
        if (manager==null)return;
        try {
            manager.getScreenManager().setTextField1("VEHICLE EXPLORER 0.9.1");
            manager.getScreenManager().setTextField2(line2);
            manager.getScreenManager().setTextField3(line3);
            manager.getScreenManager().setTextField4(line4);
        } catch(Exception e){record(this,"SCREEN_UPDATE_ERROR",e.getClass().getSimpleName());}
    }
    private void requestVehicleData() {
        if (manager==null)return;
        record(this,"VEHICLE_QUERY_SENT","Requesting policy-authorized, read-only Ford data.");
        showFirstScreenFields("Reading Ford vehicle data...","Waiting for OEM permission","Unverified readings stay blank");
        GetVehicleData request = new GetVehicleData()
            .setRpm(true).setSpeed(true).setOdometer(true).setFuelLevel(true)
            .setEngineOilLife(true).setTirePressure(true).setBodyInformation(true);
        request.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override public void onResponse(int correlationId, RPCResponse response) {
                if (response==null || !Boolean.TRUE.equals(response.getSuccess())) {
                    String reason = response==null?"no response":String.valueOf(response.getResultCode());
                    record(SdlService.this, "VEHICLE_DATA_REJECTED",reason);
                    showFirstScreenFields("SYNC connected; Ford data denied",
                            "OEM authorization may be needed",reason);
                    return;
                }
                GetVehicleDataResponse r=(GetVehicleDataResponse)response;
                StringBuilder b=new StringBuilder();
                if (r.getRpm()!=null) b.append("RPM=").append(r.getRpm()).append("; ");
                if (r.getSpeed()!=null) b.append("speedKph=").append(r.getSpeed()).append("; ");
                if (r.getFuelLevel()!=null) b.append("fuelPct=").append(r.getFuelLevel()).append("; ");
                if (r.getOdometer()!=null) b.append("odoKm=").append(r.getOdometer()).append("; ");
                if (r.getEngineOilLife()!=null) b.append("oilLife=").append(r.getEngineOilLife()).append("; ");
                if (r.getTirePressure()!=null) b.append("tireStatus=received; ");
                if (r.getBodyInformation()!=null) b.append("bodyStatus=received; ");
                String values=b.toString().trim();
                if (values.isEmpty()) {
                    record(SdlService.this,"EMPTY_VEHICLE_RESPONSE",
                            "SYNC permitted a query but returned none of the requested fields.");
                    showFirstScreenFields("Vehicle-data response received",
                            "Requested data not supplied","See phone for access status");
                } else {
                    record(SdlService.this,"FORD_DATA_VERIFIED",values);
                    String rpm=r.getRpm()==null?"RPM unavailable":"RPM "+r.getRpm();
                    String fuel=r.getFuelLevel()==null?"fuel unavailable":"Fuel "+r.getFuelLevel()+"%";
                    showFirstScreenFields(rpm,fuel,"Actual Ford values; see phone");
                }
            }
        });
        try {manager.sendRPC(request);}
        catch(Exception e){
            record(this,"QUERY_ERROR",e.getClass().getSimpleName());
            showFirstScreenFields("Vehicle query failed",e.getClass().getSimpleName(),"No values verified");
        }
    }
    @Override public void onDestroy(){
        SdlManager old=manager;manager=null;
        if(old!=null)try{old.dispose();}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
