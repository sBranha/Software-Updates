package com.newswire.vehicleporttester;

import android.content.Context;
import android.content.pm.PackageManager;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Evidence-labelled inventory, NOT a hidden Ford module scan.
 * No ECU, CAN, SYNC, gateway or control command is ever transmitted.
 * It enumerates candidates for future adapter/official API discovery.
 */
public final class VehicleSystemInventory {
    private VehicleSystemInventory(){}

    private static final String[][] CATALOG = {
        {"Engine & Powertrain","engine_rpm|Vehicle RPM,engine_speed|Vehicle speed,engine_coolant|Engine coolant temperature,engine_oil_temp|Engine oil temperature,engine_oil_pressure|Engine oil pressure,engine_load|Calculated engine load,engine_throttle|Throttle position,engine_accelerator|Accelerator pedal position,engine_intake_air|Intake air temperature,engine_intake_pressure|Manifold absolute pressure,engine_air_flow|Mass airflow,engine_fuel_level|Fuel level,engine_fuel_pressure|Fuel pressure,engine_fuel_trim_short|Short-term fuel trim,engine_fuel_trim_long|Long-term fuel trim,engine_oxygen_sensor|Oxygen sensor readings,engine_catalyst|Catalyst temperature and status,engine_emissions|Emissions readiness monitors,engine_misfire|Misfire counters,engine_dtc|Engine diagnostic trouble codes,engine_freeze|Freeze-frame diagnostic records,engine_vin|Vehicle identification number,engine_ecu_identity|Engine ECU identification,engine_idle|Engine idle request,engine_start_stop|Automatic start/stop settings,engine_fan|Cooling fan command"},
        {"Transmission & Drivetrain","trans_gear|Selected transmission gear,trans_fluid_temp|Transmission fluid temperature,trans_line_pressure|Transmission line pressure,trans_slip|Torque converter slip,trans_dtc|Transmission trouble codes,trans_shift_mode|Transmission driving mode,trans_tcm|Transmission control module details,drivetrain_ptu|Power transfer unit state,drivetrain_awd|All-wheel-drive status,drivetrain_diff|Differential or axle module status,drivetrain_traction|Traction control status"},
        {"Electrical & Charging","electric_battery|12V battery voltage,electric_soc|12V battery state of charge,electric_alternator|Charging system voltage,electric_current|Battery or charging current,electric_ignition|Ignition position,electric_key|Keyless ignition presence,electric_start|Remote start state,electric_sleep|Module sleep and wake status,electric_power|Accessory power status,electric_dtc|Electrical system fault codes"},
        {"Body & Access","body_driver_door|Driver door open state,body_passenger_door|Passenger door open state,body_rear_doors|Rear doors open states,body_tailgate|Liftgate open state,body_hood|Hood open state,body_lock_state|Door-lock state,body_lock_control|Lock and unlock request,body_window_state|Windows positions,body_window_control|Window open and close,body_sunroof|Sunroof state and commands,body_mirror_fold|Mirror-fold state and control,body_mirror_adjust|Mirror adjustment,body_keypad|Keypad and keyless-entry state,body_alarm|Anti-theft alarm state,body_horn|Horn command,body_factory_module|Body control module identity"},
        {"Lighting & Wipers","lights_headlight|Headlamp status and mode,lights_highbeam|High-beam status,lights_auto|Automatic lighting mode,lights_drl|Daytime running light status,lights_fog|Fog lamp status,lights_turn|Turn signals,lights_hazard|Hazard lights,lights_reverse|Reverse lamps,lights_brake|Brake lamp indication,lights_interior|Interior and ambient lighting,lights_brightness|Gauge illumination brightness,wipers_front|Front windshield wiper state,wipers_rear|Rear windshield wiper state,wipers_washer|Washer pump status"},
        {"Climate & Cabin","climate_temp_driver|Driver selected temperature,climate_temp_passenger|Passenger selected temperature,climate_fan|Climate fan speed,climate_mode|Climate airflow mode,climate_ac|Air conditioning compressor setting,climate_recirc|Recirculation state,climate_defrost|Defrost mode,climate_rear|Rear climate zone control,climate_heated_seats|Heated seat setting,climate_vent_seats|Ventilated seat setting,climate_heated_wheel|Heated steering wheel,climate_auto|Automatic climate mode,climate_cabin_temp|Cabin temperature sensor,climate_outside_temp|Outside temperature"},
        {"Seats & Comfort","seat_driver_memory|Driver seat memory preset,seat_passenger_memory|Passenger seat memory,seat_driver_position|Driver seat position,seat_passenger_position|Passenger seat position,seat_lumbar|Lumbar support control,seat_fold|Rear seat fold functions,seat_massage|Seat massage functions (if equipped),comfort_remote_start|Remote start cabin preparation,comfort_mirror_heat|Heated mirror state"},
        {"Brakes & Safety","safety_abs|ABS module and fault codes,safety_traction|Traction-control system,safety_esc|Electronic stability control,safety_brake_pressure|Brake pedal or pressure status,safety_parking_brake|Electric parking brake state,safety_airbag|Airbag and restraint module status,safety_seatbelt|Seat belt warnings,safety_occupancy|Occupant detection,safety_pretensioner|Pretensioner diagnostics,safety_collision|Pre-collision assist status,safety_aeb|Automatic emergency braking,safety_adaptive_cruise|Adaptive cruise control,safety_lane_assist|Lane-keeping assistance,safety_blind_spot|Blind-spot monitoring,safety_cross_traffic|Cross-traffic alerts,safety_speed_limiter|Speed limiting functions,safety_steering_assist|Power steering assistance"},
        {"Parking & Cameras","park_rear_sensors|Rear parking sensor data,park_front_sensors|Front parking sensor data,park_rear_camera|Rear-view camera feed,park_360_camera|360-degree cameras (if equipped),park_front_camera|Front camera,park_active_assist|Active parking assist,park_guidelines|Parking guidance lines,park_trailer_camera|Trailer and hitch camera feeds,park_reverse_detect|Reverse gear display trigger,park_camera_switch|Camera switching controls"},
        {"Wheels & Chassis","chassis_tire_pressure|Individual tire pressure,chassis_tire_temperature|Tire temperatures (if available),chassis_tpms|Tire pressure monitoring alerts,chassis_odometer|Odometer mileage,chassis_trip|Trip meters,chassis_suspension|Suspension control (if equipped),chassis_ride_height|Ride height data,chassis_steering_angle|Steering wheel angle,chassis_yaw|Yaw rate,chassis_long_accel|Longitudinal acceleration,chassis_lat_accel|Lateral acceleration,chassis_wheel_speed|Individual wheel speeds"},
        {"Ford SYNC & Displays","sync_version|SYNC system firmware version,sync_module|Infotainment module identification,sync_app_inventory|SYNC-native installed applications,sync_navigation|Built-in navigation and map functions,sync_map_version|Navigation map version,sync_media|SYNC media source and playback,sync_radio|Radio source and station control,sync_voice|Voice-control functions,sync_touch|Touchscreen input events,sync_video|Custom video on center display,sync_custom_text|Custom on-screen text and widgets,sync_camera_view|Factory camera overlay control,sync_cluster|Digital instrument-cluster content,sync_dash_controls|Steering-wheel media controls,sync_hud|Head-up display content (if equipped),sync_update|SYNC system update capabilities"},
        {"Android Auto & Phone Apps","aa_connected|Android automotive accessory USB state,aa_traffic|Incoming accessory communication,aa_apps|Phone apps with Android Auto services,aa_music|Music and media apps,aa_calls|Phone calls and contacts,aa_messages|Messaging apps,aa_maps|Navigation applications,aa_weather|Weather and radar navigation apps,aa_audio|Phone-to-car audio path,aa_microphone|Microphone and voice-input integration,aa_app_templates|Approved Android Auto app templates,aa_notifications|Android Auto notifications,aa_screen_state|Currently displayed vehicle application,aa_screen_capture|Vehicle-screen content capture,aa_video|Video playback via approved car interfaces"},
        {"Connectivity & Remote","conn_usb_c|USB-C data/power connection,conn_usb_a|USB-A data/power connection,conn_usb_accessory|Android accessory permissions,conn_bluetooth|Paired Bluetooth devices,conn_bluetooth_obd|Paired Bluetooth OBD adapter,conn_phone_gps|Phone GPS hardware,conn_wifi|Wi-Fi access,conn_hotspot|In-vehicle Wi-Fi hotspot,conn_cellular|Cellular modem status,conn_ford_services|Official Ford connected services,conn_remote_lock|Remote lock and unlock,conn_remote_start|Remote start and shutdown,conn_remote_location|Ford account vehicle location,conn_ota|Over-the-air updates"}
    };

    private static void record(JSONArray items,String system,String id,String name,String read,
            String control,String evidence,String accessPath,String needed,boolean safety) throws Exception{
        JSONObject obj=new JSONObject();
        obj.put("system",system);
        obj.put("id",id);
        obj.put("name",name);
        obj.put("read_access",read);
        obj.put("control_access",control);
        obj.put("evidence",evidence);
        obj.put("probable_access_path",accessPath);
        obj.put("next_requirement",needed);
        obj.put("safety_critical",safety);
        obj.put("source_kind",read.equals("read_verified")
            ||read.equals("interface_observed")
            ||read.equals("phone_service_declarations_observed")
            ||read.equals("phone_hardware_observed")
            ||read.equals("blocked_usb_permission")
            ? "measured_android_api" : "candidate_not_vehicle_tested");
        items.put(obj);
    }

    public static JSONObject create(Context ctx,JSONObject report){
        JSONObject result=new JSONObject();
        JSONArray items=new JSONArray();
        try{
            JSONObject baseline=report.optJSONObject("baseline");
            boolean accessory=baseline!=null&&baseline.optInt("accessories_count",0)>0;
            boolean hasPermission=baseline!=null&&baseline.optBoolean("android_permission",false);
            JSONObject traffic=report.optJSONObject("usb_stream_and_steady_analysis");
            boolean bytes=traffic!=null&&traffic.optInt("bytes_received_total",0)>0;
            JSONObject bt=report.optJSONObject("optional_bluetooth_obd");
            boolean candidate=bt!=null&&bt.optInt("candidate_obd_adapters",0)>0;
            int appServices=report.optJSONObject("phone_car_media_services")==null?0:
                report.optJSONObject("phone_car_media_services").optInt("services_count",0);
            int count=0;
            for(String[] group:CATALOG){
                String system=group[0];
                String[] columns=group[1].split(",");
                for(String entry:columns){
                    String[] pair=entry.split("\\|",2);
                    if(pair.length!=2)continue;
                    String id=pair[0],name=pair[1];
                    boolean safety=system.equals("Brakes & Safety")
                        ||id.equals("body_horn")||id.equals("trans_shift_mode")
                        ||id.equals("engine_start_stop")||id.equals("engine_idle");
                    String read="not_tested_no_vehicle_api";
                    String control="not_tested_no_control_commands";
                    String evidence="The public phone USB interface has not exposed this Ford function.";
                    String route="Ford-compatible diagnostic interface or official Ford API";
                    String needed="Identify corresponding vehicle module and its supported read-only interface.";
                    if(id.equals("aa_connected")||id.equals("conn_usb_c")
                        ||id.equals("conn_usb_accessory")){
                        read=accessory?(hasPermission?"interface_observed":"blocked_usb_permission"):
                            "not_observed";
                        evidence=accessory?
                            "Android enumerated automotive accessory; permission is "+hasPermission+".":
                            "No Android automotive accessory detected.";
                        route="Android USB accessory API";
                        needed=hasPermission?"No extra permission for accessory enumeration":
                            "Approve Android USB permission prompt.";
                    }else if(id.equals("aa_traffic")){
                        read=bytes?"read_verified":(hasPermission?"not_observed":"blocked_usb_permission");
                        evidence=bytes?"USB bytes were received; meaning is not decrypted.":
                            "No bytes collected by this scan.";
                        route="Android USB accessory read";
                        needed=hasPermission?"Decode only documented interface messages":
                            "Approve USB permission prompt and repeat scan.";
                    }else if(id.equals("aa_apps")||id.equals("aa_app_templates")){
                        read=appServices>0?"phone_service_declarations_observed":"not_observed";
                        evidence=appServices+" phone-side car/media service declarations detected.";
                        route="Android PackageManager car app services";
                        needed="Test individual app behavior on the car display under Android Auto policy.";
                    }else if(id.equals("conn_bluetooth_obd")){
                        read=candidate?"adapter_name_candidate":"not_verified";
                        evidence=candidate?"A paired Bluetooth device name resembles an OBD adapter.":
                            "No verified paired OBD-II adapter recorded.";
                        route="Bluetooth + compatible diagnostic OBD interface";
                        needed="Pair and verify supported read-only adapter transport.";
                    }else if(id.equals("conn_phone_gps")){
                        boolean gps=ctx.getPackageManager().hasSystemFeature(
                            PackageManager.FEATURE_LOCATION_GPS);
                        read=gps?"phone_hardware_observed":"not_observed";
                        evidence="Phone GPS hardware advertised: "+gps+".";
                        route="Android location APIs (phone only)";
                        needed="Obtain phone location permission only if navigation testing is desired.";
                    }else if(system.equals("Engine & Powertrain")
                        ||system.equals("Transmission & Drivetrain")
                        ||system.equals("Electrical & Charging")
                        ||system.equals("Wheels & Chassis")){
                        route="Compatible OBD-II/CAN diagnostic device; Ford enhanced PIDs where applicable";
                        needed=candidate?"Verify candidate adapter before querying any diagnostic identifiers":
                            "Connect a compatible OBD-II adapter for read-only ECU identification.";
                    }else if(system.equals("Ford SYNC & Displays")){
                        route="Documented Android Auto app APIs or authorized Ford SYNC interface";
                        needed="Identify public SYNC integration; USB accessory transport alone is insufficient.";
                    }else if(system.equals("Android Auto & Phone Apps")){
                        route="Android Auto service APIs and approved app categories";
                        needed="Test on car screen; app visibility does not prove a custom display is possible.";
                    }else if(system.equals("Connectivity & Remote")){
                        route="Supported Ford account/service APIs or connected accessory";
                        needed="Use a documented and authorized interface, if one is available.";
                    }
                    if(safety){
                        control="not_tested_safety_critical";
                        needed+=" Safety-critical writes are excluded.";
                    }
                    record(items,system,id,name,read,control,evidence,route,needed,safety);
                    count++;
                }
            }
            result.put("report_format","Ford Explorer proposed system-level access inventory v1");
            result.put("vehicle","2021 Ford Explorer Limited, user-supplied model; not electronically identified");
            result.put("scope","Public Android, USB and Bluetooth data only; no CAN/ECU probes");
            result.put("total_feature_candidates",count);
            result.put("vehicle_features_individually_queried",0);
            result.put("verified_control_functions",0);
            result.put("vehicle_ecu_identified_count",0);
            result.put("vehicle_commands_sent",0);
            result.put("cannot_prove_writable_without_safe_authorized_tests",true);
            result.put("entries",items);
            result.put("disclaimer","Rows represent potential vehicle features to investigate; they are not discoveries of installed modules, functional sensors, or controllable equipment.");
        }catch(Exception ex){
            try{result.put("inventory_error",ex.getClass().getSimpleName());}
            catch(Exception ignored){}
        }
        return result;
    }
}
