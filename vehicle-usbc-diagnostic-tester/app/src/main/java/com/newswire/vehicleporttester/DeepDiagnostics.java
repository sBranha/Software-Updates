package com.newswire.vehicleporttester;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User-initiated, non-destructive capability discovery. Never sends undocumented
 * Ford, OBD, Android Auto, or USB vendor commands.
 */
public final class DeepDiagnostics {
    private DeepDiagnostics() { }

    private static String hexPreview(byte[] raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(raw.length, 128); i++) {
            if (i != 0) sb.append(' ');
            sb.append(String.format(java.util.Locale.US, "%02X", raw[i] & 0xff));
        }
        return sb.toString();
    }

    private static JSONArray capabilities(Context context) throws JSONException {
        JSONArray result = new JSONArray();
        PackageManager pm = context.getPackageManager();
        for (String spec : new String[] {
                "androidx.car.app.CarAppService|Android for Cars App Library",
                "android.media.browse.MediaBrowserService|Android Auto media browse service",
                "androidx.media3.session.MediaLibraryService|Media3 library service"
        }) {
            String[] parts = spec.split("\\|", 2);
            Intent intent = new Intent(parts[0]);
            List<ResolveInfo> candidates;
            try {
                candidates = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
            } catch (Exception ex) {
                candidates = java.util.Collections.emptyList();
            }
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : candidates) {
                ServiceInfo service = ri.serviceInfo;
                if (service == null || service.packageName == null || !seen.add(service.packageName + "/" + service.name)) continue;
                JSONObject item = new JSONObject();
                item.put("installed_on", "phone");
                item.put("package_name", service.packageName);
                item.put("service_name", service.name);
                item.put("service_type", parts[1]);
                item.put("app_label", String.valueOf(service.loadLabel(pm)));
                item.put("running_now", JSONObject.NULL);
                item.put("visible_on_vehicle", JSONObject.NULL);
                result.put(item);
            }
        }
        return result;
    }

    public static JSONObject capture(Context context, UsbManager manager) throws JSONException {
        JSONObject deep = new JSONObject();
        deep.put("mode", "deep_capability_discovery_read_only");
        deep.put("vehicle", "2021 Ford Explorer (user supplied; not electronically identified)");
        deep.put("android_api_level", Build.VERSION.SDK_INT);
        deep.put("android_auto_session_observed", false);
        deep.put("android_auto_session_observed_note", "Standard Android USB APIs do not disclose the currently running Android Auto applications or SYNC app inventory.");
        deep.put("vehicle_internal_apps_observed", false);
        deep.put("vehicle_firmware_observed", false);
        deep.put("obd_diagnostic_protocol_observed", false);
        deep.put("probe_policy", "Enumerate public Android USB descriptors, inspect phone-side application service declarations, and report permissions; no undocumented vehicle commands.");
        JSONArray devices = new JSONArray();
        Map<String, UsbDevice> attached = manager == null ? null : manager.getDeviceList();
        if (attached != null) {
            for (UsbDevice device : attached.values()) {
                JSONObject d = new JSONObject();
                d.put("vendor_id", device.getVendorId());
                d.put("product_id", device.getProductId());
                d.put("device_id", device.getDeviceId());
                d.put("device_class", device.getDeviceClass());
                d.put("device_subclass", device.getDeviceSubclass());
                d.put("device_protocol", device.getDeviceProtocol());
                d.put("android_permission_granted", manager.hasPermission(device));
                d.put("device_name", device.getDeviceName());
                if (Build.VERSION.SDK_INT >= 23) {
                    try { d.put("manufacturer", device.getManufacturerName()); } catch (Exception ignored) {}
                    try { d.put("product_name", device.getProductName()); } catch (Exception ignored) {}
                    try { d.put("usb_version", device.getVersion()); } catch (Exception ignored) {}
                    if (manager.hasPermission(device)) {
                        try { d.put("serial", device.getSerialNumber()); } catch (Exception ignored) {}
                    }
                }
                JSONArray ifaces = new JSONArray();
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    UsbInterface usbInterface = device.getInterface(i);
                    JSONObject iface = new JSONObject();
                    iface.put("id", usbInterface.getId());
                    iface.put("alternate_setting", usbInterface.getAlternateSetting());
                    iface.put("class", usbInterface.getInterfaceClass());
                    iface.put("subclass", usbInterface.getInterfaceSubclass());
                    iface.put("protocol", usbInterface.getInterfaceProtocol());
                    JSONArray endpoints = new JSONArray();
                    for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                        UsbEndpoint endpoint = usbInterface.getEndpoint(j);
                        JSONObject e = new JSONObject();
                        e.put("address", endpoint.getAddress());
                        e.put("direction", endpoint.getDirection() == 128 ? "in" : "out");
                        e.put("type", endpoint.getType());
                        e.put("max_packet_size", endpoint.getMaxPacketSize());
                        e.put("interval", endpoint.getInterval());
                        endpoints.put(e);
                    }
                    iface.put("endpoints", endpoints);
                    ifaces.put(iface);
                }
                d.put("interfaces", ifaces);
                if (manager.hasPermission(device)) {
                    UsbDeviceConnection connection = null;
                    try {
                        connection = manager.openDevice(device);
                        if (connection != null) {
                            byte[] raw = connection.getRawDescriptors();
                            d.put("raw_descriptor_length", raw == null ? 0 : raw.length);
                            d.put("raw_descriptor_preview_hex", hexPreview(raw));
                        }
                    } catch (SecurityException denied) {
                        d.put("raw_descriptor_access", "permission_denied");
                    } finally {
                        if (connection != null) connection.close();
                    }
                } else {
                    d.put("raw_descriptor_access", "requires_android_usb_permission");
                }
                devices.put(d);
            }
        }
        deep.put("host_devices", devices);
        JSONArray accessories = new JSONArray();
        if (manager != null) {
            try {
                UsbAccessory[] found = manager.getAccessoryList();
                if (found != null) {
                    for (UsbAccessory accessory : found) {
                        JSONObject a = new JSONObject();
                        a.put("manufacturer", accessory.getManufacturer());
                        a.put("model", accessory.getModel());
                        a.put("description", accessory.getDescription());
                        a.put("version", accessory.getVersion());
                        a.put("uri", accessory.getUri());
                        a.put("permission_granted", manager.hasPermission(accessory));
                        a.put("data_messages_read", 0);
                        a.put("vehicle_commands_sent", 0);
                        a.put("accessory_opened", false);
                        a.put("reason_not_opened", "No publicly documented general-purpose SYNC inventory protocol; taking over an Android Auto accessory could interrupt the session.");
                        accessories.put(a);
                    }
                }
            } catch (Exception ex) {
                deep.put("accessory_enumeration_error", ex.getClass().getSimpleName());
            }
        }
        deep.put("accessories", accessories);
        deep.put("phone_apps_with_car_or_media_services", capabilities(context));
        deep.put("phone_app_visibility_limit", "Only apps visible through the declared car/media service queries are listed, not a complete phone inventory or an in-car app inventory.");
        deep.put("permissions", new JSONObject()
                .put("accessory_request_available", accessories.length() > 0)
                .put("usb_host_request_available", devices.length() > 0)
                .put("permission_result_note", "Use REQUEST USB ACCESS to ask Android permission; re-run Deep Scan after approving."));
        deep.put("not_accessible_via_standard_usb_apis", new JSONArray()
                .put("Installed applications inside Ford SYNC")
                .put("Apps currently displayed by Android Auto")
                .put("Engine/ABS/transmission diagnostic data")
                .put("Ford SYNC firmware and module inventories unless separately exposed"));
        return deep;
    }
}
