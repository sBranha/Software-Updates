package com.newswire.vehicleporttester;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Standalone FordConnect login and real cloud telemetry. Not connected to the
 * Ford Android widget, installed Ford app, or vehicle USB port.
 * This is a personal-use Ford developer client. Developer credentials and
 * OAuth consent are required before a cloud response can be retrieved.
 */
public final class FordConnectActivity extends Activity {
    private static final int BG=0xff071522,WHITE=0xffe7f3ff,MUTED=0xffa4bbcd,GREEN=0xff68e1b0;
    private EditText client,secret,redirectInput;
    private TextView status,values;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ServerSocket server;
    private final Runnable refreshLoop=new Runnable(){
        @Override public void run(){refreshUi();handler.postDelayed(this,1500);}
    };
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String s,int sp,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);
        t.setTextColor(color);t.setPadding(0,dp(7),0,dp(7));return t;
    }
    private Button button(String s,int color){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);
        b.setTextColor(WHITE);b.setBackgroundTintList(ColorStateList.valueOf(color));return b;
    }
    private EditText entry(String hint,String prefill,boolean hidden){
        EditText e=new EditText(this);
        e.setHint(hint);e.setText(prefill);e.setTextSize(14);e.setSingleLine(true);
        e.setTextColor(WHITE);e.setHintTextColor(MUTED);
        e.setInputType(hidden?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        e.setSelectAllOnFocus(true);return e;
    }
    private android.content.SharedPreferences prefs(){
        return getSharedPreferences(FordConnectClient.PREF,MODE_PRIVATE);
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        sc.setVerticalScrollBarEnabled(false);sc.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(19),dp(18),dp(19),dp(26));
        sc.addView(body);setContentView(sc);
        body.addView(label("FORDCONNECT • STANDALONE",22,0xff4cdbef));
        body.addView(label("Vehicle Explorer v0.9.8 • Ford connected-vehicle cloud",14,GREEN));
        body.addView(label("The Ford app and its home-screen widget are NOT used by this connection. Your Explorer's cellular modem can report vehicle status through FordConnect once Ford approves your developer app and you authorize it.",14,WHITE));
        body.addView(label("FIRST: Ford developer application (North America)",18,0xff4cdbef));
        Button developer=button("1. REGISTER FORD DEVELOPER APPLICATION",0xff176b8d);
        body.addView(developer);
        developer.setOnClickListener(v->open("https://developer.ford.com/developer-na"));
        body.addView(label("In Ford Developer Marketplace, create a personal-use FordConnect Query app and select the read-only vehicle categories. For its redirect URL, use exactly:",13,MUTED));
        Button copyRedirect=button("2. COPY REGISTERED CALLBACK URL",0xff176b8d);
        body.addView(copyRedirect);
        body.addView(label(FordConnectClient.REDIRECT,13,GREEN));
        copyRedirect.setOnClickListener(v->{
            ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Ford OAuth redirect",FordConnectClient.REDIRECT));
            new AlertDialog.Builder(this).setMessage("Copied. Register this exact localhost callback with Ford. If Ford refuses localhost, do not continue until a supported redirect is approved.")
                .setPositiveButton("OK",null).show();
        });
        body.addView(label("Ford gives you a Client ID and Client Secret. Type them on this phone only. Never send them in a screenshot, chat, report, or GitHub.",13,0xffffc887));
        client=entry("Ford Developer Client ID",FordConnectClient.clientId(this),false);
        body.addView(client);
        secret=entry("Ford Developer Client Secret (leave blank to keep saved)", "",true);
        body.addView(secret);
        Button save=button("3. SAVE DEVELOPER CREDENTIALS ON PHONE",0xff187b64);body.addView(save);
        save.setOnClickListener(v->saveConfig());
        body.addView(label("Owner authorization",18,0xff4cdbef));
        Button login=button("4. SIGN IN AND GRANT FORD VEHICLE CONSENT",0xff167c6a);
        body.addView(login);
        login.setOnClickListener(v->startLogin());
        body.addView(label("After you consent on Ford's browser page, Android should return the authorization to this app through localhost. If the browser cannot complete the return, copy the COMPLETE redirect URL from its address bar and paste it below.",13,MUTED));
        redirectInput=entry("Paste full http://localhost:8765/callback?code=... URL","",false);
        body.addView(redirectInput);
        Button paste=button("COMPLETE FORD LOGIN FROM REDIRECT URL",0xff176b8d);body.addView(paste);
        paste.setOnClickListener(v->completeLogin(redirectInput.getText().toString().trim()));
        body.addView(label("Vehicle status",18,0xff4cdbef));
        Button read=button("5. REFRESH REAL FORD VEHICLE DATA",0xff187b64);
        body.addView(read);
        read.setOnClickListener(v->FordConnectClient.refreshAsync(this,true,result->{
            refreshUi();
            new AlertDialog.Builder(this).setMessage(result).setPositiveButton("OK",null).show();
        }));
        status=label("",14,GREEN);body.addView(status);
        values=label("",16,WHITE);body.addView(values);
        Button clear=button("DISCONNECT THIS FORD CLOUD ACCOUNT",0xff615267);body.addView(clear);
        clear.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Disconnect FordConnect?")
            .setMessage("Remove your Ford API credentials, local tokens, and all saved cloud readings from this phone? Your Ford account and existing vehicle settings will not be changed.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Disconnect",(d,w)->{
                FordConnectClient.disconnect(this);client.setText("");secret.setText("");refreshUi();
            }).show());
        body.addView(label("Privacy: Ford Developer credentials and OAuth tokens are encrypted on this phone with Android Keystore. No VIN, GPS position, Ford password, token, or secret appears in exported diagnostic reports. FordConnect only reads vehicle status; it cannot lock, unlock, remote-start, or change your vehicle. Refresh is throttled to protect Ford's API.",12,MUTED));
        refreshUi();
    }
    private void open(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){new AlertDialog.Builder(this).setMessage("Please open "+url+" in your phone browser.").setPositiveButton("OK",null).show();}
    }
    private void saveConfig(){
        try{
            FordConnectClient.configure(this,client.getText().toString(),secret.getText().toString());
            secret.setText("");
            refreshUi();
            new AlertDialog.Builder(this).setMessage("Ford developer app information saved locally. Your Ford owner login is still required before actual vehicle data can be read.")
                .setPositiveButton("OK",null).show();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("Could not save configuration: "+e.getMessage())
                .setPositiveButton("OK",null).show();}
    }
    private void startLogin(){
        try{
            String auth=FordConnectClient.authorizationUrl(this);
            startLoopback();
            open(auth);
        }catch(Exception ex){
            new AlertDialog.Builder(this).setMessage("Cannot start Ford login: "+ex.getClass().getSimpleName()+
                ". Save your Ford developer client ID and secret first.")
                .setPositiveButton("OK",null).show();
        }
    }
    private void startLoopback(){
        closeServer();
        new Thread(()->{
            try(ServerSocket socket=new ServerSocket(8765,1,InetAddress.getByName("127.0.0.1"))){
                server=socket;
                socket.setSoTimeout(180000);
                try(Socket incoming=socket.accept()){
                    incoming.setSoTimeout(5000);
                    BufferedReader reader=new BufferedReader(new InputStreamReader(
                            incoming.getInputStream(),StandardCharsets.US_ASCII));
                    String request=reader.readLine();
                    String path=request!=null&&request.startsWith("GET ")?request.split(" ")[1]:"";
                    String redirect=FordConnectClient.REDIRECT;
                    if(path.startsWith("/callback?"))redirect=redirect+path.substring("/callback".length());
                    boolean good=FordConnectClient.validRedirect(this,redirect);
                    String text=good?"Ford authorization received. Return to Vehicle Explorer.":
                            "Ford authorization not verified. Return to Vehicle Explorer and check the login.";
                    byte[] body=("<html><body><h2>"+text+"</h2></body></html>")
                            .getBytes(StandardCharsets.UTF_8);
                    byte[] headers=("HTTP/1.1 "+(good?"200 OK":"400 Bad Request")+
                            "\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: "+
                            body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                    try(OutputStream out=incoming.getOutputStream()){out.write(headers);out.write(body);out.flush();}
                    if(good)completeLogin(redirect);
                }
            }catch(SocketTimeoutException timeout){
                // Manual redirect-paste fallback stays available.
            }catch(Exception error){
                handler.post(()->{
                    if(status!=null && !FordConnectClient.authorized(this))
                        status.setText("Local callback unavailable; use the full redirect URL paste option.");
                });
            }finally{server=null;}
        },"FordLocalOAuthCallback").start();
    }
    private void completeLogin(String redirect){
        if(redirect.isEmpty()){
            new AlertDialog.Builder(this).setMessage("Paste the full Ford redirect URL including the code and state.").setPositiveButton("OK",null).show();
            return;
        }
        if(!FordConnectClient.validRedirect(this,redirect)){
            new AlertDialog.Builder(this).setMessage("The Ford redirect is not recognized, is expired, or does not match the current login state. Start Ford sign-in again if necessary.")
                .setPositiveButton("OK",null).show();
            return;
        }
        FordConnectClient.exchangeRedirectAsync(this,redirect,result->{
            refreshUi();
            new AlertDialog.Builder(this).setMessage(result).setPositiveButton("OK",null).show();
        });
    }
    private void refreshUi(){
        if(status==null||values==null)return;
        boolean configured=FordConnectClient.hasConfiguration(this);
        boolean auth=FordConnectClient.authorized(this);
        status.setText("FordConnect: "+FordConnectClient.status(this)+
            "\nDeveloper app credentials: "+(configured?"SAVED ON THIS PHONE":"NOT CONFIGURED")+
            "\nOwner authorization: "+(auth?"TOKEN STORED":"NOT VERIFIED")+
            "\nFord Android app needed: NO");
        long at=FordConnectClient.lastRead(this);
        if(at==0){values.setText("No Ford cloud readings confirmed yet. Sign in through Ford and refresh to begin.");return;}
        android.content.SharedPreferences p=prefs();
        String fuel=p.contains("fuel_pct")?p.getInt("fuel_pct",-1)+"%":"unavailable";
        String range=p.contains("range_mi")?p.getInt("range_mi",-1)+" miles":"unavailable";
        String odometer=p.contains("odometer_mi")?p.getInt("odometer_mi",-1)+" miles":"unavailable";
        String oil=p.contains("oil_pct")?p.getInt("oil_pct",-1)+"%":"unavailable";
        long age=Math.max(0,(System.currentTimeMillis()-at)/60000);
        values.setText("FORDCONNECT CLOUD SNAPSHOT\nFuel: "+fuel+
            "\nEstimated range: "+range+
            "\nOdometer: "+odometer+
            "\nOil life: "+oil+
            "\nLast real Ford API response: "+age+" min ago"+
            "\nSource: FordConnect Query (not Ford app / widget / OBD-II).");
    }
    private void closeServer(){
        try{if(server!=null)server.close();}catch(Exception ignored){}
        server=null;
    }
    @Override protected void onResume(){
        super.onResume();handler.removeCallbacks(refreshLoop);
        handler.post(refreshLoop);
    }
    @Override protected void onPause(){
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }
    @Override protected void onDestroy(){
        closeServer();handler.removeCallbacks(refreshLoop);super.onDestroy();
    }
}
