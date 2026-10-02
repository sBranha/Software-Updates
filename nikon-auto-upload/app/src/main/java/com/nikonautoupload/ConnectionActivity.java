package com.nikonautoupload;

import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class ConnectionActivity extends Activity {
    private final int bg=Color.rgb(7,11,15),panel=Color.rgb(16,23,31),white=Color.rgb(245,247,250),muted=Color.rgb(155,166,178),blue=Color.rgb(33,150,243),green=Color.rgb(55,206,108),amber=Color.rgb(255,193,7);
    private SharedPreferences p;

    @Override public void onCreate(Bundle b){super.onCreate(b);p=getSharedPreferences("settings",MODE_PRIVATE);draw();}
    @Override protected void onResume(){super.onResume();if(p!=null)draw();}

    private void draw(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(14),dp(8),dp(14),dp(12));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=new Button(this);back.setText("‹");back.setTextSize(28);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(54),dp(52)));TextView title=text("Camera Connections",22,white,true);top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(top);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        String snap=toSnapshot();
        LinearLayout status=card();status.addView(text("CURRENT STATUS",13,blue,true));status.addView(text(snap,13,white,false));body.addView(status);

        LinearLayout usb=card();usb.addView(text("USB-C WIRED",18,green,true));usb.addView(text("Easiest offline option. Plug the camera directly into the phone, browse full-resolution files, select/import, or automatically import newly-shot pictures. No IP address or internet is required.",13,muted,false));Button ub=button("OPEN USB-C WIRED IMPORT",green);ub.setOnClickListener(v->startActivity(new Intent(this,UsbCameraActivity.class)));usb.addView(ub);body.addView(usb);

        LinearLayout wifi=card();wifi.addView(text("WI-FI / HOTSPOT / FTP",18,blue,true));wifi.addView(text("Keeps the existing camera Wi-Fi/FTP receiver exactly as before. Use this for full-size wireless transfers and cellular Flickr uploads.",13,muted,false));Button ws=button("OPEN APP CONNECTION SETTINGS",blue);ws.setOnClickListener(v->{Intent i=new Intent(this,MainActivity.class);i.putExtra("open_settings",true);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);finish();});wifi.addView(ws);Button pw=button("OPEN PHONE WI-FI SETTINGS",Color.rgb(70,80,90));pw.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));wifi.addView(pw);body.addView(wifi);

        LinearLayout bt=card();bt.addView(text("BLUETOOTH / WIRELESS SETUP",18,amber,true));bt.addView(text("Nikon smart-device Bluetooth pairing and the experimental automatic Wi-Fi/PTP handoff stay separate from USB-C.",13,muted,false));Button bb=button("OPEN NIKON BLUETOOTH",amber);bb.setOnClickListener(v->startActivity(new Intent(this,NikonBluetoothActivity.class)));bt.addView(bb);body.addView(bt);
        setContentView(root);
        if(Build.VERSION.SDK_INT>=20){root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(14),dp(8),dp(14),Math.max(dp(12),insets.getSystemWindowInsetBottom()+dp(6)));return insets;});root.requestApplyInsets();}
    }

    private String toSnapshot(){
        StringBuilder b=new StringBuilder();
        b.append("Connection: ").append(ConnectionStatus.cameraConnection(p)).append('\n');
        b.append("IP address: ").append(ConnectionStatus.ipv4(this,p)).append('\n');
        b.append("Network: ").append(ConnectionStatus.networkSummary(this));
        String usb=p.getString("usb_camera_name","");if(!usb.isEmpty())b.append("\nUSB camera: ").append(usb);
        return b.toString();
    }
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(12),dp(14),dp(12));l.setBackgroundColor(panel);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));l.setLayoutParams(lp);return l;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setBackgroundTintList(ColorStateList.valueOf(color));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(5),0,dp(3));b.setLayoutParams(lp);return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);t.setLineSpacing(0,1.08f);return t;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
