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
    static final String ACTION_TEST_ALL = "com.newswire.vehicleporttester.action.TEST_EVERYTHING";
    private boolean pendingFullAudit=false, sdlReady=false;
    private OneTapAudit oneTapAudit;
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
        if (level == HMILevel.HMI_FULL && (oneTapAudit==null || !oneTapAudit.isRunning())) showFirstScreen();
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
        if(intent!=null && ACTION_TEST_ALL.equals(intent.getAction())){
            pendingFullAudit=true;
            OneTapAudit.waiting(this);
        }
        if(manager!=null){
            if(pendingFullAudit && sdlReady) mainHandler.post(this::beginFullAudit);
            return START_STICKY;
        }
        if(pendingFullAudit) mainHandler.postDelayed(()->{
            if(pendingFullAudit && !sdlReady){
                pendingFullAudit=false;
                OneTapAudit.noSync(this);
                record(this,"ALL_TEST_NO_SYNC","No Ford AppLink session, no ECU query sent");
            }
        },20000);
        record(this, "STARTING", "Waiting for an authorized SmartDeviceLink session.");
        try {
            MultiplexTransportConfig transport = new MultiplexTransportConfig(
                    this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
            transport.setPrimaryTransports(Collections.singletonList(TransportType.BLUETOOTH));
            Vector<AppHMIType> types = new Vector<>();
            types.add(AppHMIType.INFORMATION);
            SdlManagerListener listener = new SdlManagerListener() {
                @Override public void onStart() {
                    sdlReady=true;
                    record(SdlService.this, "SDL_SDK_STARTED",
                            "SDL manager reported startup. Check the HMI level before claiming the app is visible.");
                    showFirstScreen();
                    if(pendingFullAudit) mainHandler.post(SdlService.this::beginFullAudit);
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
    // An AppLink foreground template is not an Android Auto custom view.
    // All screens below are native SYNC 3 text fields and touch/voice menus.
    private static final String VERSION = "0.9.4";
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicBoolean diagnosticBusy = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final String[] DATA_LABELS = {
        "Engine RPM", "Vehicle speed", "Fuel level", "Odometer",
        "Oil life", "Tire status", "Body information"
    };
    private static final String[] DATA_KEYS = {
        "rpm", "speed", "fuel_level", "odometer",
        "engine_oil_life", "tire_pressure", "body_information"
    };

    private MenuCell cell(String title, final Runnable action) {
        return new MenuCell(title, null, null, null, null,
                java.util.Collections.<String>emptyList(),
                new MenuSelectionListener() {
                    @Override public void onTriggered(TriggerSource trigger) {
                        mainHandler.post(action);
                    }
                });
    }
    private void showFirstScreen() {
        if (manager == null) return;
        try {
            showLines("SYNC 3 DISPLAY CONNECTED",
                    "Open Menu for Ford vehicle data",
                    "No sensor values assumed");
            java.util.List<MenuCell> menu = new java.util.ArrayList<>();
            menu.add(cell("TEST EVERYTHING - ONE TAP", this::beginFullAudit));
            menu.add(cell("Last full test results", this::showFullAuditResult));
            menu.add(cell("Vehicle overview", this::showOverview));
            menu.add(cell("Engine RPM", () -> readOne(0)));
            menu.add(cell("Vehicle speed", () -> readOne(1)));
            menu.add(cell("Fuel level", () -> readOne(2)));
            menu.add(cell("Odometer", () -> readOne(3)));
            menu.add(cell("Oil life", () -> readOne(4)));
            menu.add(cell("Tire status", () -> readOne(5)));
            menu.add(cell("Body information", () -> readOne(6)));
            menu.add(cell("Read permissions", this::showPermissionSummary));
            menu.add(cell("OBD-II saved results", this::showObdSnapshot));
            menu.add(cell("Diagnostics / codes", this::showDiagnosticsHelp));
            menu.add(cell("Connection status", this::showConnectionStatus));
            manager.getScreenManager().setMenu(menu);
            record(this, "FORD_MENU_READY", "SYNC 3 menu created. Ford policy may still restrict individual readings.");
        } catch (Exception e) {
            record(this, "SCREEN_ERROR", "SYNC 3 menu failed: " + e.getClass().getSimpleName());
        }
    }
    private void showLines(String second, String third, String fourth) {
        if (manager == null) return;
        try {
            manager.getScreenManager().setTextField1("VEHICLE EXPLORER " + VERSION);
            manager.getScreenManager().setTextField2(shortText(second));
            manager.getScreenManager().setTextField3(shortText(third));
            manager.getScreenManager().setTextField4(shortText(fourth));
        } catch (Exception e) {
            record(this, "SCREEN_UPDATE_ERROR", e.getClass().getSimpleName());
        }
    }
    private static String shortText(String input) {
        if (input == null) return "";
        return input.length() > 75 ? input.substring(0, 72) + "..." : input;
    }
    private android.content.SharedPreferences vehiclePrefs() {
        return getSharedPreferences("ford_data_v093", MODE_PRIVATE);
    }
    private void beginFullAudit() {
        if(!sdlReady || manager==null) {
            pendingFullAudit=true;
            OneTapAudit.waiting(this);
            showLines("SYNC 3 not connected", "Connect Ford Bluetooth first","Test will wait for connection");
            return;
        }
        pendingFullAudit=false;
        if(oneTapAudit==null) oneTapAudit=new OneTapAudit(this,manager);
        oneTapAudit.start();
    }
    private void showFullAuditResult() {
        if(oneTapAudit==null) oneTapAudit=new OneTapAudit(this,manager);
        oneTapAudit.showLast();
    }
    private void showOverview() {
        android.content.SharedPreferences p = vehiclePrefs();
        showLines("2021 Ford Explorer Limited",
            "Ford data: " + p.getInt("verified_count", 0) + " confirmed fields",
            "Read actual ECU data from Menu");
        record(this, "OVERVIEW_OPENED", "Owner-reported vehicle profile; no inferred engine readings.");
    }
    private void showConnectionStatus() {
        String hmi = getSharedPreferences("sdl_discovery_v091",MODE_PRIVATE)
                .getString("hmi_level", "NOT_RECEIVED");
        showLines("AppLink connected", "Ford HMI: " + hmi, "USB not opened by Vehicle Explorer");
        record(this, "CONNECTION_STATUS_VIEWED", "SYNC 3 HMI level " + hmi);
    }
    private void showDiagnosticsHelp() {
        showLines("Powertrain fault codes", "Requires Bluetooth OBD-II adapter",
                "Open Ford OBD-II test on phone");
        record(this, "DIAGNOSTICS_HELP", "SDL GetVehicleData is not a Ford trouble-code reader.");
    }
    private void showObdSnapshot() {
        android.content.SharedPreferences p = getSharedPreferences("ford_obd_verified",MODE_PRIVATE);
        String json = p.getString("last_verified_readings", "");
        long at = p.getLong("last_verified_at_ms", 0);
        if (json.isEmpty() || at <= 0) {
            showLines("No verified OBD-II scan",
                "Pair an OBD-II adapter to phone",
                "Run Ford OBD-II Data scan first");
            record(this, "OBD_SNAPSHOT_UNAVAILABLE", "No actual ECU reading saved.");
            return;
        }
        try {
            org.json.JSONArray rows = new org.json.JSONArray(json);
            String first = "", second = "";
            for (int i=0;i<rows.length();i++) {
                org.json.JSONObject row = rows.optJSONObject(i);
                if (row == null || !"VERIFIED_ECU_VALUE".equals(row.optString("status"))) continue;
                String reading = shortText(row.optString("name")+": "+row.optString("reading"));
                if (first.isEmpty()) first = reading;
                else if (second.isEmpty()) { second = reading; break; }
            }
            if (first.isEmpty()) { showLines("OBD scan saved", "No confirmed sensor values", "Check OBD report on phone"); return; }
            showLines("OBD snapshot (not live)", first, second.isEmpty()?"Open phone for full report":second);
            record(this, "OBD_SNAPSHOT_VIEWED", "Saved verified ECU values. Snapshot age ms: "+(System.currentTimeMillis()-at));
        } catch (Exception e) {
            showLines("OBD report unreadable", "Re-run diagnostic scan on phone", "No simulated values");
        }
    }
    private void showPermissionSummary() {
        android.content.SharedPreferences p=vehiclePrefs();
        int allowed=0, blocked=0, noData=0, untested=0;
        for (String key : DATA_KEYS) {
            String status = p.getString("permission_"+key, "NOT_TESTED");
            if ("VERIFIED".equals(status)) allowed++;
            else if ("DISALLOWED".equals(status)) blocked++;
            else if ("NOT_TESTED".equals(status)) untested++;
            else noData++;
        }
        showLines("Ford data permission tests",
                allowed+" verified / "+blocked+" disallowed",
                noData+" no value/error, "+untested+" untested");
        record(this,"PERMISSIONS_VIEWED", "Individual Ford fields; verified="+allowed+", disallowed="+blocked+", other="+noData+", untested="+untested);
    }
    private GetVehicleData makeRequest(int which) {
        GetVehicleData request = new GetVehicleData();
        switch(which){
            case 0: request.setRpm(true); break;
            case 1: request.setSpeed(true); break;
            case 2: request.setFuelLevel(true); break;
            case 3: request.setOdometer(true); break;
            case 4: request.setEngineOilLife(true); break;
            case 5: request.setTirePressure(true); break;
            case 6: request.setBodyInformation(true); break;
            default: throw new IllegalArgumentException("Unknown vehicle field");
        }
        return request;
    }
    private void readOne(final int which) {
        if(oneTapAudit!=null && oneTapAudit.isRunning()){
            showLines("Full audit in progress","Wait until TEST EVERYTHING finishes","7 fields tested automatically");
            return;
        }
        if (manager == null || which < 0 || which >= DATA_KEYS.length) return;
        if (!diagnosticBusy.compareAndSet(false,true)) {
            showLines("Request already running", "Wait for Ford's response", "Please try again");
            return;
        }
        String label=DATA_LABELS[which];
        String key=DATA_KEYS[which];
        showLines("Requesting "+label, "Ford policy decides access", "Waiting for actual vehicle reply");
        record(this, "FORD_READ_REQUEST", label + " (one field, read only)");
        GetVehicleData req = makeRequest(which);
        final java.util.concurrent.atomic.AtomicBoolean completed=new java.util.concurrent.atomic.AtomicBoolean(false);
        Runnable timeout=()->{
            if (!completed.compareAndSet(false,true)) return;
            vehiclePrefs().edit().putString("permission_"+key,"TIMEOUT").apply();
            diagnosticBusy.set(false);
            showLines(label+": timed out", "No Ford data was verified", "Retry from Menu");
            record(this,"FORD_READ_TIMEOUT",label);
        };
        mainHandler.postDelayed(timeout,11000);
        req.setOnRPCResponseListener(new OnRPCResponseListener(){
            @Override public void onResponse(int correlationId, RPCResponse response) {
                mainHandler.post(()->{
                    if (!completed.compareAndSet(false,true)) return;
                    mainHandler.removeCallbacks(timeout);
                    diagnosticBusy.set(false);
                    if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
                        String reason=response==null?"NO_RESPONSE":String.valueOf(response.getResultCode());
                        vehiclePrefs().edit().putString("permission_"+key,reason).apply();
                        showLines(label+": not permitted", "Ford returned: "+reason, "No data was read");
                        record(SdlService.this, "FORD_READ_DENIED",label+": "+reason);
                        return;
                    }
                    GetVehicleDataResponse r=(GetVehicleDataResponse)response;
                    String reading=readingFor(which,r);
                    if (reading == null) {
                        vehiclePrefs().edit().putString("permission_"+key,"NO_DATA").apply();
                        showLines(label+": no value", "Ford replied, field is empty", "Nothing estimated");
                        record(SdlService.this,"FORD_READ_EMPTY",label+" returned null");
                        return;
                    }
                    android.content.SharedPreferences pref=vehiclePrefs();
                    int old=pref.getInt("verified_count",0);
                    boolean newField=!"VERIFIED".equals(pref.getString("permission_"+key,"NOT_TESTED"));
                    pref.edit().putString("permission_"+key,"VERIFIED")
                        .putString("value_"+key,reading)
                        .putLong("sample_time_"+key,System.currentTimeMillis())
                        .putInt("verified_count",old+(newField?1:0)).apply();
                    showLines(label+" [Ford ECU]",reading,"ACTUAL SYNC data, not phone GPS");
                    record(SdlService.this,"FORD_READ_VERIFIED",label+": "+reading);
                });
            }
        });
        try { manager.sendRPC(req); }
        catch(Exception e) {
            if (completed.compareAndSet(false,true)) {
                mainHandler.removeCallbacks(timeout);
                diagnosticBusy.set(false);
                vehiclePrefs().edit().putString("permission_"+key,"SEND_ERROR").apply();
                showLines(label+" request error",e.getClass().getSimpleName(),"No data verified");
                record(this,"FORD_SEND_ERROR",label+": "+e.getClass().getSimpleName());
            }
        }
    }
    private static String readingFor(int i, GetVehicleDataResponse r){
        switch(i){
            case 0: return r.getRpm()==null?null:r.getRpm()+" rpm";
            case 1: return r.getSpeed()==null?null:r.getSpeed()+" km/h";
            case 2: return r.getFuelLevel()==null?null:r.getFuelLevel()+"%";
            case 3: return r.getOdometer()==null?null:r.getOdometer()+" km";
            case 4: return r.getEngineOilLife()==null?null:r.getEngineOilLife()+"%";
            case 5: return r.getTirePressure()==null?null:"Tire status supplied; see phone for details";
            case 6: return r.getBodyInformation()==null?null:"Body info supplied; see phone for details";
            default: return null;
        }
    }
    @Override public void onDestroy(){
        mainHandler.removeCallbacksAndMessages(null);
        if(oneTapAudit!=null && oneTapAudit.isRunning()){
            getSharedPreferences(OneTapAudit.PREF,MODE_PRIVATE).edit()
                .putString("status","INTERRUPTED")
                .putString("stage","SYNC 3 service disconnected before completing audit").apply();
        }
        SdlManager old=manager;manager=null;
        if(old!=null)try{old.dispose();}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
