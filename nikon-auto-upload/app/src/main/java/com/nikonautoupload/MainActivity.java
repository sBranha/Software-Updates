package com.nikonautoupload;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root,body,nav;
    SharedPreferences p;
    TextView status,connectionInfo;
    boolean receiverRegistered=false;
    int white=Color.rgb(244,247,250),muted=Color.rgb(154,168,182),blue=Color.rgb(33,150,243),green=Color.rgb(57,208,111),panel=Color.rgb(17,24,32),amber=Color.rgb(255,193,7);

    private final BroadcastReceiver directReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(!DirectTransferService.ACTION_STATUS.equals(i.getAction()))return;
            String s=i.getStringExtra("status");
            if(s!=null)p.edit().putString("last_status",s).apply();
            refreshLiveViews(s);
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        p=getSharedPreferences("settings",MODE_PRIVATE);
        migrateStableSettings();
        requestPerms();
        registerDirectReceiver();
        drawHome();
        handleCallback(getIntent());
        if(p.getBoolean("auto_start_receiver",true))startDirect(false);
    }

    private void migrateStableSettings(){
        SharedPreferences.Editor e=p.edit();
        if(!p.getBoolean("ftp_fixed_password_v031",false)){
            e.putString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD);
            e.putBoolean("ftp_fixed_password_v031",true);
        }
        if(!p.contains("flickr_upload_enabled"))e.putBoolean("flickr_upload_enabled",true);
        e.apply();
    }

    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleCallback(i);}
    @Override protected void onResume(){super.onResume();if(status!=null)refreshLiveViews(null);}
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
            b.setOnClickListener(v->{if(n.equals("Home"))drawHome();else if(n.equals("Photos"))drawPhotos();else if(n.equals("Uploads"))drawUploads();else drawSettings();});
            nav.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(68));lp.setMargins(0,dp(4),0,dp(6));root.addView(nav,lp);setContentView(root);
        if(Build.VERSION.SDK_INT>=20){root.setOnApplyWindowInsetsListener((v,insets)->{int bottom=insets.getSystemWindowInsetBottom();root.setPadding(dp(18),dp(12),dp(18),Math.max(dp(18),bottom+dp(8)));return insets;});root.requestApplyInsets();}
    }

    void drawHome(){
        base("Nikon Auto Upload");
        boolean flickrOn=p.getBoolean("flickr_upload_enabled",true);
        card("Nikon Z8",flickrOn?"Z8 Wi-Fi → phone → 5G/LTE → Flickr":"Z8 Wi-Fi → phone  •  Flickr OFF",green);
        status=txt("● "+p.getString("last_status","Ready — receiver starts automatically"),16,statusColor(p.getString("last_status","")),true);body.addView(status);space();

        LinearLayout mode=panelBox();
        Switch flickr=new Switch(this);flickr.setText("Upload new photos to Flickr");flickr.setTextColor(white);flickr.setTextSize(16);flickr.setChecked(flickrOn);
        flickr.setOnCheckedChangeListener((b,c)->{p.edit().putBoolean("flickr_upload_enabled",c).apply();startDirect(false);toast(c?"Flickr uploads ON":"Flickr uploads OFF — photos still save to phone");drawHome();});
        mode.addView(flickr);mode.addView(txt("Photos are always saved to this phone. Turn this OFF any time you want phone-only transfer.",13,muted,false));body.addView(mode);space();

        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statBox("RECEIVED",String.valueOf(p.getInt("total_received",0))),new LinearLayout.LayoutParams(0,dp(76),1));
        stats.addView(statBox("UPLOADED",String.valueOf(p.getInt("total_uploaded",0))),new LinearLayout.LayoutParams(0,dp(76),1));
        stats.addView(statBox("PENDING",String.valueOf(p.getStringSet("pending_uploads",Collections.emptySet()).size())),new LinearLayout.LayoutParams(0,dp(76),1));
        body.addView(stats);space();

        Button wifi=big("OPEN PHONE WI-FI SETTINGS");wifi.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));body.addView(wifi);
        Button start=big("START / RESTART Z8 RECEIVER");start.setOnClickListener(v->startDirect(true));body.addView(start);

        section("Live connection");
        connectionInfo=txt("",15,white,false);LinearLayout info=panelBox();info.addView(connectionInfo);body.addView(info);refreshLiveViews(null);
        if(flickrOn&&p.getString("access_token","").isEmpty()){space();body.addView(txt("Flickr upload is ON, but Flickr is not connected. Open Settings to connect it. Photos will still save to the phone.",14,amber,true));}

        section("Game-day use");
        body.addView(txt("1. Turn on Connect to FTP server on the Z8 and use your working WLAN2 profile.\n\n2. Join the Z8 Wi-Fi on the phone and stay connected even if Android says there is no internet.\n\n3. Open this app. The receiver starts automatically.\n\n4. Shoot. Every JPEG is saved in Pictures/Nikon Auto Upload. If Flickr Upload is ON, it also uploads over cellular data. If Flickr Upload is OFF, nothing is sent to Flickr.",15,white,false));
        Button stop=big("STOP Z8 RECEIVER");stop.setOnClickListener(v->{stopService(new Intent(this,DirectTransferService.class));p.edit().putBoolean("receiver_running",false).putString("last_status","Receiver stopped").apply();refreshLiveViews("Receiver stopped");});body.addView(stop);
    }

    void refreshLiveViews(String newest){
        if(status!=null){String s=newest==null?p.getString("last_status","Ready"):newest;status.setText("● "+s);status.setTextColor(statusColor(s));}
        if(connectionInfo!=null){
            String ip=p.getString("last_ip","waiting for Z8 Wi-Fi"),ftp=p.getString("ftp_state","Waiting for Z8"),cell=p.getString("cellular_state","Checking cellular data"),flickr=p.getString("flickr_name","");
            boolean on=p.getBoolean("flickr_upload_enabled",true);int pending=p.getStringSet("pending_uploads",Collections.emptySet()).size();
            StringBuilder b=new StringBuilder();
            b.append(p.getBoolean("receiver_running",false)?"✓ RECEIVER RUNNING":"○ RECEIVER STOPPED").append("\n");
            b.append("Save to phone: ALWAYS ON\n");b.append("Wi-Fi IP: ").append(ip).append("\n");b.append("Z8 FTP: ").append(ftp).append("\n");b.append("Flickr uploads: ").append(on?"ON":"OFF — phone only").append("\n");
            if(on){b.append("Cellular: ").append(cell).append("\n");b.append("Flickr account: ").append(flickr.isEmpty()?"Not connected":flickr).append("\n");}
            b.append("Queue: ").append(pending).append(" pending");connectionInfo.setText(b.toString());
        }
    }

    int statusColor(String s){String x=s==null?"":s.toLowerCase(Locale.US);if(x.contains("error")||x.contains("rejected")||x.contains("failed"))return amber;if(x.contains("waiting")||x.contains("stopped")||x.contains("not ready"))return muted;return green;}

    void startDirect(boolean toastIt){
        Intent i=new Intent(this,DirectTransferService.class).putExtra("mode","z8ap");if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        p.edit().putBoolean("receiver_running",true).apply();if(status!=null){status.setText("● Starting Z8 receiver…");status.setTextColor(blue);}if(toastIt)toast("Z8 receiver started");
    }

    void drawPhotos(){
        base("Photos");
        section("Photos received from Z8");
        body.addView(txt("Tap any picture to open it full size.",14,muted,false));

        ArrayList<Uri> photos=new ArrayList<>();
        try(Cursor c=getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.RELATIVE_PATH},
                Build.VERSION.SDK_INT>=29?MediaStore.Images.Media.RELATIVE_PATH+" LIKE ?":null,
                Build.VERSION.SDK_INT>=29?new String[]{"Pictures/Nikon Auto Upload%"}:null,
                MediaStore.Images.Media.DATE_ADDED+" DESC")){
            int n=0;
            while(c!=null&&c.moveToNext()&&n<30){
                long id=c.getLong(0);
                photos.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id));
                n++;
            }
        }catch(Exception e){
            body.addView(txt("Could not load Nikon photos: "+e.getMessage(),14,amber,false));
            return;
        }

        if(photos.isEmpty()){
            space();body.addView(txt("No Nikon photos saved on this phone yet.",16,muted,true));return;
        }

        body.addView(txt(photos.size()+" most recent photo(s)",16,green,true));space();
        LinearLayout row=null;
        for(int i=0;i<photos.size();i++){
            if(i%2==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER);body.addView(row,new LinearLayout.LayoutParams(-1,dp(178)));}
            Uri uri=photos.get(i);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(panel);image.setContentDescription("Nikon photo");
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,dp(170),1);ip.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(image,ip);
            try{
                Bitmap thumb=getContentResolver().loadThumbnail(uri,new Size(900,650),null);
                image.setImageBitmap(thumb);
            }catch(Exception e){image.setImageResource(R.drawable.ic_camera);image.setPadding(dp(48),dp(48),dp(48),dp(48));}
            image.setOnClickListener(v->{
                try{Intent open=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(open);}catch(Exception ex){toast("Could not open photo");}
            });
            if(i==photos.size()-1&&photos.size()%2==1){Space filler=new Space(this);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(170),1);fp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(filler,fp);}
        }
    }

    void drawUploads(){
        base("Uploads");boolean on=p.getBoolean("flickr_upload_enabled",true);int pending=p.getStringSet("pending_uploads",Collections.emptySet()).size();section("Flickr queue");
        body.addView(txt(on?(pending==0?"✓ Flickr is ON — all caught up":pending+" photo(s) waiting for Flickr"):"Flickr uploads are OFF",20,on?(pending==0?green:amber):muted,true));
        body.addView(txt(on?"New photos will upload automatically over cellular data.":"New camera photos still save to your phone, but are not added to Flickr while this is OFF.",14,muted,false));
        space();body.addView(txt("Last received: "+p.getString("last_received_name","None yet")+"\n"+formatPrefTime("last_received_time")+"\n\nLast uploaded: "+p.getString("last_uploaded_name","None yet")+"\n"+formatPrefTime("last_uploaded_time"),15,white,false));
        Button retry=big(on?"RETRY PENDING UPLOADS NOW":"FLICKR UPLOADS ARE OFF");retry.setEnabled(on);retry.setOnClickListener(v->{startDirect(false);toast("Retry requested");});body.addView(retry);section("Recent activity");String log=p.getString("event_log","");body.addView(txt(log.isEmpty()?"No activity recorded yet.":log,13,log.isEmpty()?muted:white,false));
    }

    void drawSettings(){
        base("Settings");section("Automatic operation");
        Switch auto=new Switch(this);auto.setText("Start Z8 receiver when app opens");auto.setTextColor(white);auto.setChecked(p.getBoolean("auto_start_receiver",true));auto.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("auto_start_receiver",c).apply());body.addView(auto);
        Switch upload=new Switch(this);upload.setText("Automatically upload new photos to Flickr");upload.setTextColor(white);upload.setChecked(p.getBoolean("flickr_upload_enabled",true));upload.setOnCheckedChangeListener((b,c)->{p.edit().putBoolean("flickr_upload_enabled",c).apply();startDirect(false);toast(c?"Flickr uploads ON":"Phone-only mode ON");});body.addView(upload);
        body.addView(txt("Save-to-phone is always enabled. Turning Flickr OFF does not stop camera transfers or remove photos from the phone.",13,muted,false));

        section("Flickr account");
        EditText k=input("Flickr API key",p.getString("flickr_key",""));EditText s=input("Flickr API secret",p.getString("flickr_secret",""));s.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);body.addView(k);body.addView(s);
        Button save=big("SAVE FLICKR API KEY");save.setOnClickListener(v->{p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();toast("Saved");});body.addView(save);
        Button conn=big(p.getString("access_token","").isEmpty()?"CONNECT FLICKR":"RECONNECT FLICKR");conn.setOnClickListener(v->{p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();new Thread(()->{try{String u=new FlickrClient(this).beginAuth();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}}).start();});body.addView(conn);if(!p.getString("flickr_name","").isEmpty())body.addView(txt("Connected as: "+p.getString("flickr_name",""),15,green,true));

        section("Upload defaults");
        EditText tags=input("Default Flickr tags",p.getString("tags","nikon z8"));body.addView(tags);tags.setOnFocusChangeListener((v,f)->{if(!f)p.edit().putString("tags",tags.getText().toString()).apply();});
        Switch pub=new Switch(this);pub.setText("Upload as Public");pub.setTextColor(white);pub.setChecked(p.getBoolean("public",true));pub.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("public",c).apply());body.addView(pub);

        section("Permanent Z8 FTP login");
        body.addView(txt("Use these same settings in the Nikon Z8. They no longer change automatically.",13,muted,false));
        body.addView(txt("Port: 2121\nUser: nikon\nPassive ports: 32768–61000",15,white,false));
        EditText ftpPass=input("FTP password",p.getString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD));ftpPass.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);body.addView(ftpPass);
        Button ftpSave=big("SAVE PERMANENT FTP PASSWORD");ftpSave.setOnClickListener(v->{String x=ftpPass.getText().toString().trim();if(x.length()<4){toast("Use at least 4 digits");return;}p.edit().putString("ftp_password",x).putBoolean("ftp_fixed_password_v031",true).apply();stopService(new Intent(this,DirectTransferService.class));startDirect(false);toast("FTP password saved. Only change the Z8 if you deliberately change this value.");});body.addView(ftpSave);
        Button reset=big("RESET PASSWORD TO 47250558");reset.setOnClickListener(v->{p.edit().putString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD).putBoolean("ftp_fixed_password_v031",true).apply();ftpPass.setText(DirectTransferService.DEFAULT_FTP_PASSWORD);stopService(new Intent(this,DirectTransferService.class));startDirect(false);toast("Permanent FTP password reset to 47250558");});body.addView(reset);
        body.addView(txt("Version 0.3.3 adds the in-app photo gallery with real picture thumbnails while keeping permanent FTP credentials, phone-only mode and Flickr upload controls.",13,muted,false));
    }

    String formatPrefTime(String key){long t=p.getLong(key,0);return t==0?"":formatTime(t);}
    String formatTime(long t){return new SimpleDateFormat("MMM d, yyyy  h:mm:ss a",Locale.US).format(new Date(t));}
    LinearLayout statBox(String label,String value){LinearLayout x=panelBox();x.setGravity(Gravity.CENTER);x.addView(txt(value,23,green,true));x.addView(txt(label,10,muted,true));return x;}
    LinearLayout panelBox(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(12),dp(10),dp(12),dp(10));x.setBackgroundColor(panel);return x;}
    void requestPerms(){ArrayList<String>x=new ArrayList<>();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.POST_NOTIFICATIONS);if(!x.isEmpty())requestPermissions(x.toArray(new String[0]),7);}
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
