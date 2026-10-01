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
            if(status!=null&&s!=null){
                status.setText("● "+s);
                String low=s.toLowerCase(Locale.US);
                status.setTextColor(low.contains("error")||low.contains("waiting")||low.contains("not ready")?muted:green);
            }
            if(connectionInfo!=null){
                String ip=i.getStringExtra("ip");
                String user=i.getStringExtra("ftp_user");
                String pass=i.getStringExtra("ftp_pass");
                int port=i.getIntExtra("ftp_port",2121);
                String cell=i.getStringExtra("cellular");
                StringBuilder b=new StringBuilder();
                b.append("FTP SETTINGS FOR Z8\n");
                b.append("Port: ").append(port).append("\n");
                b.append("User: ").append(user==null?"nikon":user).append("\n");
                b.append("Password: ").append(pass==null?p.getString("ftp_password",""):pass).append("\n");
                b.append("Phone Wi-Fi IP: ").append(ip==null?"waiting for Z8 Wi-Fi":ip).append("\n\n");
                b.append("Flickr internet: ").append(cell==null?"checking cellular data":cell);
                connectionInfo.setText(b.toString());
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
        for(String n:ns){
            Button b=button(n);b.setSingleLine(true);b.setTextSize(12);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(dp(2),0,dp(2),0);
            b.setOnClickListener(v->{if(n.equals("Home"))drawHome();else if(n.equals("Settings"))drawSettings();else placeholder(n);});
            nav.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(68));lp.setMargins(0,dp(4),0,dp(6));root.addView(nav,lp);setContentView(root);
        if(Build.VERSION.SDK_INT>=20){root.setOnApplyWindowInsetsListener((v,insets)->{int bottom=insets.getSystemWindowInsetBottom();root.setPadding(dp(18),dp(12),dp(18),Math.max(dp(18),bottom+dp(8)));return insets;});root.requestApplyInsets();}
    }

    void drawHome(){
        base("Nikon Auto Upload");
        card("Nikon Z8","Z8 Wi-Fi hotspot → Android → cellular → Flickr",green);
        status=txt("● Ready for Z8 Access Point mode — SnapBridge not required",16,green,true);body.addView(status);space();

        if(p.getString("access_token","").isEmpty()){
            TextView warn=txt("Connect Flickr before joining the Z8 Wi-Fi. The Z8 Wi-Fi has no internet, so Flickr authorization is easiest while your phone still has normal internet.",14,muted,true);
            body.addView(warn);space();
            Button flickr=big("OPEN FLICKR SETTINGS");flickr.setOnClickListener(v->drawSettings());body.addView(flickr);
        }else{
            body.addView(txt("Flickr connected as: "+p.getString("flickr_name","account"),14,green,true));space();
        }

        Button wifi=big("OPEN PHONE WI-FI SETTINGS");
        wifi.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));body.addView(wifi);

        Button start=big("START Z8 RECEIVER");start.setOnClickListener(v->startDirect());body.addView(start);

        section("Connection information");
        connectionInfo=txt("Start Z8 Receiver. The app will show the FTP login and confirm when cellular data is ready for Flickr.",15,white,false);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(16),dp(16),dp(16),dp(16));info.setBackgroundColor(panel);info.addView(connectionInfo);body.addView(info);

        Button stop=big("STOP Z8 RECEIVER");stop.setOnClickListener(v->{stopService(new Intent(this,DirectTransferService.class));status.setText("● Z8 receiver stopped");status.setTextColor(muted);});body.addView(stop);

        section("Z8 setup — firmware 3.01");
        body.addView(txt(
                "1. On the Z8: Network Menu → Connect to FTP server → Network settings → Create profile → Connection wizard.\n\n"+
                "2. Choose Direct connection to computer. The Z8 will display its Wi-Fi name (SSID) and password.\n\n"+
                "3. On this phone tap OPEN PHONE WI-FI SETTINGS and join the Wi-Fi name shown by the Z8. Android may warn that the network has no internet — stay connected anyway.\n\n"+
                "4. Return to the Z8. Choose FTP as the server type. For the port enter 2121. In Access Point mode Nikon connects to the phone directly, so the wizard does not require you to type a server IP address.\n\n"+
                "5. Choose Enter user ID. User: nikon. Enter the password shown in this app, then choose Home folder.\n\n"+
                "6. When the FTP profile turns green, go to Connect to FTP server → Options → Auto upload → ON. If you shoot RAW+JPEG, set the camera to upload JPEG only if that is what you want on Flickr.\n\n"+
                "7. Take a picture. The Z8 sends it to this phone by Wi-Fi. This app saves the photo first, then sends Flickr traffic over cellular data.",15,white,false));

        section("How the two connections work");
        body.addView(txt("The Z8 is the Wi-Fi hotspot only for the local photo transfer. Nikon Auto Upload separately asks Android for a cellular network and uses that cellular connection for Flickr. If cellular is temporarily unavailable, the photo stays saved on the phone and remains queued for retry.",14,muted,false));
    }

    void startDirect(){
        Intent i=new Intent(this,DirectTransferService.class).putExtra("mode","z8ap");
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        if(status!=null){status.setText("● Starting Z8 receiver and cellular upload path…");status.setTextColor(blue);}
    }

    void drawSettings(){
        base("Settings");section("Flickr");
        EditText k=input("Flickr API key",p.getString("flickr_key",""));EditText s=input("Flickr API secret",p.getString("flickr_secret",""));body.addView(k);body.addView(s);
        Button save=big("SAVE FLICKR API KEY");save.setOnClickListener(v->{p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();toast("Saved");});body.addView(save);
        Button conn=big(p.getString("access_token","").isEmpty()?"CONNECT FLICKR":"RECONNECT FLICKR");
        conn.setOnClickListener(v->{p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();new Thread(()->{try{String u=new FlickrClient(this).beginAuth();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}}).start();});body.addView(conn);
        if(!p.getString("flickr_name","").isEmpty())body.addView(txt("Connected as: "+p.getString("flickr_name",""),15,green,true));
        section("Upload defaults");
        EditText tags=input("Default Flickr tags",p.getString("tags","nikon z8"));body.addView(tags);tags.setOnFocusChangeListener((v,f)->{if(!f)p.edit().putString("tags",tags.getText().toString()).apply();});
        Switch pub=new Switch(this);pub.setText("Upload as Public");pub.setTextColor(white);pub.setChecked(p.getBoolean("public",true));pub.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("public",c).apply());body.addView(pub);
        section("Direct transfer");
        body.addView(txt("Version 0.2.1 is designed for Nikon Z8 Access Point FTP mode. The camera creates the Wi-Fi network, the phone joins it for photo transfer, and Flickr uploads are explicitly routed over the phone's cellular connection. SnapBridge is not required.",14,muted,false));
    }

    void placeholder(String n){
        base(n);
        if(n.equals("Photos"))body.addView(txt("Direct Z8 photos are saved in Pictures/Nikon Auto Upload on this phone.",18,white,false));
        else if(n.equals("Uploads")){int count=p.getStringSet("pending_uploads",Collections.emptySet()).size();body.addView(txt(count+" photo(s) waiting to upload to Flickr.",18,white,false));}
        else body.addView(txt(n,18,white,false));
    }

    void requestPerms(){
        ArrayList<String>x=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!x.isEmpty())requestPermissions(x.toArray(new String[0]),7);
    }

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
