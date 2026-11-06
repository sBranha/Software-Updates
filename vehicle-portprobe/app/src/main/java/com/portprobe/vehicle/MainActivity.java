package com.portprobe.vehicle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.graphics.Color;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final String USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final String AUTHORITY = "com.portprobe.vehicle.reports";
    private TextView results;
    private EditText vehicle;
    private File latestFile;
    private String latestJson;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(24), dp(18), dp(30));
        page.setBackgroundColor(Color.rgb(249, 250, 252));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(page);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("PortProbe");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(23, 42, 70));
        title.setTypeface(null, 1);
        page.addView(title);

        TextView intro = text("Vehicle USB-C connection tester · v0.1\nRead-only. No engine commands. No internet or account required.", 14);
        page.addView(intro);
        gap(page, 18);
        page.addView(text("Vehicle (editable for other brands)", 14));
        vehicle = new EditText(this);
        vehicle.setSingleLine(true);
        vehicle.setText(getPreferences(MODE_PRIVATE).getString("vehicle", "2021 Ford Explorer"));
        vehicle.setTextSize(16);
        page.addView(vehicle);

        Button scan = button("Scan USB-C connection");
        scan.setOnClickListener(v -> scan());
        page.addView(scan);
        Button share = button("Share latest JSON report");
        share.setOnClickListener(v -> { if (latestFile != null) share(latestFile); else toast("Run a scan first."); });
        page.addView(share);
        Button history = button("Saved reports");
        history.setOnClickListener(v -> showHistory());
        page.addView(history);
        Button copy = button("Copy latest report");
        copy.setOnClickListener(v -> copy());
        page.addView(copy);
        gap(page, 14);

        results = text("Ready to scan.", 15);
        results.setTextIsSelectable(true);
        results.setPadding(dp(12), dp(14), dp(12), dp(14));
        results.setBackgroundColor(Color.WHITE);
        page.addView(results);
        gap(page, 16);
        page.addView(text("Important: This tests what Android can see through the connection. It does not read check-engine trouble codes and cannot confirm the car has no problems. Many car USB-C ports expose charging or Android Auto only.", 13));
        scan();
    }

    private void scan() {
        try {
            String selectedVehicle = vehicle.getText().toString().trim();
            getPreferences(MODE_PRIVATE).edit().putString("vehicle", selectedVehicle).apply();
            JSONObject report = new JSONObject();
            report.put("report_format", "PortProbe/1");
            report.put("app_version", "0.1.0");
            report.put("timestamp_utc", utcNow());
            report.put("read_only", true);
            report.put("vehicle_label_user_supplied", selectedVehicle);
            JSONObject phone = new JSONObject();
            phone.put("manufacturer", Build.MANUFACTURER);
            phone.put("model", Build.MODEL);
            phone.put("android_sdk", Build.VERSION.SDK_INT);
            report.put("phone", phone);

            StringBuilder display = new StringBuilder();
            display.append("USB-C CONNECTION SCAN\n");
            display.append("Vehicle: ").append(selectedVehicle.isEmpty() ? "Not specified" : selectedVehicle).append("\n");
            display.append("Time: ").append(new Date()).append("\n\n");

            JSONObject state = new JSONObject();
            Intent sticky = null;
            try { sticky = registerReceiver(null, new IntentFilter(USB_STATE)); }
            catch (Exception ignored) { /* This system broadcast is not available on all devices. */ }
            if (sticky == null) {
                state.put("status", "not_exposed_to_app");
                display.append("USB state: Android did not expose a connection-state broadcast.\n");
            } else {
                state.put("status", "broadcast_received");
                String[] keys = {"connected", "configured", "host_connected", "mtp", "ptp", "accessory", "midi", "rndis", "adb"};
                display.append("USB state reported by Android:\n");
                for (String key : keys) {
                    if (sticky.hasExtra(key)) {
                        boolean yes = sticky.getBooleanExtra(key, false);
                        state.put(key, yes);
                        display.append("  ").append(key).append(": ").append(yes ? "yes" : "no").append("\n");
                    }
                }
            }
            report.put("android_usb_state", state);

            UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
            JSONArray devices = new JSONArray();
            JSONArray accessories = new JSONArray();
            if (usb == null) {
                display.append("\nUSB manager unavailable.\n");
            } else {
                HashMap<String, UsbDevice> hostDevices = usb.getDeviceList();
                if (hostDevices != null) {
                    for (UsbDevice dev : hostDevices.values()) {
                        JSONObject device = new JSONObject();
                        device.put("vendor_id", dev.getVendorId());
                        device.put("product_id", dev.getProductId());
                        device.put("device_class", dev.getDeviceClass());
                        device.put("device_subclass", dev.getDeviceSubclass());
                        device.put("device_protocol", dev.getDeviceProtocol());
                        device.put("manufacturer", safe(dev.getManufacturerName()));
                        device.put("product", safe(dev.getProductName()));
                        device.put("interface_count", dev.getInterfaceCount());
                        JSONArray interfaces = new JSONArray();
                        for (int i = 0; i < dev.getInterfaceCount(); i++) {
                            UsbInterface ui = dev.getInterface(i);
                            JSONObject intf = new JSONObject();
                            intf.put("interface_id", ui.getId());
                            intf.put("class", ui.getInterfaceClass());
                            intf.put("subclass", ui.getInterfaceSubclass());
                            intf.put("protocol", ui.getInterfaceProtocol());
                            JSONArray eps = new JSONArray();
                            for (int j = 0; j < ui.getEndpointCount(); j++) {
                                UsbEndpoint ep = ui.getEndpoint(j);
                                JSONObject endpoint = new JSONObject();
                                endpoint.put("address", ep.getAddress());
                                endpoint.put("direction", ep.getDirection() == 128 ? "IN" : "OUT");
                                endpoint.put("type", ep.getType());
                                eps.put(endpoint);
                            }
                            intf.put("endpoints", eps);
                            interfaces.put(intf);
                        }
                        device.put("interfaces", interfaces);
                        devices.put(device);
                        display.append("\nUSB host device found:\n");
                        display.append("  Vendor/Product IDs: ").append(dev.getVendorId()).append(" / ").append(dev.getProductId()).append("\n");
                        display.append("  Name: ").append(safe(dev.getManufacturerName())).append(" ").append(safe(dev.getProductName())).append("\n");
                        display.append("  Interfaces: ").append(dev.getInterfaceCount()).append("\n");
                    }
                }
                UsbAccessory[] list = usb.getAccessoryList();
                if (list != null) {
                    for (UsbAccessory accessory : list) {
                        JSONObject obj = new JSONObject();
                        obj.put("manufacturer", safe(accessory.getManufacturer()));
                        obj.put("model", safe(accessory.getModel()));
                        obj.put("description", safe(accessory.getDescription()));
                        accessories.put(obj);
                        display.append("\nAndroid USB accessory found:\n");
                        display.append("  ").append(safe(accessory.getManufacturer())).append(" / ").append(safe(accessory.getModel())).append("\n");
                    }
                }
            }
            report.put("usb_host_devices", devices);
            report.put("usb_accessories", accessories);
            if (devices.length() == 0 && accessories.length() == 0) {
                display.append("\nNo USB host devices or Android Open Accessories visible to this app.\n");
                display.append("This is common when a phone is plugged into a vehicle USB port in device/peripheral mode.\n");
            }
            JSONObject diagnosis = new JSONObject();
            diagnosis.put("trouble_codes", "not_tested");
            diagnosis.put("engine_ecu_data", "not_tested");
            diagnosis.put("reason", "A built-in vehicle USB-C media/charging connection does not provide a standard, general-purpose OBD-II diagnostic interface to a phone app.");
            report.put("vehicle_diagnostics", diagnosis);
            report.put("interpretation", "USB enumeration alone does not establish that vehicle sensor data or diagnostic trouble codes can be read. An absent USB device is not a finding that the vehicle is healthy.");
            report.put("network_access_used", false);
            report.put("commands_sent_to_vehicle", false);

            display.append("\nVEHICLE DIAGNOSTIC CODES: NOT TESTED\n");
            display.append("This scan does not communicate with the ECU. Missing information is not a clean bill of health.\n");
            latestJson = report.toString(2);
            latestFile = store(latestJson);
            display.append("\nReport saved on this phone:\n").append(latestFile.getName()).append("\n");
            results.setText(display.toString());
        } catch (Exception e) {
            results.setText("Scan couldn't finish: " + e.getClass().getSimpleName() + "\n" + safe(e.getMessage()));
        }
    }

    private File store(String data) throws IOException {
        File directory = new File(getFilesDir(), "reports");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create reports directory");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        File report = new File(directory, "PortProbe_" + stamp + ".json");
        try (FileOutputStream out = new FileOutputStream(report)) {
            out.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return report;
    }

    private void share(File file) {
        Uri uri = Uri.parse("content://" + AUTHORITY + "/" + Uri.encode(file.getName()));
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/json");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newUri(getContentResolver(), "PortProbe report", uri));
        try { startActivity(Intent.createChooser(send, "Share PortProbe report")); }
        catch (Exception ex) { toast("No app available to share this report."); }
    }

    private void showHistory() {
        File folder = new File(getFilesDir(), "reports");
        File[] files = folder.listFiles((dir, name) -> name.startsWith("PortProbe_") && name.endsWith(".json"));
        if (files == null || files.length == 0) { toast("No saved reports yet."); return; }
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        String[] titles = new String[files.length];
        for (int i = 0; i < files.length; i++) titles[i] = files[i].getName();
        new AlertDialog.Builder(this).setTitle("Saved scan reports").setItems(titles, (dialog, which) -> {
            latestFile = files[which];
            latestJson = read(files[which]);
            share(latestFile);
        }).setNegativeButton("Close", null).show();
    }

    private String read(File file) {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) { return ""; }
    }

    private void copy() {
        if (latestJson == null || latestJson.isEmpty()) { toast("Run a scan first."); return; }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("PortProbe report", latestJson));
            toast("Report copied.");
        }
    }

    private String utcNow() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }
    private static String safe(String s) { return s == null ? "" : s; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, int size) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(size);
        tv.setTextColor(Color.rgb(45, 59, 74));
        tv.setLineSpacing(dp(3), 1.0f);
        return tv;
    }
    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }
    private void gap(LinearLayout page, int h) {
        View space = new View(this);
        page.addView(space, new LinearLayout.LayoutParams(1, dp(h)));
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
