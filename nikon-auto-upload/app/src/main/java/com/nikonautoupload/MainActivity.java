package com.nikonautoupload;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root,body,nav;
    SharedPreferences p;
    TextView status,connectionInfo;
    boolean receiverRegistered=false;
    int white=Color.rgb(244,247,250),muted=Color.rgb(154,168,182),blue=Color.rgb(33,150,243),green=Color.rgb(57,208,111),panel=Color.rgb(17,24,32);

    private final BroadcastReceiver directReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(!DirectTransferService.ACTION_STATUS.equals(i.getAction()))return;
            String s=i.getStringExtra("status");
            if(status!=null&&s!=null){status.setText("● "+s);status.setTextColor(s.toLowerCase(Locale.US).contains("error")||s.toLowerCase(Locale.US).contains("could not")?muted:green);}
            if(connectionInfo!=null){
                String current=connectionInfo.getText().toString();
                String ssid=i.getStringExtra("ssid"),wifi=i.getStringExtra("wifi_pass"),ip=i.getStringExtra("ip");
                String user=i.getStringExtra("ftp_user"),pass=i.getStringExtra("ftp_pass");
                int port=i.getIntExtra("ftp_port",2121);
                if(ssid!=null||ip!=null||pass!=null){
                    StringBuilder b=new StringBuilder();
                    if(ssid!=null&&!ssid.isEmpty())b.append("CAMERA WI-FI\nName: ").append(ssid).append("\nPassword: ").append(wifi==null?"":wifi).append("\n\n");
                    else if(current.contains("CAMERA WI-FI")) b.append(current.substring(0,current.indexOf("FTP SERVER")));
                    b.append("FTP SERVER\nIP: ").append(ip==null?extractOld(current,"IP: "):ip).append("\nPort: ").append(port)
                            .append("\nUser: ").append(user==null?"nikon":user).append("\nPassword: ").append(pass==null?p.getString("ftp_password",""):pass);
                    connectionInfo.setText(b.toString());
                }
            }
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        p=getSharedPreferences("settings",MODE_PRIVATE);
        requestPerms();
        registerDirectReceiver();
        drawHome();
        handleCallback(getIntent());
    }

    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleCallback(i);}
    @Override protected void onDestroy(){if(receiverRegistered)unregisterReceiver(directReceiver);super.onDestroy();}

    void registerDirectReceiver(){
        IntentFilter f=new IntentFilter(DirectTransferService.ACTION_STATUS);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(directReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(directReceiver,f);
        receiverRegistered=true;
    }

    void handleCallback(Intent i){
        Uri d=i.getData();
        if(d!=null&&"nikonautoupload".equals(d.getScheme()))new Thread(()->{
            try{new FlickrClient(this).finishAuth(d);runOnUiThread(()->{toast("Flickr connected");drawSettings();});}
            catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}
        }).start();
    }

    void base(String title){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(12),dp(18),dp(18));root.setBackgroundColor(Color.rgb(8,12,16));
        root.addView(txt(title,24,white,true),new LinearLayout.LayoutParams(-1,dp(58)));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,0,0,dp(8));
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);nav.setPadding(0,dp(4),0,dp(10));nav.setBackgroundColor(Color.rgb(10,15,20));
        String[] ns={"Home","Photos","Uploads","Settings"};
        for(String n:ns){Button b=button(n);b.setSingleLine(true);b.setTextSize(12);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(dp(2),0,dp(2),0);b.setOnClickListener(v->{if(n.equals("Home"))drawHome();else if(n.equals("Settings"))drawSettings();else placeholder(n);});nav.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(68));lp.setMargins(0,dp(4),0,dp(6));root.addView(nav,lp);setContentView(root);
        if(Build.VERSION.SDK_INT>=20){root.setOnApplyWindowInsetsListener((v,insets)->{int bottom=insets.getSystemWindowInsetBottom();root.setPadding(dp(18),dp(12),dp(18),Math.max(dp(18),bottom+dp(8)));return insets;});root.requestApplyInsets();}
    }

    void drawHome(){
        base("Nikon Auto Upload");
        card("Nikon Z8","Direct Wi-Fi transfer → Android → Flickr",green);
        status=txt("● Ready for direct Z8 transfer — no SnapBridge required",16,green,true);body.addView(status);space();

        Button openHotspot=big("OPEN PHONE HOTSPOT SETTINGS");
        openHotspot.setOnClickListener(v->{try{startActivity(new Intent("android.settings.TETHER_SETTINGS"));}catch(Exception e){startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}});body.addView(openHotspot);
        Button startReceiver=big("START RECEIVER ON PHONE HOTSPOT");startReceiver.setOnClickListener(v->startDirect("server"));body.addView(startReceiver);

        section("Recommended at a ball game");
        body.addView(txt("Use your phone's normal Mobile Hotspot. The Z8 connects to the phone's Wi-Fi hotspot, while the phone keeps using 5G/LTE for Flickr. The hotspot name/password stay familiar, so after the Z8 is configured you normally do not have to enter new Wi-Fi credentials every session.",15,white,false));

        Button quick=big("QUICK PRIVATE CAMERA HOTSPOT");quick.setOnClickListener(v->{if(hasWifiPermission())startDirect("local");else requestPerms();});body.addView(quick);
        body.addView(txt("Quick Private Hotspot is fully created by this app and has no internet access of its own, so your phone keeps cellular internet. Android may give this quick hotspot a new Wi-Fi name/password when it is recreated.",13,muted,false));

        section("Connection information");
        connectionInfo=txt("Start one of the camera modes above. This box will show the FTP server IP, port, username and password to enter in the Z8.",15,white,false);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(16),dp(16),dp(16),dp(16));info.setBackgroundColor(panel);info.addView(connectionInfo);body.addView(info);

        Button stop=big("STOP CAMERA MODE");stop.setOnClickListener(v->{stopService(new Intent(this,DirectTransferService.class));status.setText("● Camera mode stopped");status.setTextColor(muted);});body.addView(stop);

        section("Z8 one-time setup");
        body.addView(txt("1. Connect the Z8 to the phone hotspot Wi-Fi.\n\n2. On the Z8: Network Menu → Connect to FTP server.\n\n3. Choose FTP, enter the Server IP shown above, Port 2121, User nikon, and the password shown above.\n\n4. In the Z8 FTP options, turn Auto upload ON. For RAW+JPEG shooting, choose to send JPEG if that is what you want on Flickr.\n\n5. Take a picture. The Z8 sends it straight to this phone. The app saves it in Pictures/Nikon Auto Upload and sends it to Flickr.",15,white,false));
    }

    void startDirect(String mode){
        Intent i=new Intent(this,DirectTransferService.class).putExtra("mode",mode);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        if(status!=null){status.setText("● Starting direct camera mode…");status.setTextColor(blue);}
    }

    void drawSettings(){
        base("Settings");section("Flickr");
        EditText k=input("Flickr API key",p.getString("flickr_key",""));EditText s=input("Flickr API secret",p.getString("flickr_secret",""));body.addView(k);body.addView(s);
        Button save=big("SAVE FLICKR API KEY");save.setOnClickListener(v->{p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();toast("Saved");});body.addView(save);
        Button conn=big(p.getString("access_token","").isEmpty()?"CONNECT FLICKR":"RECONNECT FLICKR");conn.setOnClickListener(v->{p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();new Thread(()->{try{String u=new FlickrClient(this).beginAuth();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}}).start();});body.addView(conn);
        if(!p.getString("flickr_name","").isEmpty())body.addView(txt("Connected as: "+p.getString("flickr_name",""),15,green,true));
        section("Upload defaults");
        EditText tags=input("Default Flickr tags",p.getString("tags","nikon z8"));body.addView(tags);tags.setOnFocusChangeListener((v,f)->{if(!f)p.edit().putString("tags",tags.getText().toString()).apply();});
        Switch pub=new Switch(this);pub.setText("Upload as Public");pub.setTextColor(white);pub.setChecked(p.getBoolean("public",true));pub.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("public",c).apply());body.addView(pub);
        section("Direct transfer");body.addView(txt("Version 0.2.0 receives photos directly from the Z8 over FTP. SnapBridge is not required. Failed Flickr uploads remain queued and retry while camera mode is running; the original photo is saved on the phone first.",14,muted,false));
    }

    void placeholder(String n){base(n);if(n.equals("Photos"))body.addView(txt("Direct Z8 photos are saved in your phone's Pictures/Nikon Auto Upload folder.",18,white,false));else if(n.equals("Uploads")){int count=p.getStringSet("pending_uploads",Collections.emptySet()).size();body.addView(txt(count+" photo(s) waiting to upload to Flickr.",18,white,false));}else body.addView(txt(n,18,white,false));}

    boolean hasWifiPermission(){if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void requestPerms(){ArrayList<String>x=new ArrayList<>();if(Build.VERSION.SDK_INT>=33){if(checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.NEARBY_WIFI_DEVICES);if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.POST_NOTIFICATIONS);}else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.ACCESS_FINE_LOCATION);if(!x.isEmpty())requestPermissions(x.toArray(new String[0]),7);}

    String extractOld(String text,String prefix){int x=text.indexOf(prefix);if(x<0)return "(waiting for hotspot)";x+=prefix.length();int e=text.indexOf('\n',x);return e<0?text.substring(x):text.substring(x,e);}
    void card(String a,String b,int c){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(18),dp(18),dp(18),dp(18));x.setBackgroundColor(panel);x.addView(txt(a,26,white,true));x.addView(txt(b,15,muted,false));body.addView(x,new LinearLayout.LayoutParams(-1,dp(120)));space();}
    void section(String s){space();body.addView(txt(s,18,blue,true));space();}
    void space(){Space s=new Space(this);body.addView(s,new LinearLayout.LayoutParams(1,dp(12)));}
    TextView txt(String s,int z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(null,1);t.setGravity(Gravity.CENTER_VERTICAL);t.setLineSpacing(0,1.08f);return t;}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(white);b.setBackgroundColor(Color.TRANSPARENT);return b;}
    Button big(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(8),0,dp(8));b.setLayoutParams(lp);return b;}
    EditText input(String hint,String val){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(muted);e.setTextColor(white);e.setText(val);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackgroundColor(panel);e.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(54)));return e;}
    void toast(String s){Toast.makeText(this,s==null?"Error":s,Toast.LENGTH_LONG).show();}
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
