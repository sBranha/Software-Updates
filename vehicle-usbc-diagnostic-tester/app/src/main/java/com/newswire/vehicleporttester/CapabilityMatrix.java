package com.newswire.vehicleporttester;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/** Turns observed diagnostics into an evidence-labelled capability inventory.
 * "supported" refers to phone/Android public APIs, NOT Ford SYNC capabilities.
 */
public final class CapabilityMatrix {
    private CapabilityMatrix(){}

    private static void item(JSONArray items, String category, String id,
            String title, String status, String evidence, String limit) throws Exception {
        JSONObject o=new JSONObject();
        o.put("category",category);
        o.put("id",id);
        o.put("title",title);
        o.put("status",status);
        o.put("evidence",evidence);
        o.put("limitation",limit);
        items.put(o);
    }

    private static boolean state(JSONObject o,String key){
        return o!=null&&o.optBoolean(key,false);
    }

    public static JSONObject build(Context context, JSONObject audit) {
        JSONObject result=new JSONObject();
        try{
            JSONArray entries=new JSONArray();
            JSONObject baseline=audit.optJSONObject("baseline");
            JSONObject usbState=baseline==null?null:baseline.optJSONObject("usb_state");
            boolean connected=state(usbState,"connected");
            boolean configured=state(usbState,"configured");
            int accessoryCount=baseline==null?0:baseline.optInt("accessories_count",0);
            boolean accessoryPermission=baseline!=null&&baseline.optBoolean("android_permission",false);
            boolean automotive=accessoryCount>0
                &&"Android Open Automotive Protocol".equals(
                    baseline.optString("accessory_model",""));
            JSONObject capture=audit.optJSONObject("usb_stream_and_steady_analysis");
            int bytes=capture==null?0:capture.optInt("bytes_received_total",0);
            JSONObject rate=capture==null?null:capture.optJSONObject("all_45s_steady");
            JSONObject phoneApps=audit.optJSONObject("phone_car_media_services");
            int phoneAppCount=phoneApps==null?0:phoneApps.optInt("services_count",0);
            JSONObject bluetooth=audit.optJSONObject("optional_bluetooth_obd");
            int adapters=bluetooth==null?0:bluetooth.optInt("candidate_obd_adapters",0);
            boolean bluetoothPermission=bluetooth!=null &&
                !"skipped_bluetooth_connect_permission_not_granted".equals(
                    bluetooth.optString("status",""));
            PackageManager pm=context.getPackageManager();

            item(entries,"USB & CONNECTION","usb_link",
                "Phone-to-vehicle USB connection",
                connected&&configured?"observed":"not_observed",
                connected&&configured?"Android reported a configured USB link."
                    :"A configured vehicle USB data link was not observed during this scan.",
                "USB connection alone is not access to the vehicle's internal modules.");
            item(entries,"USB & CONNECTION","android_automotive",
                "Android automotive accessory",
                automotive?"observed":"not_observed",
                automotive?"Android Open Automotive Protocol accessory detected."
                    :"No Android Open Automotive Protocol accessory was enumerated.",
                "Accessory mode does not expose SYNC app inventory or ECU diagnostics.");
            item(entries,"USB & CONNECTION","usb_permission",
                "Android USB accessory permission",
                accessoryPermission?"observed":"needs_permission",
                accessoryPermission?"Phone granted permission for the accessory."
                    :"Permission was not confirmed.",
                "Permission to read accessory bytes is not authorization to control the vehicle.");
            item(entries,"USB & CONNECTION","usb_stream",
                "Incoming structured USB traffic",
                bytes>0?"observed":"not_observed",
                bytes>0?"Received "+bytes+" bytes; steady sample "
                    +(rate==null?"unavailable":rate.optString("bytes_per_second","unknown"))
                    +" bytes/second.":"No USB bytes received or the reader could not complete.",
                "Content remains opaque and may be encrypted. No Ford payload was decoded.");

            item(entries,"SCREEN & APPS","android_auto_display",
                "Android Auto app projection",
                automotive?"supported_path_detected":"not_verified",
                automotive?"Automotive accessory transport was found.": "No active accessory transport confirmed.",
                "The scan cannot verify what is currently visible on the Explorer screen.");
            item(entries,"SCREEN & APPS","phone_car_apps",
                "Phone applications with car/media services",
                phoneAppCount>0?"observed":"not_observed",
                "Found "+phoneAppCount+" visible service declarations on the phone.",
                "Installed service declarations do not prove apps are usable or showing on SYNC.");
            item(entries,"SCREEN & APPS","navigation_gps",
                "Phone GPS / navigation hardware",
                pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
                    ?"phone_supported":"not_observed",
                pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
                    ?"Phone advertises GPS location hardware."
                    :"Phone GPS hardware was not reported.",
                "No location data was read; Android Auto navigation follows supported app policies.");
            item(entries,"SCREEN & APPS","media_audio",
                "Music, calls and audio via Android Auto",
                phoneAppCount>0?"supported_path_detected":"not_verified",
                "Car/media service availability was checked on the phone.",
                "The scan does not test media playback, screen mirroring, or video on the vehicle.");
            item(entries,"SCREEN & APPS","native_sync_apps",
                "Ford SYNC internal apps, firmware and layout",
                "not_accessible_from_this_connection",
                "Android public USB APIs do not provide a Ford SYNC application inventory.",
                "Requires a documented Ford-authorized interface or supported head-unit integration.");
            item(entries,"SCREEN & APPS","screen_video",
                "Unrestricted vehicle display mirroring/video",
                "not_verified",
                "No unrestricted display-capture or video protocol was detected.",
                "Android Auto restricts app classes and video use for driver safety.");

            item(entries,"VEHICLE SENSORS","obd_adapter",
                "Paired external OBD-II interface",
                adapters>0?"adapter_candidate":"not_verified",
                adapters>0?"Found "+adapters+" Bluetooth device name(s) resembling an OBD adapter."
                    :(bluetoothPermission?"No known adapter names found in paired devices."
                      :"Bluetooth connect permission not granted; adapter discovery was skipped."),
                "Bluetooth device names do not confirm a working OBD-II connection.");
            item(entries,"VEHICLE SENSORS","engine_telemetry",
                "RPM, speed, coolant, fuel and diagnostic codes",
                "requires_obd_adapter",
                "No verified vehicle diagnostic connection or OBD query was made.",
                "Requires a compatible adapter and separately authorized read-only diagnostics.");
            item(entries,"VEHICLE SENSORS","vehicle_modules",
                "ABS, transmission, body modules and tire sensors",
                "not_verified",
                "No vehicle control-module diagnostic protocol was established.",
                "Availability depends on model-specific modules, permissions and adapter support.");

            item(entries,"CONTROLS & SETTINGS","climate_and_locks",
                "Climate, remote start, locks, lights and seats",
                "not_verified",
                "No Ford-authorized control API or compatible secure adapter was verified.",
                "This scanner does not send vehicle-control requests or bypass security.");
            item(entries,"CONTROLS & SETTINGS","steering_controls",
                "Steering wheel, dashboard and instrument cluster",
                "not_verified",
                "No Ford dashboard/steering-control channel was identified.",
                "Android Auto does not provide arbitrary instrument-cluster control.");
            item(entries,"CONTROLS & SETTINGS","connected_services",
                "Ford cloud/connected vehicle features",
                "not_tested",
                "This scan does not connect to a Ford account or remote API.",
                "Remote features require official support, authorization and compatible service.");
            item(entries,"CONTROLS & SETTINGS","safe_write_access",
                "Write commands and ECU reprogramming",
                "not_tested_safety",
                "The entire audit was read-only and sent zero commands.",
                "Control writes and programming are intentionally excluded.");

            int observed=0,supported=0,need=0,unverified=0;
            for(int i=0;i<entries.length();i++){
                String status=entries.getJSONObject(i).optString("status","");
                if("observed".equals(status))observed++;
                else if(status.startsWith("supported")||"phone_supported".equals(status))supported++;
                else if(status.startsWith("requires")||"needs_permission".equals(status))need++;
                else unverified++;
            }
            result.put("format","Vehicle Capability Explorer evidence matrix v1");
            result.put("generated_version","0.8.2");
            result.put("vehicle","2021 Ford Explorer Limited (user-reported; not electronically identified)");
            result.put("phone_model",Build.MANUFACTURER+" "+Build.MODEL);
            result.put("status_meaning",new JSONObject()
                .put("observed","Directly measured by Android during this scan")
                .put("supported_path_detected","Possible Android capability path, NOT proof of active Ford feature")
                .put("phone_supported","Feature advertised on the PHONE only")
                .put("adapter_candidate","Device name matches an adapter keyword, NOT tested")
                .put("requires_obd_adapter","A separate supported diagnostic interface is needed")
                .put("not_verified","No functional access established"));
            result.put("observed_count",observed);
            result.put("supported_path_count",supported);
            result.put("needs_adapter_or_permission_count",need);
            result.put("unverified_or_unavailable_count",unverified);
            result.put("capabilities",entries);
            result.put("recommendation","For actual vehicle sensor readings, pair a compatible OBD-II adapter. For screen functions use documented Android Auto app APIs. Ford SYNC internals and safety-critical controls remain out of scope.");
        }catch(Exception ex){
            try{result.put("error",ex.getClass().getSimpleName());}
            catch(Exception ignored){}
        }
        return result;
    }
}
