package com.newswire.vehicleporttester;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Source-audited 2021 Ford Explorer Limited profile and investigation matrix.
 * Describes what has been directly seen vs phone-only vs unavailable.
 * Does NOT enumerate CAN/ECUs and never sends Ford commands.
 * No VIN/MAC/modem serial numbers are collected or exported.
 */
final class ExplorerResearchPanel {
    private static final String PREFS="explorer_research_v087";
    private final Activity activity;
    private final SharedPreferences store;
    private final LinearLayout panel;
    private final TextView countLabel;
    private final JSONArray items=new JSONArray();
    private static final int TEAL=0xff47dbe9,WHITE=0xfff2f8fe,MUTED=0xffa0b7c9,GREEN=0xff67e2a6,ORANGE=0xffffbb74;
    private int dp(float a){return (int)(a*activity.getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String s,int sp,int color,boolean bold){
        TextView t=new TextView(activity);
        t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void gap(int h){View v=new View(activity);panel.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void label(String name,String value,String source){
        TextView n=text(name,12,TEAL,true);panel.addView(n);
        TextView v=text(value,15,WHITE,true);panel.addView(v);
        TextView src=text(source,11,MUTED,false);panel.addView(src);
        gap(8);
    }
    private Button button(String title){
        Button b=new Button(activity);b.setText(title);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff216078));
        return b;
    }
    ExplorerResearchPanel(Activity context){
        activity=context;
        store=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(15),dp(14),dp(15));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xff152a3e);bg.setCornerRadius(dp(18));bg.setStroke(dp(1),0xff2b4d61);
        panel.setBackground(bg);
        panel.addView(text("EXPLORER VEHICLE PROFILE",17,TEAL,true));
        gap(4);
        panel.addView(text("What we KNOW from your screenshots and tests—not a VIN-based equipment lookup.",12,MUTED,false));
        gap(12);
        label("VEHICLE","2021 Ford Explorer Limited","Provided by vehicle owner • not detected by phone");
        label("INFOTAINMENT","SYNC 3 • software 3.4 • build 23188_PRODUCT","Confirmed from vehicle About SYNC photo");
        label("FACTORY NAVIGATION","North America map database: NA 2 20","Confirmed from About SYNC photo; map database version, not a live map feed");
        label("GRACENOTE DATABASE","1.1.1.2323 • NA-0020","Confirmed from About SYNC photo");
        label("ANDROID AUTO","Projection supported; version shown in Software Versions card","USB accessory communication and Android Auto launcher previously observed");
        label("TOUCHSCREEN COMPATIBILITY","Working with read-only, nonintrusive scan","Owner confirmed after v0.8.3; requires retest after updates");
        label("IDENTIFIERS","VIN, Bluetooth MAC, hotspot MAC and modem serial omitted","Private vehicle identifiers are not necessary for diagnostics here");
        panel.addView(text("SYSTEM CAPABILITY INVESTIGATION",15,TEAL,true));
        gap(5);
        countLabel=text("",12,MUTED,false);panel.addView(countLabel);
        gap(8);
        populateItems();
        Button explore=button("EXPLORE ALL TESTS AND LIMITATIONS");
        panel.addView(explore,new LinearLayout.LayoutParams(-1,dp(47)));
        explore.setOnClickListener(v->showMatrix());
        gap(6);
        panel.addView(text("User-marked observations remain just observations. A test does not prove a Ford module or control interface exists.",11,ORANGE,false));
        refreshCounts();
    }
    LinearLayout view(){return panel;}

    private void add(String id,String group,String title,String status,String method,String caveat){
        try{items.put(new JSONObject().put("id",id).put("group",group)
          .put("title",title).put("baseline_status",status)
          .put("method",method).put("limitation",caveat));}catch(Exception ignored){}
    }
    private void populateItems(){
        // confirmed-by-photo is distinct from automatic read access.
        add("vehicle_identity","Profile","2021 Explorer Limited","owner_reported","Vehicle owner description","No VIN decoding or trim equipment inventory performed");
        add("sync3","Infotainment","SYNC 3 software 3.4 build 23188","confirmed_photo","Vehicle Settings > About SYNC photo","Not queried through Android Auto");
        add("factory_nav","Infotainment","Factory Ford navigation app","confirmed_photo","Nav button on Ford SYNC screen","Installed map does not expose a public overlay API");
        add("nav_db","Infotainment","Factory maps database NA 2 20","confirmed_photo","About SYNC screen","Not a live maps data interface");
        add("gracenote","Infotainment","Gracenote database 1.1.1.2323 (NA-0020)","confirmed_photo","About SYNC screen","Music metadata database only");
        add("android_auto","Phone projection","Android Auto support and launcher","confirmed_user_test","Connect phone and view Android Auto launcher","Not access to internal vehicle computer");
        add("android_auto_version","Phone projection","Phone Android Auto app version","phone_readable","Android PackageManager version check","Version on phone, not SYNC firmware");
        add("usb_accessory","Phone projection","Accessory present and USB connected","phone_readable","Read USB accessory metadata; do NOT open connection","Raw USB takeover can freeze the Ford touchscreen");
        add("touch","Phone projection","Ford touch responsive with safe scan","confirmed_user_test","Tap CHECK FORD TOUCHSCREEN","User observation, not an electronic readback");
        add("car_apps","Phone projection","Car-compatible installed apps","phone_readable","Android package visibility when permitted","Phone apps, not Ford embedded apps");
        add("media","Infotainment","Media browse and controls","supported_in_respective_apps","Test media buttons in Android Auto apps","No vehicle CAN control verified");
        add("voice","Infotainment","Voice assistant and microphone","manual_test_possible","Test normal Android Auto voice assistant","No access to Ford mic hardware APIs proven");
        add("bluetooth","Bluetooth","Connected audio profile names","phone_readable_on_permission","Run Bluetooth Audio Connection Test","Not a vehicle diagnostic data link");
        add("phone_gps","Phone sensors","GPS location / speed","phone_readable_on_permission","Enable Phone GPS Speed","Phone measurement, not wheel-speed sensor");
        add("heading","Phone sensors","GPS travel bearing","phone_readable_on_permission","Move at safe speed after GPS enabled","Needs motion and satellite lock; not vehicle compass");
        add("trip","Phone sensors","Trip distance from GPS","phone_readable_on_permission","GPS sampling in Instrument Lab","Not actual Ford odometer");
        add("elevation","Phone sensors","GPS elevation","phone_readable_on_permission","Instrument Lab with GPS enabled","Elevation varies with GPS accuracy");
        add("acceleration","Phone sensors","Phone accelerometer","phone_readable","Motion Lab","Orientation/hand movement and gravity can contaminate reading");
        add("camera_tach","Optical tests","Tachometer via phone camera","experiment_available","Camera RPM Reader; 3 point calibration","Not ECU RPM; optically approximate and can misread");
        add("engine_rpm","Vehicle diagnostics","Engine RPM sensor PID","vehicle_unavailable","Would require supported diagnostic interface","No OBD adapter and no public SYNC RPM interface verified");
        add("odometer","Vehicle diagnostics","Actual vehicle odometer","vehicle_unavailable","Requires supported vehicle data interface","GPS trip is not odometer");
        add("coolant","Vehicle diagnostics","Engine coolant temperature","vehicle_unavailable","Requires supported vehicle diagnostic data","Phone cannot read via Android Auto");
        add("oil","Vehicle diagnostics","Oil life / oil pressure","vehicle_unavailable","Requires OEM or supported vehicle diagnostics","Not directly exposed to generic apps");
        add("fuel","Vehicle diagnostics","Fuel level / range / fuel economy","vehicle_unavailable","Requires OEM or supported vehicle diagnostics","No actual Ford sensor access proven");
        add("battery","Vehicle diagnostics","Vehicle battery voltage","vehicle_unavailable","Requires proper vehicle sensor interface","Phone battery is unrelated");
        add("tpms","Vehicle diagnostics","Tire pressure monitoring","vehicle_unavailable","Would require supported OEM vehicle interface","No TPMS module communication confirmed");
        add("codes","Vehicle diagnostics","Fault codes / diagnostic modules","vehicle_unavailable","Requires authorized diagnostic connection","No ECU enumeration from safe Android Auto metadata");
        add("doors","Vehicle controls","Locks / windows / tailgate","no_control_interface","No writing or actuation through this app","Do not guess vehicle control addresses");
        add("climate","Vehicle controls","Fan / temperature settings","no_control_interface","May be adjustable by built-in Ford controls only","No SDK read/write access from Android Auto");
        add("cameras","Vehicle controls","Built-in Ford cameras","no_control_interface","Use built-in vehicle view where supported","Not an Android camera permission");
        add("autopilot","Vehicle controls","Safety systems, brakes, steering, ADAS","no_control_interface","No experimental actuation","Safety-critical functions deliberately excluded");
        add("nws_warnings","Storm technology","Live NWS warning polygons on NewsWire map","future_supported_path","Build separate weather map using Android for Cars weather category","NWS warnings are NOT storm tracks or precise predicted paths");
        add("native_nav_overlay","Storm technology","Overlay on Ford factory navigation map","not_supported_by_public_api","No exposed SYNC 3 factory-map drawing API verified","Requires Ford OEM integration, not USB sniffing");
        add("android_auto_weather","Storm technology","NewsWire map on Android Auto screen","plausible_needs_build_and_test","Car App Library weather map templates and supported Play distribution","Separate map inside NewsWire, not overlay on Ford factory map");
        add("storm_track","Storm technology","Predicted moving-storm track over map","future_advanced_analysis","Requires radar motion vectors, verified forecasting and route geometry","NWS warnings alone are not storm trajectory; avoid false safety claims");
    }
    void refreshCounts(){
        int photo=0,phone=0,blocked=0;
        for(int i=0;i<items.length();i++){
            JSONObject x=items.optJSONObject(i);
            if(x==null)continue;
            String st=x.optString("baseline_status");
            if("confirmed_photo".equals(st)||"confirmed_user_test".equals(st))photo++;
            if(st.startsWith("phone_readable"))phone++;
            if(st.contains("unavailable")||st.contains("no_control")||st.contains("not_supported"))blocked++;
        }
        countLabel.setText(String.format(Locale.US,
          "%d investigation targets • %d screenshot/user observations • %d phone-readable candidates • %d unavailable/control-limited",
          items.length(),photo,phone,blocked));
    }
    private String statusLabel(String st){
        switch(st){
            case "confirmed_photo":return "CONFIRMED FROM PHOTO";
            case "confirmed_user_test":return "USER CONFIRMED";
            case "owner_reported":return "OWNER REPORTED";
            case "phone_readable":case "phone_readable_on_permission":return "PHONE-READABLE";
            case "vehicle_unavailable":return "NOT VEHICLE-READABLE";
            case "no_control_interface":return "NO CONTROL ACCESS";
            case "not_supported_by_public_api":return "NO PUBLIC OVERLAY API";
            case "experiment_available":return "EXPERIMENT AVAILABLE";
            case "future_supported_path":case "plausible_needs_build_and_test":return "FUTURE / NEEDS TEST";
            default:return "POSSIBLE / MANUAL TEST";
        }
    }
    private void showMatrix(){
        LinearLayout inner=new LinearLayout(activity);inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(12),dp(10),dp(12),dp(10));
        TextView intro=text("Tap any topic to see exactly how we could test it and what remains unproven. Your notes stay on this phone.",12,MUTED,false);
        inner.addView(intro);
        for(int i=0;i<items.length();i++){
            JSONObject x=items.optJSONObject(i);if(x==null)continue;
            Button b=button(x.optString("title")+"  ·  "+statusLabel(x.optString("baseline_status")));
            b.setTextSize(11);b.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
            inner.addView(b,new LinearLayout.LayoutParams(-1,dp(53)));
            View divider=new View(activity);inner.addView(divider,new LinearLayout.LayoutParams(1,dp(3)));
            b.setOnClickListener(v->itemDetails(x));
        }
        android.widget.ScrollView scroll=new android.widget.ScrollView(activity);
        scroll.addView(inner);
        new AlertDialog.Builder(activity).setTitle("Explorer Research • "+items.length()+" topics")
          .setView(scroll).setPositiveButton("CLOSE",null).show();
    }
    private void itemDetails(JSONObject x){
        String id=x.optString("id");
        String note=store.getString("test_"+id,"not_recorded");
        String body="Group: "+x.optString("group")
            +"\nEvidence: "+statusLabel(x.optString("baseline_status"))
            +"\n\nHOW TO INVESTIGATE\n"+x.optString("method")
            +"\n\nLIMITATION\n"+x.optString("limitation")
            +"\n\nYour observation: "+note
            +"\n\nMarking a test changes only your own notes. It does not confirm ECU access.";
        new AlertDialog.Builder(activity).setTitle(x.optString("title"))
            .setMessage(body)
            .setPositiveButton("MARK SEEN",(d,w)->store.edit().putString("test_"+id,"user_observed").apply())
            .setNeutralButton("MARK NOT WORKING",(d,w)->store.edit().putString("test_"+id,"user_reports_not_working").apply())
            .setNegativeButton("CLOSE",null).show();
    }
    JSONObject snapshot(){
        JSONObject out=new JSONObject();
        try{
            JSONObject profile=new JSONObject()
                .put("vehicle","2021 Ford Explorer Limited")
                .put("vehicle_source","owner_reported")
                .put("sync_platform","SYNC 3")
                .put("sync_software_version","3.4")
                .put("sync_build","23188_PRODUCT")
                .put("sync_source","user_supplied_about_sync_photo")
                .put("navigation_database","NA 2 20")
                .put("navigation_source","user_supplied_about_sync_photo")
                .put("gracenote","1.1.1.2323 (NA-0020)")
                .put("gracenote_source","user_supplied_about_sync_photo")
                .put("ford_sync_version_auto_read",false)
                .put("factory_map_direct_overlay_supported",false)
                .put("sensitive_ids_exported",false);
            out.put("profile",profile);
            JSONArray details=new JSONArray();
            for(int i=0;i<items.length();i++){
                JSONObject original=items.optJSONObject(i);if(original==null)continue;
                JSONObject item=new JSONObject(original.toString());
                String note=store.getString("test_"+item.optString("id"),"not_recorded");
                item.put("user_test_note",note);
                item.put("user_test_note_is_not_verified_vehicle_telemetry",true);
                details.put(item);
            }
            out.put("research_items",details);
            out.put("targets_count",details.length());
            out.put("no_ecu_data_read",true);
            out.put("privacy","Never save VIN, MAC addresses or SYNC/modem serial numbers in this report.");
        }catch(Exception ignored){}
        return out;
    }
}
