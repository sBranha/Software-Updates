package com.newswire.vehicleporttester;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Reports the PHONE-INSTALLED Android Auto package version via PackageManager.
 * Ford SYNC firmware/version cannot be determined through public Android Auto
 * APIs, so Ford version is a clearly labeled user-entered value.
 */
final class VehicleVersionPanel {
    private static final String ANDROID_AUTO="com.google.android.projection.gearhead";
    private static final String PREFS="vehicle_explorer_manual_ford_version";
    private final Activity activity;
    private final LinearLayout panel;
    private final TextView androidVersion,fordVersion;
    private String autoStatus="not_checked",autoVersion="",autoCode="";
    private String fordSaved="";
    VehicleVersionPanel(Activity context){
        activity=context;
        panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pd=dp(14);
        panel.setPadding(pd,pd,pd,pd);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xff13293a);bg.setStroke(dp(1),0xff2c4d62);
        bg.setCornerRadius(dp(16));panel.setBackground(bg);
        panel.addView(label("ANDROID AUTO & FORD SYNC VERSIONS",15,0xff46dced,true));
        panel.addView(label("Keep software versions separate from vehicle connection status.",12,0xff9cb4c2,false));
        androidVersion=label("ANDROID AUTO: checking phone…",15,Color.WHITE,true);
        androidVersion.setPadding(0,dp(12),0,dp(5));panel.addView(androidVersion);
        fordVersion=label("FORD SYNC: not supplied",15,Color.WHITE,true);
        panel.addView(fordVersion);
        panel.addView(label("SYNC firmware is NOT automatically readable through the Android Auto USB accessory.",11,0xffffb864,false));
        Button enter=new Button(activity);enter.setText("ENTER FORD SYNC VERSION");
        enter.setAllCaps(false);enter.setTextColor(Color.WHITE);
        enter.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff216276));
        panel.addView(enter,new LinearLayout.LayoutParams(-1,dp(48)));
        enter.setOnClickListener(v->askFord());
        Button refresh=new Button(activity);refresh.setText("REFRESH PHONE ANDROID AUTO VERSION");
        refresh.setAllCaps(false);refresh.setTextColor(Color.WHITE);
        refresh.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff304c62));
        panel.addView(refresh,new LinearLayout.LayoutParams(-1,dp(45)));
        refresh.setOnClickListener(v->refresh());
        panel.addView(label("On the parked Ford screen, look for Settings → General → About SYNC (or Settings → About). Enter the displayed SYNC software version/build, not a guessed model year.",11,0xffaac1ce,false));
        fordSaved=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString("ford_sync_version","").trim();
        updateFordText();
        refresh();
    }
    LinearLayout view(){return panel;}
    private int dp(float x){return (int)(activity.getResources().getDisplayMetrics().density*x+0.5f);}
    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(activity);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    void refresh(){
        try{
            PackageManager pm=activity.getPackageManager();
            PackageInfo pi=pm.getPackageInfo(ANDROID_AUTO,0);
            autoStatus="observed_installed_phone_package";
            autoVersion=pi.versionName==null?"unknown":pi.versionName;
            autoCode=String.valueOf(pi.getLongVersionCode());
            androidVersion.setText("ANDROID AUTO (PHONE): "+autoVersion+"  •  build "+autoCode);
            androidVersion.setTextColor(0xff67e6ac);
        }catch(PackageManager.NameNotFoundException e){
            autoStatus="not_visible_or_not_installed";
            autoVersion="";autoCode="";
            androidVersion.setText("ANDROID AUTO (PHONE): not visible / not installed");
            androidVersion.setTextColor(0xffffbd70);
        }catch(Exception e){
            autoStatus="package_lookup_error";
            autoVersion="";autoCode="";
            androidVersion.setText("ANDROID AUTO: version check unavailable");
            androidVersion.setTextColor(0xffffbd70);
        }
    }
    private void updateFordText(){
        fordVersion.setText(fordSaved.isEmpty()?"FORD SYNC: not entered":
            "FORD SYNC (USER ENTERED): "+fordSaved);
        fordVersion.setTextColor(fordSaved.isEmpty()?0xffffbd70:0xffc1d5e3);
    }
    private void askFord(){
        EditText edit=new EditText(activity);
        edit.setSingleLine(true);
        edit.setText(fordSaved);
        edit.setHint("Example: SYNC 3 version 3.4 build ####");
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        int p=dp(10);edit.setPadding(p,p,p,p);
        new AlertDialog.Builder(activity)
            .setTitle("Ford SYNC version — manual")
            .setMessage("Read the value on the Explorer screen while parked:\nSettings → General → About SYNC (or About).\n\nThis app cannot independently verify the entry.")
            .setView(edit)
            .setPositiveButton("SAVE",(d,w)->{
                fordSaved=edit.getText().toString().trim();
                if(fordSaved.length()>120)fordSaved=fordSaved.substring(0,120);
                activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                    .putString("ford_sync_version",fordSaved).apply();
                updateFordText();
            })
            .setNeutralButton("CLEAR",(d,w)->{
                fordSaved="";
                activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                    .remove("ford_sync_version").apply();
                updateFordText();
            })
            .setNegativeButton("CANCEL",null)
            .show();
    }
    JSONObject snapshot(){
        refresh();
        JSONObject j=new JSONObject();
        try{
            j.put("android_auto_package",ANDROID_AUTO);
            j.put("android_auto_version_name",autoVersion.isEmpty()?JSONObject.NULL:autoVersion);
            j.put("android_auto_version_code",autoCode.isEmpty()?JSONObject.NULL:autoCode);
            j.put("android_auto_source","android_package_manager_installed_phone_app");
            j.put("android_auto_status",autoStatus);
            j.put("ford_sync_version",fordSaved.isEmpty()?JSONObject.NULL:fordSaved);
            j.put("ford_sync_version_source",fordSaved.isEmpty()?
                "not_available_no_public_api":"user_entered_from_vehicle_screen");
            j.put("ford_sync_version_verified_by_app",false);
            j.put("ford_sync_version_auto_detected",false);
            j.put("note","Android Auto version is the phone app, NOT the Ford head-unit firmware version.");
        }catch(Exception ignored){}
        return j;
    }
}
