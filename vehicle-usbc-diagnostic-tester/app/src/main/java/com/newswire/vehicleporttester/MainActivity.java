package com.newswire.vehicleporttester;

import android.app.Activity;
import android.app.PendingIntent;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.BatteryManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final String USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final int EXPORT_JSON = 9001;
    private TextView summary;
    private TextView details;
    private TextView footer;
    private TextView scanStatus;
    private ProgressBar scanProgress;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean scanActive = false;
    private String lastReport = "";
    private String latestFilename = "";
    private final BroadcastReceiver usbChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (USB_STATE.equals(intent.getAction())) runScan("USB state changed", true);
        }
    };
    private boolean receiverRegistered;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildLayout();
        runScan("App started", true);
    }

    @Override public void onStart() {
        super.onStart();
        try {
            IntentFilter filter = new IntentFilter(USB_STATE);
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(usbChanged, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(usbChanged, filter);
            receiverRegistered = true;
        } catch (Exception ex) {
            footer.setText("USB change notifications unavailable: tap Scan Connection after plugging in.");
        }
    }

    @Override public void onStop() {
        if (receiverRegistered) {
            try { unregisterReceiver(usbChanged); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(11, 22, 38));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(30), dp(22), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView eyebrow = text("READ-ONLY DISCOVERY • NO VEHICLE CONTROL", 12, 0xff84d9cc, true);
        root.addView(eyebrow);
        TextView heading = text("Vehicle Port Tester\nv0.4.0", 28, Color.WHITE, true);
        LinearLayout.LayoutParams h = new LinearLayout.LayoutParams(-1, -2);
        h.topMargin = dp(12);
        root.addView(heading, h);
        TextView intro = text("Compare unplugged, USB-C and USB-A. Deep Scan also checks accessible USB descriptors, permissions and phone-side car-app services. It cannot assume access to Ford SYNC internals.", 15, 0xffb8c9d8, false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(12);
        ip.bottomMargin = dp(20);
        root.addView(intro, ip);

        TextView instructions = text("Ignition/accessory power ON, vehicle parked. Change the cable first, then tap the matching button.", 14, 0xff84d9cc, false);
        root.addView(instructions);
        scanStatus = text("Ready to scan", 14, 0xff84d9cc, true);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2);
        stp.topMargin = dp(14);
        root.addView(scanStatus, stp);
        scanProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        scanProgress.setMax(100);
        scanProgress.setProgress(0);
        scanProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams prog = new LinearLayout.LayoutParams(-1, dp(10));
        prog.topMargin = dp(8);
        prog.bottomMargin = dp(12);
        root.addView(scanProgress, prog);
        Button unplugged = button("1  SCAN UNPLUGGED", 0xff29435a);
        root.addView(unplugged);
        unplugged.setOnClickListener(v -> runScan("Test: disconnected", true));
        Button usbC = button("2  SCAN USB-C PORT", 0xff247ec6);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(10);
        root.addView(usbC, cp);
        usbC.setOnClickListener(v -> runScan("Test: usb_c", true));
        Button usbA = button("3  SCAN REGULAR USB PORT", 0xff247ec6);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = dp(10);
        root.addView(usbA, ap);
        usbA.setOnClickListener(v -> runScan("Test: usb_a", true));
        Button deep = button("4  DEEP USB-C DISCOVERY", 0xffb64236);
        LinearLayout.LayoutParams deepLp = new LinearLayout.LayoutParams(-1, -2);
        deepLp.topMargin = dp(12);
        root.addView(deep, deepLp);
        deep.setOnClickListener(v -> runScan("Test: usb_c_deep", true));
        Button access = button("REQUEST USB ACCESS PERMISSION", 0xff776225);
        LinearLayout.LayoutParams accessLp = new LinearLayout.LayoutParams(-1, -2);
        accessLp.topMargin = dp(10);
        root.addView(access, accessLp);
        access.setOnClickListener(v -> requestUsbAccess());
        Button comparison = button("VIEW AND EXPORT COMPARISON", 0xff23736b);
        LinearLayout.LayoutParams cmp = new LinearLayout.LayoutParams(-1, -2);
        cmp.topMargin = dp(14);
        root.addView(comparison, cmp);
        comparison.setOnClickListener(v -> showComparison());

        summary = text("Waiting for scan", 17, Color.WHITE, true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(22);
        root.addView(summary, sp);

        details = text("", 15, 0xffd5e2ea, false);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
        dp.topMargin = dp(10);
        dp.bottomMargin = dp(15);
        root.addView(details, dp);

        Button export = button("EXPORT JSON REPORT", 0xff29435a);
        root.addView(export);
        export.setOnClickListener(v -> exportJson());
        Button share = button("SHARE REPORT AS TEXT", 0xff29435a);
        LinearLayout.LayoutParams sh = new LinearLayout.LayoutParams(-1, -2);
        sh.topMargin = dp(10);
        root.addView(share, sh);
        share.setOnClickListener(v -> shareReport());

        footer = text("Discovery requires no network account. USB permission is optional. Reports stay on the phone until you export or share; no undocumented vehicle commands are sent.", 12, 0xff9fb7c8, false);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(18);
        root.addView(footer, fp);
    }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    private TextView text(String content, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(3), 1.0f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setMinHeight(dp(52));
        return b;
    }

    private String timeUtc() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private String fileTimeUtc() {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private Intent usbStateIntent() {
        try {
            IntentFilter filter = new IntentFilter(USB_STATE);
            if (Build.VERSION.SDK_INT >= 33)
                return registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            return registerReceiver(null, filter);
        } catch (Exception ignored) { return null; }
    }

    private void scanStage(int percent, String stage) {
        runOnUiThread(() -> {
            if (scanProgress != null && scanActive) {
                scanProgress.setIndeterminate(false);
                scanProgress.setProgress(percent);
                scanStatus.setText(stage + " • " + percent + "%");
            }
        });
    }

    private void finishScan(long startedMs, String message) {
        long elapsed = SystemClock.elapsedRealtime() - startedMs;
        long holdMs = Math.max(0, 550 - elapsed);
        uiHandler.postDelayed(() -> {
            scanProgress.setIndeterminate(false);
            scanProgress.setProgress(100);
            scanStatus.setText(message);
            scanActive = false;
        }, holdMs);
    }

    /**
     * Android mediates permission. We ask the OS for access only when the user
     * requests it; no vendor-specific command or bytes are sent to the vehicle.
     */
    private void requestUsbAccess() {
        try {
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (manager == null) {
                scanStatus.setText("USB manager unavailable");
                return;
            }
            UsbAccessory[] accessories = manager.getAccessoryList();
            boolean hasAccessory = accessories != null && accessories.length > 0;
            UsbDevice firstDevice = null;
            if (!hasAccessory && !manager.getDeviceList().isEmpty())
                firstDevice = manager.getDeviceList().values().iterator().next();
            if (!hasAccessory && firstDevice == null) {
                scanStatus.setText("No USB accessory or host device currently visible for permission request");
                return;
            }
            Intent permission = new Intent(UsbPermissionReceiver.ACTION);
            permission.setComponent(new ComponentName(this, UsbPermissionReceiver.class));
            PendingIntent response = PendingIntent.getBroadcast(this, 431,
                    permission, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            if (hasAccessory) {
                if (manager.hasPermission(accessories[0])) {
                    scanStatus.setText("Android USB accessory permission already granted. Run Deep Scan.");
                    return;
                }
                manager.requestPermission(accessories[0], response);
            } else {
                if (manager.hasPermission(firstDevice)) {
                    scanStatus.setText("Android USB device permission already granted. Run Deep Scan.");
                    return;
                }
                manager.requestPermission(firstDevice, response);
            }
            scanStatus.setText("Android permission request submitted. Approve or deny the system prompt, then run Deep Scan.");
        } catch (Exception ex) {
            scanStatus.setText("USB permission request failed: " + ex.getClass().getSimpleName());
        }
    }

    private void runScan(String trigger, boolean save) {
        if (scanActive) {
            scanStatus.setText("A scan is already running. Results will appear shortly.");
            return;
        }
        scanActive = true;
        scanProgress.setProgress(0);
        scanProgress.setIndeterminate(true);
        scanProgress.setVisibility(View.VISIBLE);
        scanStatus.setText("Scanning " + trigger + " — checking USB connection...");
        summary.setText("New scan in progress...");
        long startedMs = SystemClock.elapsedRealtime();
        new Thread(() -> captureScan(trigger, save, startedMs), "VehiclePortScan").start();
    }

    private void captureScan(String trigger, boolean save, long startedMs) {
        try {
            JSONObject report = new JSONObject();
            report.put("report_format", "Vehicle USB Port Tester v2");
            report.put("test_label", trigger.startsWith("Test: ") ? trigger.substring(6) : "unlabeled");
            report.put("scanned_at_utc", timeUtc());
            report.put("trigger", trigger);
            report.put("app_version", "0.4.0");
            report.put("device_manufacturer", Build.MANUFACTURER);
            report.put("device_model", Build.MODEL);
            report.put("android_api_level", Build.VERSION.SDK_INT);
            report.put("android_usb_host_supported", getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST));
            report.put("android_usb_accessory_supported", getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY));
            report.put("scan_is_read_only", true);
            report.put("obd_codes_scanned", false);
            report.put("vehicle_data_confirmed", false);
            report.put("note", "USB connectivity is not proof that a vehicle exposes diagnostic data. This app cannot read OBD-II codes from a standard media/charging USB-C port.");
            scanStage(15, "Phone capabilities checked");

            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int plugged = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            String charging = plugged == BatteryManager.BATTERY_PLUGGED_USB ? "USB power detected" :
                    plugged == BatteryManager.BATTERY_PLUGGED_AC ? "AC power" :
                    plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ? "Wireless charging" :
                    plugged == 0 ? "Not charging" : "Unknown";
            report.put("power_state", charging);
            scanStage(30, "USB power checked");

            Intent state = usbStateIntent();
            JSONObject usbState = new JSONObject();
            if (state != null) {
                for (String key : new String[]{"connected","configured","host_connected","mtp","ptp","adb","rndis","midi","accessory","audio_source","ncm"}) {
                    if (state.hasExtra(key)) usbState.put(key, state.getBooleanExtra(key, false));
                }
            }
            report.put("android_usb_state", usbState);
            report.put("usb_state_broadcast_available", state != null);
            scanStage(50, "USB connection state checked");

            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            Map<String,UsbDevice> devices = manager == null ? null : manager.getDeviceList();
            JSONArray found = new JSONArray();
            if (devices != null) {
                for (UsbDevice d : devices.values()) {
                    JSONObject device = new JSONObject();
                    device.put("vendor_id", d.getVendorId());
                    device.put("product_id", d.getProductId());
                    device.put("usb_device_class", d.getDeviceClass());
                    device.put("usb_device_subclass", d.getDeviceSubclass());
                    device.put("interface_count", d.getInterfaceCount());
                    device.put("android_permission_granted", manager.hasPermission(d));
                    try { device.put("manufacturer", String.valueOf(d.getManufacturerName())); }
                    catch (Exception e) { device.put("manufacturer", "unavailable"); }
                    try { device.put("product", String.valueOf(d.getProductName())); }
                    catch (Exception e) { device.put("product", "unavailable"); }
                    JSONArray interfaces = new JSONArray();
                    for (int i = 0; i < d.getInterfaceCount(); i++) {
                        UsbInterface iface = d.getInterface(i);
                        JSONObject inf = new JSONObject();
                        inf.put("id", iface.getId());
                        inf.put("class", iface.getInterfaceClass());
                        inf.put("subclass", iface.getInterfaceSubclass());
                        inf.put("protocol", iface.getInterfaceProtocol());
                        inf.put("endpoint_count", iface.getEndpointCount());
                        interfaces.put(inf);
                    }
                    device.put("interfaces", interfaces);
                    found.put(device);
                }
            }
            report.put("usb_host_device_count", found.length());
            report.put("usb_host_devices", found);
            scanStage(75, "USB devices inspected");
            JSONArray accessibleAccessories = new JSONArray();
            try {
                UsbAccessory[] accessories = manager == null ? null : manager.getAccessoryList();
                if (accessories != null) {
                    for (UsbAccessory a : accessories) {
                        JSONObject obj = new JSONObject();
                        obj.put("manufacturer", a.getManufacturer());
                        obj.put("model", a.getModel());
                        obj.put("version", a.getVersion());
                        obj.put("description", a.getDescription());
                        obj.put("android_permission_granted", manager.hasPermission(a));
                        accessibleAccessories.put(obj);
                    }
                }
            } catch (Exception ignored) { /* Some phones restrict USB accessory enumeration. */ }
            report.put("usb_accessory_count", accessibleAccessories.length());
            report.put("usb_accessories", accessibleAccessories);
            scanStage(90, "USB accessories inspected");
            JSONObject deepReport = null;
            if ("Test: usb_c_deep".equals(trigger)) {
                scanStage(92, "Inspecting USB descriptors and interfaces");
                deepReport = DeepDiagnostics.capture(this, manager);
                android.content.SharedPreferences permissionLog =
                    getSharedPreferences("usb_permission_results", MODE_PRIVATE);
                JSONObject requestedAccess = new JSONObject();
                requestedAccess.put("previous_request_received", permissionLog.contains("last_granted"));
                requestedAccess.put("last_request_granted", permissionLog.contains("last_granted")
                    ? permissionLog.getBoolean("last_granted", false) : JSONObject.NULL);
                requestedAccess.put("last_checked_at_ms", permissionLog.getLong("last_checked_at_ms", 0L));
                deepReport.put("latest_permission_request", requestedAccess);
                report.put("deep_diagnostics", deepReport);
                scanStage(97, "Discovering phone-side car and media apps");
            }
            report.put("communication_path", usbState.optBoolean("connected", false)
                ? "Android reports USB attached; vehicle diagnostic APIs not established"
                : "Android does not currently report an attached USB data connection");
            report.put("limitations", new JSONArray()
                .put("On Android Auto or phone-peripheral connections, Android USB host enumeration can be empty even with a working data cable.")
                .put("USB power alone does not prove there is a USB data link.")
                .put("No engine, ABS, transmission, or other vehicle data was queried.")
                .put("If vehicle data is not provided to Android via a supported API or adapter, it cannot be obtained by this app.")
                .put("This is a capability test, not a vehicle health check."));

            final String reportText = report.toString(2);
            final String capturedLabel = report.optString("test_label", "unlabeled");
            String visible = "Scan time: " + report.getString("scanned_at_utc") + "\n"
                + "Phone: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Power: " + charging + "\n"
                + "USB state visible to Android: " + (state == null ? "Not reported" : usbState.toString()) + "\n"
                + "USB-host devices listed: " + found.length() + "\n"
                + "USB accessories listed: " + accessibleAccessories.length() + "\n"
                + "Vehicle information confirmed: NO\n"
                + "Engine fault codes read: NO\n\n"
                + (found.length() > 0
                    ? "Android lists USB device interfaces. Details are saved in the report; they are not automatically vehicle diagnostics."
                    : "No USB-host devices listed. This is common when the vehicle acts as USB host. It does not mean the cable is broken.")
                + "\\n\\nCapture all three labeled tests, then VIEW AND EXPORT COMPARISON."
                + (deepReport == null ? "" : "\\n\\nDEEP USB DISCOVERY:"
                    + "\\nHost devices: " + deepReport.optJSONArray("host_devices").length()
                    + "\\nAccessory descriptors: " + deepReport.optJSONArray("accessories").length()
                    + "\\nPhone apps with car/media services (visible subset): "
                    + deepReport.optJSONArray("phone_apps_with_car_or_media_services").length()
                    + "\\nFord SYNC internal apps: NOT ACCESSIBLE via standard Android USB"
                    + "\\nNo vehicle control commands sent. Full details in JSON report.");
            if (trigger.startsWith("Test: ")) {
                getPreferences(MODE_PRIVATE).edit()
                    .putString(trigger.substring(6), report.toString()).apply();
            }
            runOnUiThread(() -> {
                lastReport = reportText;
                summary.setText("Scan captured: " + capturedLabel);
                details.setText(visible);
                if (save) saveLocalReport();
                finishScan(startedMs, "Scan complete • " + timeUtc() + " • verified checks finished");
            });
        } catch (Exception ex) {
            runOnUiThread(() -> {
                summary.setText("Scan could not complete");
                details.setText("The scanner hit an Android access error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\nNo vehicle commands were sent.");
                finishScan(startedMs, "Scan finished with an error • " + timeUtc());
            });
        }
    }

    private JSONObject stored(String key) {
        String json = getPreferences(MODE_PRIVATE).getString(key, "");
        if (json.isEmpty()) return null;
        try { return new JSONObject(json); } catch (Exception ignored) { return null; }
    }

    private String describe(String label, JSONObject item) {
        if (item == null) return label + ": NOT TESTED YET";
        JSONObject state = item.optJSONObject("android_usb_state");
        if (state == null) state = new JSONObject();
        String connected = state.has("connected") ? String.valueOf(state.optBoolean("connected")) : "not reported";
        String configured = state.has("configured") ? String.valueOf(state.optBoolean("configured")) : "not reported";
        return label + " | connected=" + connected +
            ", configured=" + configured + ", power=" + item.optString("power_state", "unknown") +
            ", host devices=" + item.optInt("usb_host_device_count", 0) +
            ", accessories=" + item.optInt("usb_accessory_count", 0);
    }

    private void showComparison() {
        try {
            JSONObject unplug = stored("disconnected");
            JSONObject usbc = stored("usb_c");
            JSONObject usba = stored("usb_a");
            JSONObject combined = new JSONObject();
            combined.put("report_format", "Vehicle USB Port Tester comparison v2");
            combined.put("app_version", "0.4.0");
            combined.put("created_at_utc", timeUtc());
            combined.put("baseline_unplugged", unplug == null ? JSONObject.NULL : unplug);
            combined.put("usb_c_port", usbc == null ? JSONObject.NULL : usbc);
            combined.put("regular_usb_port", usba == null ? JSONObject.NULL : usba);
            JSONObject deep = stored("usb_c_deep");
            combined.put("deep_usb_c_port", deep == null ? JSONObject.NULL : deep);
            combined.put("vehicle_diagnostic_access_confirmed", false);
            combined.put("explanation", "USB configured indicates a link, not an OBD-II connection. Apps cannot assume Android Auto exposes engine or fault-code data.");
            lastReport = combined.toString(2);
            latestFilename = "vehicle-usb-comparison-" + fileTimeUtc() + ".json";
            int count = (unplug == null ? 0 : 1) + (usbc == null ? 0 : 1) + (usba == null ? 0 : 1);
            summary.setText("Comparison: " + count + " of 3 scans saved");
            details.setText(describe("1 UNPLUGGED", unplug) + "\n\n" +
                describe("2 USB-C", usbc) + "\n\n" +
                describe("3 REGULAR USB", usba) +
                (deep == null ? "" : "\\n\\n4 DEEP USB-C: last captured " + deep.optString("scanned_at_utc", "unknown")) +
                "\n\nEngine trouble codes: NOT SCANNED\n" +
                "Android Auto data link does not imply engine diagnostic access. " +
                "Tap EXPORT JSON REPORT to save all available results in one file.");
            saveLocalReport();
        } catch (Exception ex) {
            summary.setText("Unable to compare scans");
            details.setText(ex.getMessage());
        }
    }

    private void saveLocalReport() {
        try {
            File dir = new File(getFilesDir(), "reports");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create reports directory");
            latestFilename = "vehicle-usbc-" + fileTimeUtc() + ".json";
            File file = new File(dir, latestFilename);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(lastReport.getBytes(StandardCharsets.UTF_8));
            }
            footer.setText("Saved locally: " + latestFilename + "\nTap Export JSON Report to save a shareable copy. Reports remain private unless you share them.");
        } catch (Exception ex) {
            footer.setText("Could not save locally: " + ex.getMessage() + ". You can still share or export the report.");
        }
    }

    private void exportJson() {
        if (lastReport.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, latestFilename.isEmpty() ? "vehicle-usbc-report.json" : latestFilename);
        startActivityForResult(intent, EXPORT_JSON);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == EXPORT_JSON && result == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("No output stream");
                out.write(lastReport.getBytes(StandardCharsets.UTF_8));
                footer.setText("JSON report exported successfully. No vehicle data was modified.");
            } catch (Exception ex) {
                footer.setText("Export failed: " + ex.getMessage());
            }
        }
    }

    private void shareReport() {
        if (lastReport.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Vehicle USB-C Connection Report");
        intent.putExtra(Intent.EXTRA_TEXT, lastReport);
        startActivity(Intent.createChooser(intent, "Share connection report"));
    }
}
