package com.nikonautoupload;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root, body, nav;
    SharedPreferences p;
    TextView status;
    int white=Color.rgb(244,247,250), muted=Color.rgb(154,168,182), blue=Color.rgb(33,150,243), green=Color.rgb(57,208,111), panel=Color.rgb(17,24,32);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        p=getSharedPreferences("settings",MODE_PRIVATE);
        requestPerms();
        drawHome();
        handleCallback(getIntent());
    }

    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);
        setIntent(i);
        handleCallback(i);
    }

    void handleCallback(Intent i){
        Uri d=i.getData();
        if(d!=null && "nikonautoupload".equals(d.getScheme())){
            new Thread(()->{
                try{
                    new FlickrClient(this).finishAuth(d);
                    runOnUiThread(()->{toast("Flickr connected");drawSettings();});
                }catch(Exception e){
                    runOnUiThread(()->toast(e.getMessage()));
                }
            }).start();
        }
    }

    void base(String title){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(12),dp(18),dp(18));
        root.setBackgroundColor(Color.rgb(8,12,16));

        TextView t=txt(title,24,white,true);
        root.addView(t,new LinearLayout.LayoutParams(-1,dp(58)));

        body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0,0,0,dp(8));
        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(0,dp(4),0,dp(10));
        nav.setBackgroundColor(Color.rgb(10,15,20));

        String[] ns={"Home","Photos","Uploads","Settings"};
        for(String n:ns){
            Button b=button(n);
            b.setSingleLine(true);
            b.setTextSize(12);
            b.setMinHeight(0);
            b.setMinWidth(0);
            b.setPadding(dp(2),0,dp(2),0);
            b.setOnClickListener(v->{
                if(n.equals("Home")) drawHome();
                else if(n.equals("Settings")) drawSettings();
                else placeholder(n);
            });
            nav.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));
        }

        LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(-1,dp(68));
        navLp.setMargins(0,dp(4),0,dp(6));
        root.addView(nav,navLp);
        setContentView(root);

        if(Build.VERSION.SDK_INT>=20){
            root.setOnApplyWindowInsetsListener((v,insets)->{
                int bottom=insets.getSystemWindowInsetBottom();
                root.setPadding(dp(18),dp(12),dp(18),Math.max(dp(18),bottom+dp(8)));
                return insets;
            });
            root.requestApplyInsets();
        }
    }

    void drawHome(){
        base("Nikon Auto Upload");
        card("Nikon Z8","Bluetooth visibility + automatic photo workflow",green);
        status=txt("● Ready to look for Nikon Z8",16,green,true);
        body.addView(status);

        Button scan=big("Find Nikon Z8");
        scan.setOnClickListener(v->scan());
        body.addView(scan);

        Button service=big("Start Automatic Upload");
        service.setOnClickListener(v->startMonitor());
        body.addView(service);

        section("Camera setup");
        body.addView(txt("Pair the Z8 with Nikon SnapBridge first. When the camera is on the Pairing (Bluetooth) screen, this app can recognize Z8 Bluetooth names such as Z_8_3036941 and confirm that the camera is visible. SnapBridge handles Nikon's camera pairing and camera-to-phone transfer; Nikon Auto Upload takes over when the JPEG reaches your phone.",15,white,false));

        section("Workflow");
        body.addView(txt("1. Pair the Nikon Z8 to this phone with SnapBridge.\n\n2. Z8/SnapBridge automatically transfers each new JPEG to the phone.\n\n3. Nikon Auto Upload detects the new JPEG.\n\n4. The photo uploads to Flickr automatically.\n\n5. If Flickr or internet is unavailable, the original remains safely on your phone.",16,white,false));
    }

    void drawSettings(){
        base("Settings");
        section("Flickr");
        EditText k=input("Flickr API key",p.getString("flickr_key",""));
        EditText s=input("Flickr API secret",p.getString("flickr_secret",""));
        body.addView(k); body.addView(s);

        Button save=big("Save Flickr API Key");
        save.setOnClickListener(v->{
            p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();
            toast("Saved");
        });
        body.addView(save);

        Button conn=big(p.getString("access_token","").isEmpty()?"Connect Flickr":"Reconnect Flickr");
        conn.setOnClickListener(v->{
            p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();
            new Thread(()->{
                try{
                    String u=new FlickrClient(this).beginAuth();
                    startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));
                }catch(Exception e){
                    runOnUiThread(()->toast(e.getMessage()));
                }
            }).start();
        });
        body.addView(conn);
        if(!p.getString("flickr_name","").isEmpty()) body.addView(txt("Connected as: "+p.getString("flickr_name",""),15,green,true));

        section("Automatic Upload");
        Switch sw=new Switch(this);
        sw.setText("Upload new Nikon JPEGs automatically"); sw.setTextColor(white); sw.setChecked(p.getBoolean("auto_upload",true));
        sw.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("auto_upload",c).apply());
        body.addView(sw);

        Switch all=new Switch(this);
        all.setText("Upload every new JPEG (not only Nikon/SnapBridge names)"); all.setTextColor(white); all.setChecked(p.getBoolean("all_jpegs",false));
        all.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("all_jpegs",c).apply());
        body.addView(all);

        EditText tags=input("Default Flickr tags",p.getString("tags","nikon z8"));
        body.addView(tags);
        tags.setOnFocusChangeListener((v,f)->{if(!f)p.edit().putString("tags",tags.getText().toString()).apply();});

        Switch pub=new Switch(this);
        pub.setText("Upload as Public"); pub.setTextColor(white); pub.setChecked(p.getBoolean("public",true));
        pub.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("public",c).apply());
        body.addView(pub);

        section("Camera Transfer");
        body.addView(txt("Version 0.1.2 recognizes Nikon Z8 Bluetooth advertising names including Z_8-style names. SnapBridge remains responsible for Nikon pairing and the camera-to-phone transfer. This app monitors the phone for the incoming JPEG and handles the Flickr upload.",14,muted,false));
    }

    void placeholder(String n){
        base(n);
        body.addView(txt(n+" view is scaffolded for the next build.",18,white,false));
    }

    void startMonitor(){
        Intent i=new Intent(this,PhotoMonitorService.class);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        p.edit().putBoolean("monitor_running",true).apply();
        toast("Automatic upload is running");
    }

    boolean isZ8Name(String name){
        if(name==null) return false;
        String lower=name.toLowerCase(Locale.US);
        String compact=lower.replace("_","").replace("-","").replace(" ","");
        return lower.contains("nikon") || compact.contains("z8");
    }

    String scanName(ScanResult r){
        String n=null;
        try{
            if(r.getScanRecord()!=null) n=r.getScanRecord().getDeviceName();
            if((n==null || n.trim().isEmpty()) && r.getDevice()!=null) n=r.getDevice().getName();
        }catch(SecurityException ignored){}
        return n;
    }

    void scan(){
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){
            requestPerms();
            return;
        }

        BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter a=bm.getAdapter();
        if(a==null || !a.isEnabled()){
            toast("Turn Bluetooth on first");
            return;
        }

        BluetoothLeScanner sc=a.getBluetoothLeScanner();
        if(sc==null){toast("Bluetooth scanner is unavailable");return;}

        status.setText("● Looking for Nikon Z8 Bluetooth signal…");
        status.setTextColor(blue);

        ScanCallback cb=new ScanCallback(){
            @Override public void onScanResult(int type,ScanResult r){
                String n=scanName(r);
                if(isZ8Name(n)){
                    status.setText("● Camera visible: "+n+"\nPairing/transfer is handled by SnapBridge");
                    status.setTextColor(green);
                    p.edit().putString("last_camera_name",n).apply();
                    try{sc.stopScan(this);}catch(Exception ignored){}
                }
            }

            @Override public void onBatchScanResults(List<ScanResult> results){
                for(ScanResult r:results){
                    String n=scanName(r);
                    if(isZ8Name(n)){
                        status.setText("● Camera visible: "+n+"\nPairing/transfer is handled by SnapBridge");
                        status.setTextColor(green);
                        p.edit().putString("last_camera_name",n).apply();
                        try{sc.stopScan(this);}catch(Exception ignored){}
                        break;
                    }
                }
            }

            @Override public void onScanFailed(int e){
                status.setText("Bluetooth scan error "+e);
                status.setTextColor(muted);
            }
        };

        sc.startScan(cb);
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            try{sc.stopScan(cb);}catch(Exception ignored){}
            if(status.getText().toString().contains("Looking for")){
                status.setText("No Z8 Bluetooth signal found yet — put the Z8 on its Pairing (Bluetooth) screen and try again");
                status.setTextColor(muted);
            }
        },12000);
    }

    void requestPerms(){
        ArrayList<String>x=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=33){
            x.add(Manifest.permission.READ_MEDIA_IMAGES);
            x.add(Manifest.permission.POST_NOTIFICATIONS);
        }else x.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if(Build.VERSION.SDK_INT>=31){
            x.add(Manifest.permission.BLUETOOTH_SCAN);
            x.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if(!x.isEmpty()) requestPermissions(x.toArray(new String[0]),7);
    }

    void card(String a,String b,int c){
        LinearLayout x=new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(dp(18),dp(18),dp(18),dp(18));
        x.setBackgroundColor(panel);
        TextView t=txt(a,26,white,true);
        x.addView(t);
        x.addView(txt(b,15,muted,false));
        body.addView(x,new LinearLayout.LayoutParams(-1,dp(120)));
        space();
    }

    void section(String s){space();body.addView(txt(s,18,blue,true));space();}
    void space(){Space s=new Space(this);body.addView(s,new LinearLayout.LayoutParams(1,dp(12)));}

    TextView txt(String s,int z,int c,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(z);t.setTextColor(c);
        if(bold)t.setTypeface(null,1);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setLineSpacing(0,1.08f);
        return t;
    }

    Button button(String s){
        Button b=new Button(this);
        b.setText(s);b.setTextColor(white);b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    Button big(String s){
        Button b=new Button(this);
        b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(16);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));
        lp.setMargins(0,dp(8),0,dp(8));b.setLayoutParams(lp);
        return b;
    }

    EditText input(String hint,String val){
        EditText e=new EditText(this);
        e.setHint(hint);e.setHintTextColor(muted);e.setTextColor(white);e.setText(val);e.setSingleLine(true);
        e.setPadding(dp(14),0,dp(14),0);e.setBackgroundColor(panel);
        e.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(54)));
        return e;
    }

    void toast(String s){Toast.makeText(this,s==null?"Error":s,Toast.LENGTH_LONG).show();}
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
