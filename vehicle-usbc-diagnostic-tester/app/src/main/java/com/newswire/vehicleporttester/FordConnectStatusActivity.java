package com.newswire.vehicleporttester;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Authorized FordConnect staging + optional user-entered snapshot.
 * No third-party login tokens are acquired, no FordPass reverse engineering,
 * and no Ford API calls are attempted without official approved application
 * credentials, OAuth endpoint/scopes, and user authorization.
 */
public final class FordConnectStatusActivity extends Activity {
    static final String CLOUD_PREFS = "fordconnect_readiness_v095";
    static final String MANUAL_PREFS = "ford_app_manual_snapshot_v095";
    static final String PORTAL = "https://developer.ford.com/apis";
    private static final int WHITE=0xffeaf4ff, MUTED=0xffa4bad0, BG=0xff071522, GREEN=0xff62e1ae;
    private TextView result;
    private EditText fuel,oil,mileage,tires;

    static boolean hasInternet(Context c) {
        try {
            ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network=cm==null?null:cm.getActiveNetwork();
            NetworkCapabilities capabilities=cm==null||network==null?null:cm.getNetworkCapabilities(network);
            return capabilities!=null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Exception ignored){return false;}
    }
    static String internetState(Context c){return hasInternet(c)?"PHONE_INTERNET_VALIDATED":"PHONE_INTERNET_NOT_VALIDATED";}
    static String cloudState(){return "NOT_CONFIGURED_REQUIRES_FORD_DEVELOPER_APPROVAL_AND_OAUTH";}

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String value,int size,int color){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(0,dp(7),0,dp(7));return v;
    }
    private Button button(String name,int color){
        Button b=new Button(this);b.setText(name);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));return b;
    }
    private EditText field(String hint,String value){
        EditText e=new EditText(this);e.setTextColor(WHITE);e.setHintTextColor(MUTED);
        e.setTextSize(16);e.setHint(hint);e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(value!=null)e.setText(value);return e;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(17),dp(17),dp(17),dp(30));
        scroll.addView(layout);setContentView(scroll);
        layout.addView(label("FORDCONNECT • CONNECTED VEHICLE",23,0xff4cdbed));
        layout.addView(label("Vehicle Explorer v0.9.5",13,GREEN));
        layout.addView(label("Ford's official FordConnect Query API supports authorized vehicle-data access using owner consent and OAuth. Our app is NOT approved or connected to that API yet. A normal Ford app login cannot be reused by a separate app.",14,WHITE));
        layout.addView(label("Before cloud data can load: 1) register the application with Ford Developer Marketplace; 2) obtain the approved client configuration and scopes; 3) implement Ford's documented authorization flow; 4) grant vehicle-owner consent.",13,MUTED));
        Button check=button("CHECK CLOUD CONNECTION READINESS",0xff126b7e);layout.addView(check);
        result=label("",14,WHITE);layout.addView(result);
        check.setOnClickListener(v->updateStatus());
        Button portal=button("OPEN OFFICIAL FORD DEVELOPER PORTAL",0xff1d637f);layout.addView(portal);
        portal.setOnClickListener(v->{
            try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL)));}
            catch(Exception e){new AlertDialog.Builder(this).setMessage("Open "+PORTAL+" in your browser.").setPositiveButton("OK",null).show();}
        });

        layout.addView(label("OPTIONAL: FORD APP STATUS SNAPSHOT",18,0xff4cdbed));
        layout.addView(label("If your Ford app already shows these values, you can enter them here to display a saved snapshot on your SYNC screen without any new hardware. This is MANUAL, not live or automatically synchronized. Leave unknown values blank.",13,MUTED));
        SharedPreferences m=getSharedPreferences(MANUAL_PREFS,MODE_PRIVATE);
        fuel=field("Fuel level (%)",m.getString("fuel",""));layout.addView(fuel);
        oil=field("Oil life (%)",m.getString("oil",""));layout.addView(oil);
        mileage=field("Odometer (miles)",m.getString("miles",""));layout.addView(mileage);
        tires=field("Tire pressure (psi, optional)",m.getString("tires",""));layout.addView(tires);
        Button save=button("SAVE MANUAL FORD APP SNAPSHOT",0xff187c5f);layout.addView(save);
        save.setOnClickListener(v->saveSnapshot());
        Button clear=button("CLEAR MANUAL SNAPSHOT",0xff60526f);layout.addView(clear);
        clear.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Clear manual readings?").setMessage("This only deletes the values you typed into Vehicle Explorer.")
            .setPositiveButton("Clear",(d,w)->{
                m.edit().clear().apply();
                fuel.setText("");oil.setText("");mileage.setText("");tires.setText("");
                new AlertDialog.Builder(this).setMessage("Manual snapshot cleared.").setPositiveButton("OK",null).show();
            }).setNegativeButton("Cancel",null).show());
        layout.addView(label("Privacy: This screen does not request your Ford app password, VIN, developer secret, or authorization token. Your manual snapshot remains on this phone and is labeled user-entered on SYNC 3.",12,MUTED));
        updateStatus();
    }
    private void updateStatus(){
        String net=internetState(this);
        SharedPreferences p=getSharedPreferences(CLOUD_PREFS,MODE_PRIVATE);
        p.edit().putString("internet",net).putString("fordconnect",cloudState())
            .putString("oauth","NOT_AUTHORIZED").putString("vehicle_api_response","NOT_TESTED_NO_APPROVED_CREDENTIALS")
            .putLong("checked_ms",System.currentTimeMillis()).apply();
        result.setText("Phone internet: "+(hasInternet(this)?"AVAILABLE":"NOT VALIDATED")+
                "\nFordConnect approval: NOT CONFIGURED"+
                "\nFord OAuth: NOT AUTHORIZED"+
                "\nCloud vehicle data: NOT TESTED"+
                "\nNo data received from Ford's servers.");
    }
    private static boolean validRange(String raw,double min,double max){
        if(raw.isEmpty())return true;
        try{double x=Double.parseDouble(raw);return !Double.isNaN(x)&&!Double.isInfinite(x)&&x>=min&&x<=max;}
        catch(Exception ex){return false;}
    }
    private static String str(EditText x){return x.getText().toString().trim();}
    private void saveSnapshot(){
        String f=str(fuel),o=str(oil),m=str(mileage),t=str(tires);
        if(!validRange(f,0,100)||!validRange(o,0,100)||!validRange(m,0,10000000)||!validRange(t,0,200)){
            new AlertDialog.Builder(this).setTitle("Check the numbers")
                .setMessage("Fuel/oil must be 0-100%, odometer 0-10,000,000 miles, and tire pressure 0-200 psi. Leave unknown values blank.")
                .setPositiveButton("OK",null).show();return;
        }
        if(f.isEmpty()&&o.isEmpty()&&m.isEmpty()&&t.isEmpty()){
            new AlertDialog.Builder(this).setMessage("Enter at least one reading from the Ford app, or leave this optional feature unused.")
                .setPositiveButton("OK",null).show();return;
        }
        getSharedPreferences(MANUAL_PREFS,MODE_PRIVATE).edit()
                .putString("fuel",f).putString("oil",o).putString("miles",m).putString("tires",t)
                .putLong("saved_ms",System.currentTimeMillis())
                .putString("source","USER_ENTERED_FROM_FORD_APP_NOT_LIVE")
                .apply();
        new AlertDialog.Builder(this).setTitle("Manual snapshot saved")
            .setMessage("Open Vehicle Explorer on SYNC 3, then select Ford app manual snapshot. Values are not automatically updated.")
            .setPositiveButton("OK",null).show();
    }
}
