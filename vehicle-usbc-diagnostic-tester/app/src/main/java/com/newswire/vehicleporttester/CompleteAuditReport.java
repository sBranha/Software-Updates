package com.newswire.vehicleporttester;
import org.json.JSONArray;
import org.json.JSONObject;

/** Passive combined audit; never claims a Ford ECU value without a verified transport. */
final class CompleteAuditReport {
    private static void add(JSONArray list,String id,String status,String source,String note){
        try{list.put(new JSONObject().put("id",id).put("status",status).put("source",source).put("note",note));}
        catch(Exception ignored){}
    }
    static JSONObject build(JSONObject report){
        JSONObject result=new JSONObject();
        JSONArray checks=new JSONArray();
        try{
            JSONObject motion=report.optJSONObject("phone_motion_lab");
            JSONObject ver=report.optJSONObject("software_versions");
            JSONObject bt=report.optJSONObject("bluetooth_audio_link_check");
            JSONArray usb=report.optJSONArray("usb_accessories_visible");
            boolean hasUsb=usb!=null&&usb.length()>0;
            int gps=motion==null?0:motion.optInt("gps_samples");
            int sensor=motion==null?0:motion.optInt("motion_samples");
            String audio=bt==null?"not_scanned":bt.optString("scan_status","not_scanned");
            add(checks,"usb_accessory_metadata",hasUsb?"DETECTED_PHONE":"NOT_DETECTED",
                "Android UsbManager","Accessory metadata only; no USB accessory was opened.");
            add(checks,"usb_host_devices",report.optInt("usb_host_devices_count")>0?"DETECTED_PHONE":"NOT_DETECTED",
                "Android UsbManager","Phone USB host list only, not vehicle ECU modules.");
            add(checks,"usb_no_takeover","PASS_BY_DESIGN","App source","No USB accessory open/read/write calls or vehicle commands.");
            add(checks,"android_auto_phone_version",
                ver!=null&&"observed_installed_phone_package".equals(ver.optString("android_auto_status"))?"DETECTED_PHONE":"NOT_VISIBLE",
                "Android PackageManager","Phone Android Auto package, not Ford firmware.");
            add(checks,"android_auto_active_projection","NOT_VERIFIED","No live projection API","USB metadata does not establish active projection.");
            add(checks,"android_auto_vehicle_hardware","NOT_INTEGRATED","No authorized Car Hardware session",
                "Vehicle hardware properties cannot be inferred by a normal phone Activity.");
            add(checks,"ford_sync_software","MANUAL_EVIDENCE_ONLY","Owner photo or typed value",
                "SYNC 3.4 build 23188_PRODUCT is owner-provided, not electronically queried.");
            add(checks,"ford_applink_registration","NOT_INTEGRATED","No Ford SDL registration/authorization",
                "AppLink is not just a USB port. Approved mobile registration is required.");
            add(checks,"ford_applink_connection","NOT_TESTED","No AppLink transport",
                "Potential separate parked test; Android Auto may need to be disabled.");
            add(checks,"ford_applink_vehicle_permissions","NOT_VERIFIED","No Ford access grants",
                "Do not claim vehicle-data access until actually negotiated.");
            add(checks,"bluetooth_hardware",report.optBoolean("bluetooth_hardware_present")?"DETECTED_PHONE":"UNAVAILABLE",
                "Android BluetoothManager","Bluetooth phone hardware only.");
            add(checks,"bluetooth_permission",report.optBoolean("bluetooth_permission_granted")?"GRANTED":"PERMISSION_REQUIRED",
                "Android runtime permission","Permission needed for current audio profile information.");
            add(checks,"bluetooth_audio",audio,"Android phone A2DP/HEADSET",
                "Phone audio link information, not an OBD diagnostic link.");
            add(checks,"phone_gps_speed",gps>0?"PHONE_DATA_OBSERVED":"NO_GPS_SAMPLES",
                "Phone GPS","Phone speed and trip, not Ford wheel speed or odometer.");
            add(checks,"phone_gps_heading",
                motion!=null&&!motion.isNull("heading_degrees")?"PHONE_DATA_OBSERVED":"NO_MOVING_HEADING",
                "Phone GPS","Requires moving GPS fix; not Ford steering angle.");
            add(checks,"phone_gps_elevation",
                motion!=null&&!motion.isNull("gps_elevation_feet")?"PHONE_DATA_OBSERVED":"NO_ALTITUDE_SAMPLE",
                "Phone GPS","Altitude accuracy depends on GPS.");
            add(checks,"phone_motion_sensor",sensor>0?"PHONE_DATA_OBSERVED":"NO_SENSOR_SAMPLES",
                "Android sensors","Acceleration is from phone, not Ford ECU.");
            add(checks,"touchscreen_safe_use",report.optString("touchscreen_user_test","not_tested"),
                "Manual owner confirmation","Screen response cannot be read electronically.");
            add(checks,"optical_tachometer",
                report.isNull("camera_optical_rpm_estimate")?"NOT_TESTED":"CAMERA_ESTIMATE_ONLY",
                "Phone camera","No engine ECU RPM measured.");
            String[] vehicle={
                "engine_rpm","vehicle_speed_pid","odometer","fuel_level","fuel_range",
                "coolant_temperature","oil_pressure_and_life","vehicle_battery_voltage",
                "tire_pressures","transmission_module","abs_module","airbag_module",
                "trouble_codes","freeze_frame","vin_from_ecu","all_ecu_modules"};
            for(String name:vehicle)
                add(checks,name,"NO_AUTHORIZED_ECU_LINK","No OBD-II or authorized AppLink data",
                    "Unavailable to this passive test. NOT proof that Ford lacks the data.");
            add(checks,"vehicle_controls","EXCLUDED_SAFETY","Read-only audit",
                "No locks, windows, throttle, brakes, steering or safety systems are actuated.");
            add(checks,"ford_factory_navigation_overlay","NO_PUBLIC_OVERLAY_ACCESS_VERIFIED",
                "SYNC factory navigation","Not the same as drawing inside NewsWire's own map.");
            add(checks,"nws_phone_warning_map","SEPARATE_PREVIEW_NOT_RUN","Phone warning overlay lab",
                "Warning polygon does not equal a predicted storm track.");
            add(checks,"newswire_android_auto_weather","SEPARATE_BUILD_REQUIRED",
                "Android Auto approved app category","Not part of the vehicle diagnostic connection.");
            JSONObject counts=new JSONObject();
            for(int i=0;i<checks.length();i++){
                String s=checks.getJSONObject(i).optString("status");
                counts.put(s,counts.optInt(s,0)+1);
            }
            result.put("report_type","COMPLETE_EXPLORER_AUDIT");
            result.put("checks_total",checks.length());
            result.put("results",checks);
            result.put("status_counts",counts);
            result.put("real_vehicle_ecu_data_verified",false);
            result.put("applink_session_verified",false);
            result.put("android_auto_car_hardware_verified",false);
            result.put("usb_accessory_opened",false);
            result.put("guidance","Run while parked. Android Auto and Ford AppLink may require separate modes. No demo measurements are included. Unknowns remain unverified, not marked broken.");
        }catch(Exception ex){
            try{result.put("error",ex.getClass().getSimpleName());}catch(Exception ignored){}
        }
        return result;
    }
}