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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root,body,nav;
    SharedPreferences p;
    TextView status,connectionInfo;
    boolean receiverRegistered=false,photoSelectMode=false;
    String photoFilter="All";
    final Set<String> selectedPhotos=new HashSet<>();
    static final int REQ_DELETE_MEDIA=91;
    int white=Color.rgb(244,247,250),muted=Color.rgb(154,168,182),blue=Color.rgb(33,150,243),green=Color.rgb(57,208,111),panel=Color.rgb(17,24,32),amber=Color.rgb(255,193,7),red=Color.rgb(239,83,80);

    static class PhotoItem { Uri uri; long added; long size; PhotoItem(Uri u,long a,long s){uri=u;added=a;size=s;} }
    static class Health { String title,detail; int color; Health(String t,String d,int c){title=t;detail=d;color=c;} }

    private final BroadcastReceiver directReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){if(!DirectTransferService.ACTION_STATUS.equals(i.getAction()))return;String s=i.getStringExtra("status");if(s!=null)p.edit().putString("last_status",s).apply();refreshLiveViews(s);}
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);p=getSharedPreferences("settings",MODE_PRIVATE);migrateStableSettings();applyKeepAwake();requestPerms();registerDirectReceiver();drawHome();handleCallback(getIntent());if(p.getBoolean("auto_start_receiver",true))startDirect(false);
    }
    private void migrateStableSettings(){
        SharedPreferences.Editor e=p.edit();
        if(!p.getBoolean("ftp_fixed_password_v031",false)){e.putString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD);e.putBoolean("ftp_fixed_password_v031",true);}
        if(!p.contains("flickr_upload_enabled"))e.putBoolean("flickr_upload_enabled",true);
        if(!p.contains("daily_albums_enabled"))e.putBoolean("daily_albums_enabled",true);
        if(!p.contains("album_max_photos"))e.putInt("album_max_photos",999);
        if(!p.contains("album_suffix"))e.putString("album_suffix","");
        if(!p.contains("auto_clear_completed"))e.putBoolean("auto_clear_completed",true);
        if(!p.contains("keep_screen_awake"))e.putBoolean("keep_screen_awake",false);
        if(!p.contains("duplicate_protection"))e.putBoolean("duplicate_protection",true);
        if(!p.contains("auto_delete_after_upload"))e.putBoolean("auto_delete_after_upload",false);
        if(!p.contains("cleanup_keep_days"))e.putInt("cleanup_keep_days",0);
        if(!p.contains("receiver_paused"))e.putBoolean("receiver_paused",false);
        e.apply();
    }

    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleCallback(i);}
    @Override protected void onResume(){super.onResume();applyKeepAwake();if(status!=null)refreshLiveViews(null);}
    @Override protected void onStop(){super.onStop();if(!isChangingConfigurations()&&p.getBoolean("auto_clear_completed",true))p.edit().remove("completed_upload_log").apply();}
    @Override protected void onDestroy(){if(receiverRegistered)unregisterReceiver(directReceiver);super.onDestroy();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_DELETE_MEDIA){selectedPhotos.clear();photoSelectMode=false;drawPhotos();}}

    void registerDirectReceiver(){IntentFilter f=new IntentFilter(DirectTransferService.ACTION_STATUS);if(Build.VERSION.SDK_INT>=33)registerReceiver(directReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(directReceiver,f);receiverRegistered=true;}
    void handleCallback(Intent i){Uri d=i.getData();if(d!=null&&"nikonautoupload".equals(d.getScheme()))new Thread(()->{try{new FlickrClient(this).finishAuth(d);runOnUiThread(()->{toast("Flickr connected");drawSettings();});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}}).start();}

    void base(String title){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(12),dp(18),dp(18));root.setBackgroundColor(Color.rgb(8,12,16));
        root.addView(txt(title,24,white,true),new LinearLayout.LayoutParams(-1,dp(58)));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,0,0,dp(8));ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);nav.setPadding(0,dp(4),0,dp(10));nav.setBackgroundColor(Color.rgb(10,15,20));
        String[] ns={"Home","Photos","Uploads","Settings"};for(String n:ns){Button b=button(n);b.setSingleLine(true);b.setTextSize(12);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(dp(2),0,dp(2),0);b.setOnClickListener(v->{if(n.equals("Home"))drawHome();else if(n.equals("Photos"))drawPhotos();else if(n.equals("Uploads"))drawUploads();else drawSettings();});nav.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(68));lp.setMargins(0,dp(4),0,dp(6));root.addView(nav,lp);setContentView(root);
        if(Build.VERSION.SDK_INT>=20){root.setOnApplyWindowInsetsListener((v,insets)->{int bottom=insets.getSystemWindowInsetBottom();root.setPadding(dp(18),dp(12),dp(18),Math.max(dp(18),bottom+dp(8)));return insets;});root.requestApplyInsets();}
    }

    void drawHome(){
        base("Nikon Auto Upload");Health h=health();LinearLayout health=panelBox();health.addView(txt(h.title,24,h.color,true));health.addView(txt(h.detail,13,muted,false));body.addView(health);space();
        boolean flickrOn=p.getBoolean("flickr_upload_enabled",true),isPublic=p.getBoolean("public",true);String privacy=isPublic?"PUBLIC":"PRIVATE";
        card("Nikon Z8",flickrOn?"Z8 direct Wi-Fi → phone → 5G/LTE → Flickr • "+privacy:"Z8 direct Wi-Fi → phone • Flickr OFF",green);
        body.addView(txt(gameStatusLine(),14,white,true));space();

        if(p.getBoolean("session_active",false)){
            LinearLayout game=panelBox();game.addView(txt("GAME ACTIVE",13,green,true));game.addView(txt(p.getString("session_name","Game"),20,white,true));game.addView(txt("Received "+p.getInt("session_received",0)+" • Uploaded "+p.getInt("session_uploaded",0)+" • Albums "+p.getInt("session_albums",0),13,muted,false));Button end=big("END GAME");end.setOnClickListener(v->endGame());game.addView(end);body.addView(game);space();
        }else{Button game=big("START GAME / SESSION");game.setOnClickListener(v->startGameDialog());body.addView(game);}

        LinearLayout mode=panelBox();Switch flickr=new Switch(this);flickr.setText("Upload new photos to Flickr");flickr.setTextColor(white);flickr.setTextSize(16);flickr.setChecked(flickrOn);flickr.setOnCheckedChangeListener((b,c)->{p.edit().putBoolean("flickr_upload_enabled",c).apply();startDirect(false);toast(c?"Flickr uploads ON":"Flickr paused — Z8 photos still save to phone");drawHome();});mode.addView(flickr);mode.addView(txt("Turn Flickr OFF to pause internet uploads without stopping camera-to-phone transfer.",13,muted,false));body.addView(mode);space();

        boolean paused=p.getBoolean("receiver_paused",false);Button receive=big(paused?"RESUME Z8 RECEIVING":"PAUSE Z8 RECEIVING");receive.setOnClickListener(v->{sendServiceCommand(paused?"resume_receiver":"pause_receiver");p.edit().putBoolean("receiver_paused",!paused).apply();toast(paused?"Z8 receiving resumed":"Z8 receiving paused");drawHome();});body.addView(receive);

        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);stats.addView(statBox("RECEIVED",String.valueOf(p.getInt("total_received",0))),new LinearLayout.LayoutParams(0,dp(76),1));stats.addView(statBox("UPLOADED",String.valueOf(p.getInt("total_uploaded",0))),new LinearLayout.LayoutParams(0,dp(76),1));stats.addView(statBox("PENDING",String.valueOf(p.getStringSet("pending_uploads",Collections.emptySet()).size())),new LinearLayout.LayoutParams(0,dp(76),1));body.addView(stats);space();

        LinearLayout storage=panelBox();storage.addView(txt("PHONE STORAGE",13,blue,true));storage.addView(txt(storageSummary(),16,white,true));storage.addView(txt("Nikon folder: "+formatBytes(nikonFolderBytes())+" • Phone battery: "+phoneBattery()+"%",13,muted,false));body.addView(storage);space();

        status=txt("● "+p.getString("last_status","Ready — receiver starts automatically"),16,statusColor(p.getString("last_status","")),true);body.addView(status);space();
        Button wifi=big("OPEN PHONE WI-FI SETTINGS");wifi.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));body.addView(wifi);Button start=big("START / RESTART Z8 RECEIVER");start.setOnClickListener(v->startDirect(true));body.addView(start);
        section("Live connection");connectionInfo=txt("",15,white,false);LinearLayout info=panelBox();info.addView(connectionInfo);body.addView(info);refreshLiveViews(null);
        if(flickrOn&&p.getString("access_token","").isEmpty()){space();body.addView(txt("Flickr is ON but not connected. Photos will still save to the phone until Flickr is connected.",14,amber,true));}
    }

    Health health(){
        boolean running=p.getBoolean("receiver_running",false),paused=p.getBoolean("receiver_paused",false),flickr=p.getBoolean("flickr_upload_enabled",true);long free=freeBytes();int battery=phoneBattery();
        if(paused)return new Health("RECEIVING PAUSED","Tap Resume Z8 Receiving before you start shooting.",amber);
        if(!running)return new Health("NEEDS ATTENTION","The Z8 receiver is not running.",red);
        if(free<1024L*1024L*1024L)return new Health("LOW PHONE STORAGE","Less than 1 GB is free. Clear uploaded phone copies before a long game.",amber);
        if(battery>=0&&battery<15)return new Health("LOW PHONE BATTERY","Charge the phone before a long shoot.",amber);
        if(flickr&&p.getString("access_token","").isEmpty())return new Health("FLICKR NOT CONNECTED","Camera transfer is ready, but Flickr needs to be connected in Settings.",amber);
        String ftp=p.getString("ftp_state","Waiting for Z8").toLowerCase(Locale.US),cell=p.getString("cellular_state","").toLowerCase(Locale.US);
        if(flickr&&!cell.contains("ready"))return new Health("GETTING READY","Receiver is running. Waiting for the cellular Flickr path.",blue);
        if(ftp.contains("connected")||ftp.contains("logged")||ftp.contains("receiv")||ftp.contains("transfer")||ftp.contains("photo"))return new Health("READY TO SHOOT","Z8 path, phone storage and Flickr path look ready.",green);
        return new Health("READY — WAITING FOR Z8","Open WLAN2 / Connect to FTP server on the Z8. No outside Wi-Fi is required.",green);
    }

    String gameStatusLine(){
        String ftp=p.getString("ftp_state","Waiting for Z8").toLowerCase(Locale.US);String cam=(ftp.contains("connected")||ftp.contains("logged")||ftp.contains("receiv")||ftp.contains("transfer")||ftp.contains("photo"))?"Z8 CONNECTED":"WAITING FOR Z8";
        boolean on=p.getBoolean("flickr_upload_enabled",true);String cell=p.getString("cellular_state","").toLowerCase(Locale.US).contains("ready")?"5G/LTE READY":"CELLULAR CHECK";String privacy=p.getBoolean("public",true)?"PUBLIC":"PRIVATE";String album=p.getString("last_album_name","");
        return cam+" • "+(on?cell:"FLICKR PAUSED")+" • "+(on?("FLICKR "+privacy):"PHONE ONLY")+(album.isEmpty()?"":" • "+album+" "+p.getInt("last_album_count",0)+"/"+p.getInt("album_max_photos",999));
    }

    void refreshLiveViews(String newest){
        if(status!=null){String s=newest==null?p.getString("last_status","Ready"):newest;status.setText("● "+s);status.setTextColor(statusColor(s));}
        if(connectionInfo!=null){String ip=p.getString("last_ip","waiting for Z8 Wi-Fi"),ftp=p.getString("ftp_state","Waiting for Z8"),cell=p.getString("cellular_state","Checking cellular data"),flickr=p.getString("flickr_name","");boolean on=p.getBoolean("flickr_upload_enabled",true),albums=p.getBoolean("daily_albums_enabled",true),isPublic=p.getBoolean("public",true);int pending=p.getStringSet("pending_uploads",Collections.emptySet()).size(),pendingAlbums=p.getStringSet("pending_album_assignments",Collections.emptySet()).size();StringBuilder b=new StringBuilder();b.append(p.getBoolean("receiver_running",false)?"✓ RECEIVER RUNNING":"○ RECEIVER STOPPED").append('\n');b.append("Receiving: ").append(p.getBoolean("receiver_paused",false)?"PAUSED":"ON").append('\n');b.append("Save to phone: ALWAYS ON\nWi-Fi IP: ").append(ip).append("\nZ8 FTP: ").append(ftp).append("\nFlickr: ").append(on?("ON • "+(isPublic?"PUBLIC":"PRIVATE")):"OFF — phone only").append('\n');if(on){b.append("Cellular: ").append(cell).append("\nFlickr account: ").append(flickr.isEmpty()?"Not connected":flickr).append("\nDaily albums: ").append(albums?("ON • max "+p.getInt("album_max_photos",999)):"OFF").append('\n');String album=p.getString("last_album_name","");if(!album.isEmpty())b.append("Current album: ").append(album).append(" (").append(p.getInt("last_album_count",0)).append('/').append(p.getInt("album_max_photos",999)).append(")\n");}b.append("Upload queue: ").append(pending);if(pendingAlbums>0)b.append(" • Album filing: ").append(pendingAlbums);connectionInfo.setText(b.toString());}
    }

    void startGameDialog(){
        EditText name=input("Game / session name (optional)","");new AlertDialog.Builder(this).setTitle("Start Game").setMessage("If you enter a name, it becomes the Flickr album suffix for photos from this game.").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Start",(d,w)->{String n=clean(name.getText().toString());SharedPreferences.Editor e=p.edit().putBoolean("session_active",true).putString("session_name",n).putLong("session_start",System.currentTimeMillis()).putInt("session_received",0).putInt("session_uploaded",0).putInt("session_albums",0).putBoolean("receiver_paused",false).putStringSet("current_session_uris",new HashSet<>()).putStringSet("session_album_names",new HashSet<>());e.remove("flickr_album_cache_base").remove("flickr_album_cache_id").remove("flickr_album_cache_title").remove("flickr_album_cache_count").apply();sendServiceCommand("resume_receiver");startDirect(false);toast("Game started");drawHome();}).show();
    }

    void endGame(){
        int received=p.getInt("session_received",0),uploaded=p.getInt("session_uploaded",0),albums=p.getInt("session_albums",0),pending=p.getStringSet("pending_uploads",Collections.emptySet()).size();String name=p.getString("session_name","");p.edit().putBoolean("session_active",false).putLong("session_end",System.currentTimeMillis()).putBoolean("receiver_paused",true).apply();sendServiceCommand("pause_receiver");
        new AlertDialog.Builder(this).setTitle("Game ended"+(name.isEmpty()?"":" — "+name)).setMessage("Received: "+received+"\nUploaded and verified: "+uploaded+"\nFlickr albums: "+albums+"\nStill pending: "+pending+"\n\nThe Z8 receiver is paused. Pending Flickr work can continue in the background.").setPositiveButton("OK",(d,w)->drawHome()).show();
    }

    int statusColor(String s){String x=s==null?"":s.toLowerCase(Locale.US);if(x.contains("error")||x.contains("rejected")||x.contains("failed"))return amber;if(x.contains("waiting")||x.contains("stopped")||x.contains("not ready")||x.contains("paused"))return muted;return green;}
    void startDirect(boolean toastIt){Intent i=new Intent(this,DirectTransferService.class).putExtra("mode","z8ap");if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);p.edit().putBoolean("receiver_running",true).apply();if(status!=null){status.setText("● Starting Z8 receiver…");status.setTextColor(blue);}if(toastIt)toast("Z8 receiver started");}
    void sendServiceCommand(String cmd){Intent i=new Intent(this,DirectTransferService.class).putExtra("mode","z8ap").putExtra("command",cmd);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}

    void drawPhotos(){
        base("Photos");section("Photos received from Z8");body.addView(txt("Real pictures only — no filenames. Tap to view. Use Select mode to delete from the phone inside this app.",14,muted,false));
        String[] filters={"All","Today","This game","Uploaded","Phone only","Favorites","Failed"};Spinner spin=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filters);spin.setAdapter(a);int pos=Arrays.asList(filters).indexOf(photoFilter);spin.setSelection(Math.max(0,pos),false);spin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){String f=filters[position];if(!f.equals(photoFilter)){photoFilter=f;selectedPhotos.clear();drawPhotos();}}});body.addView(spin,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);Button select=button(photoSelectMode?"CANCEL":"SELECT / DELETE");select.setTextColor(blue);select.setOnClickListener(v->{photoSelectMode=!photoSelectMode;selectedPhotos.clear();drawPhotos();});controls.addView(select,new LinearLayout.LayoutParams(0,dp(48),1));Button uploaded=button("DELETE UPLOADED");uploaded.setTextColor(amber);uploaded.setOnClickListener(v->confirmDeleteUploaded());controls.addView(uploaded,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(controls);
        if(photoSelectMode){Button del=big("DELETE SELECTED FROM PHONE ("+selectedPhotos.size()+")");del.setBackgroundTintList(android.content.res.ColorStateList.valueOf(red));del.setOnClickListener(v->{if(selectedPhotos.isEmpty())toast("Select at least one picture");else confirmDelete(new ArrayList<>(selectedPhotos));});body.addView(del);}
        ArrayList<PhotoItem> items=loadPhotos(photoFilter);if(items.isEmpty()){space();body.addView(txt("No photos match this filter.",16,muted,true));return;}body.addView(txt(items.size()+" photo(s)",16,green,true));space();
        ArrayList<Uri> viewerUris=new ArrayList<>();for(PhotoItem pi:items)viewerUris.add(pi.uri);LinearLayout row=null;
        for(int i=0;i<items.size();i++){
            if(i%2==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER);body.addView(row,new LinearLayout.LayoutParams(-1,dp(photoSelectMode?218:178)));}
            Uri uri=items.get(i).uri;final int index=i;LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setPadding(dp(3),dp(3),dp(3),dp(3));row.addView(cell,new LinearLayout.LayoutParams(0,-1,1));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(panel);cell.addView(image,new LinearLayout.LayoutParams(-1,dp(170)));
            try{Bitmap thumb=getContentResolver().loadThumbnail(uri,new Size(900,650),null);image.setImageBitmap(thumb);}catch(Exception e){image.setImageResource(R.drawable.ic_camera);image.setPadding(dp(48),dp(48),dp(48),dp(48));}
            final CheckBox cb=new CheckBox(this);if(photoSelectMode){cb.setText("Select");cb.setTextColor(white);cb.setChecked(selectedPhotos.contains(uri.toString()));cb.setOnCheckedChangeListener((b,c)->{if(c)selectedPhotos.add(uri.toString());else selectedPhotos.remove(uri.toString());});cell.addView(cb,new LinearLayout.LayoutParams(-1,dp(40)));}
            image.setOnClickListener(v->{if(photoSelectMode){cb.setChecked(!cb.isChecked());}else openPhotoViewer(viewerUris,index);});
            if(i==items.size()-1&&items.size()%2==1){Space filler=new Space(this);row.addView(filler,new LinearLayout.LayoutParams(0,-1,1));}
        }
    }

    ArrayList<PhotoItem> loadPhotos(String filter){
        ArrayList<PhotoItem> out=new ArrayList<>();Set<String> uploaded=p.getStringSet("uploaded_uris",Collections.emptySet()),phone=p.getStringSet("phone_only_uris",Collections.emptySet()),fav=p.getStringSet("favorite_uris",Collections.emptySet()),failed=p.getStringSet("failed_upload_uris",Collections.emptySet()),session=p.getStringSet("current_session_uris",Collections.emptySet());Calendar cal=Calendar.getInstance();cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);long today=cal.getTimeInMillis();
        try(Cursor c=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.SIZE,MediaStore.Images.Media.RELATIVE_PATH},Build.VERSION.SDK_INT>=29?MediaStore.Images.Media.RELATIVE_PATH+" LIKE ?":null,Build.VERSION.SDK_INT>=29?new String[]{"Pictures/Nikon Auto Upload%"}:null,MediaStore.Images.Media.DATE_ADDED+" DESC")){
            while(c!=null&&c.moveToNext()&&out.size()<300){Uri u=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0));String s=u.toString();long added=c.getLong(1)*1000L,size=c.getLong(2);boolean keep="All".equals(filter)||("Today".equals(filter)&&added>=today)||("This game".equals(filter)&&session.contains(s))||("Uploaded".equals(filter)&&uploaded.contains(s))||("Phone only".equals(filter)&&phone.contains(s))||("Favorites".equals(filter)&&fav.contains(s))||("Failed".equals(filter)&&failed.contains(s));if(keep)out.add(new PhotoItem(u,added,size));}
        }catch(Exception e){toast("Could not read Nikon photos: "+e.getMessage());}return out;
    }

    void openPhotoViewer(ArrayList<Uri> photos,int start){
        if(photos.isEmpty())return;final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);final ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        final int[] index={Math.max(0,Math.min(start,photos.size()-1))};final float[] zoom={1f};final TextView count=txt("",14,Color.WHITE,true);count.setGravity(Gravity.CENTER);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(50),Gravity.BOTTOM);cp.setMargins(dp(70),0,dp(70),dp(12));frame.addView(count,cp);
        Button close=viewerButton("✕");FrameLayout.LayoutParams xp=new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.TOP|Gravity.END);xp.setMargins(0,dp(10),dp(8),0);frame.addView(close,xp);close.setOnClickListener(v->d.dismiss());
        Button star=viewerButton("☆");FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.TOP|Gravity.START);sp.setMargins(dp(8),dp(10),0,0);frame.addView(star,sp);
        Button share=viewerButton("SHARE");FrameLayout.LayoutParams shp=new FrameLayout.LayoutParams(dp(96),dp(58),Gravity.TOP|Gravity.CENTER_HORIZONTAL);shp.setMargins(0,dp(12),0,0);frame.addView(share,shp);
        Button del=viewerButton("DELETE");FrameLayout.LayoutParams dpv=new FrameLayout.LayoutParams(dp(110),dp(58),Gravity.BOTTOM|Gravity.END);dpv.setMargins(0,0,dp(8),dp(8));frame.addView(del,dpv);
        final Runnable show=()->{try{Uri u=photos.get(index[0]);zoom[0]=1f;image.setScaleX(1f);image.setScaleY(1f);Bitmap b=getContentResolver().loadThumbnail(u,new Size(2400,2400),null);image.setImageBitmap(b);count.setText((index[0]+1)+" / "+photos.size());star.setText(p.getStringSet("favorite_uris",Collections.emptySet()).contains(u.toString())?"★":"☆");}catch(Exception e){image.setImageURI(photos.get(index[0]));}};
        star.setOnClickListener(v->{Uri u=photos.get(index[0]);toggleSet("favorite_uris",u.toString());show.run();});share.setOnClickListener(v->sharePhoto(photos.get(index[0])));del.setOnClickListener(v->{Uri u=photos.get(index[0]);new AlertDialog.Builder(this).setTitle("Delete from phone?").setMessage("This removes the phone copy only. It does not delete Flickr or the Z8 memory card copy.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->{d.dismiss();deleteUris(Collections.singletonList(u));}).show();});
        final ScaleGestureDetector scaleDetector=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector detector){zoom[0]*=detector.getScaleFactor();zoom[0]=Math.max(1f,Math.min(4f,zoom[0]));image.setScaleX(zoom[0]);image.setScaleY(zoom[0]);return true;}});final GestureDetector gesture=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onFling(MotionEvent e1,MotionEvent e2,float vx,float vy){if(e1==null||e2==null||zoom[0]>1.05f)return false;float dx=e2.getX()-e1.getX();if(Math.abs(dx)<dp(80))return false;if(dx<0&&index[0]<photos.size()-1){index[0]++;show.run();return true;}if(dx>0&&index[0]>0){index[0]--;show.run();return true;}return false;}});image.setOnTouchListener((v,e)->{scaleDetector.onTouchEvent(e);gesture.onTouchEvent(e);return true;});show.run();d.setContentView(frame);d.show();
    }

    Button viewerButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(s.length()>2?11:22);b.setBackgroundColor(Color.argb(145,0,0,0));return b;}
    void sharePhoto(Uri u){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("image/jpeg");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share photo"));}catch(Exception e){toast("Could not share photo");}}

    void confirmDelete(ArrayList<String> items){new AlertDialog.Builder(this).setTitle("Delete "+items.size()+" photo(s) from phone?").setMessage("Flickr copies and the Z8 memory card are not deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{ArrayList<Uri> uris=new ArrayList<>();for(String s:items)uris.add(Uri.parse(s));deleteUris(uris);}).show();}
    void confirmDeleteUploaded(){ArrayList<Uri> uris=new ArrayList<>();Set<String> uploaded=p.getStringSet("uploaded_uris",Collections.emptySet());for(PhotoItem x:loadPhotos("All"))if(uploaded.contains(x.uri.toString()))uris.add(x.uri);if(uris.isEmpty()){toast("No tracked uploaded phone copies to delete");return;}new AlertDialog.Builder(this).setTitle("Delete all uploaded phone copies?").setMessage(uris.size()+" photo(s) have verified Flickr uploads. This deletes only their phone copies.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->deleteUris(uris)).show();}
    void deleteUris(List<Uri> uris){
        ArrayList<Uri> denied=new ArrayList<>();int deleted=0;for(Uri u:uris){try{if(getContentResolver().delete(u,null,null)>0){deleted++;removePhotoTracking(u.toString());}}catch(SecurityException e){denied.add(u);}catch(Exception e){denied.add(u);}}
        selectedPhotos.clear();photoSelectMode=false;if(!denied.isEmpty()&&Build.VERSION.SDK_INT>=30){try{PendingIntent pi=MediaStore.createDeleteRequest(getContentResolver(),denied);startIntentSenderForResult(pi.getIntentSender(),REQ_DELETE_MEDIA,null,0,0,0);}catch(Exception e){toast("Android could not open delete approval: "+e.getMessage());drawPhotos();}}else{toast("Deleted "+deleted+" photo(s) from phone");drawPhotos();}
    }
    void removePhotoTracking(String s){String[]keys={"favorite_uris","phone_only_uris","failed_upload_uris","pending_uploads","current_session_uris"};SharedPreferences.Editor e=p.edit();for(String k:keys){Set<String>x=new HashSet<>(p.getStringSet(k,Collections.emptySet()));x.remove(s);e.putStringSet(k,x);}e.apply();}

    void drawUploads(){
        base("Uploads");boolean on=p.getBoolean("flickr_upload_enabled",true),isPublic=p.getBoolean("public",true);int pending=p.getStringSet("pending_uploads",Collections.emptySet()).size(),failed=p.getStringSet("failed_upload_uris",Collections.emptySet()).size(),pendingAlbums=p.getStringSet("pending_album_assignments",Collections.emptySet()).size();section("Flickr queue");
        body.addView(txt(on?(pending==0?"✓ ALL CAUGHT UP":pending+" photo(s) waiting"):"FLICKR UPLOADS PAUSED",22,on?(pending==0?green:amber):muted,true));body.addView(txt(on?("Privacy: "+(isPublic?"PUBLIC":"ONLY ME / PRIVATE")):"Z8 photos still save to the phone.",14,on?(isPublic?amber:green):muted,true));space();
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.addView(statBox("PENDING",String.valueOf(pending)),new LinearLayout.LayoutParams(0,dp(76),1));row.addView(statBox("FAILED",String.valueOf(failed)),new LinearLayout.LayoutParams(0,dp(76),1));row.addView(statBox("ALBUM",String.valueOf(pendingAlbums)),new LinearLayout.LayoutParams(0,dp(76),1));body.addView(row);space();
        String album=p.getString("last_album_name","None yet"),albumStatus=p.getString("last_album_status","");body.addView(txt("Last received: "+p.getString("last_received_name","None yet")+"\n"+formatPrefTime("last_received_time")+"\n\nLast verified Flickr upload: "+p.getString("last_uploaded_name","None yet")+"\n"+formatPrefTime("last_uploaded_time")+"\n\nFlickr album: "+album+(albumStatus.isEmpty()?"":"\n"+albumStatus),15,white,false));
        Button retry=big(on?"RETRY FAILED / PENDING NOW":"FLICKR IS PAUSED");retry.setEnabled(on);retry.setOnClickListener(v->{sendServiceCommand("retry_failed");toast("Retry requested");});body.addView(retry);
        String completed=p.getString("completed_upload_log","");section("Completed this app session");body.addView(txt(completed.isEmpty()?"Nothing completed since this list was cleared.":completed,13,completed.isEmpty()?muted:white,false));Button clear=big("CLEAR COMPLETED");clear.setOnClickListener(v->{p.edit().remove("completed_upload_log").apply();drawUploads();});body.addView(clear);
        section("Recent diagnostics");String log=p.getString("event_log","");body.addView(txt(log.isEmpty()?"No activity recorded yet.":log,12,log.isEmpty()?muted:white,false));
    }

    void drawSettings(){
        base("Settings");section("Automatic operation");
        Switch auto=new Switch(this);auto.setText("Start Z8 receiver when app opens");auto.setTextColor(white);auto.setChecked(p.getBoolean("auto_start_receiver",true));auto.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("auto_start_receiver",c).apply());body.addView(auto);
        Switch upload=new Switch(this);upload.setText("Automatically upload new photos to Flickr");upload.setTextColor(white);upload.setChecked(p.getBoolean("flickr_upload_enabled",true));upload.setOnCheckedChangeListener((b,c)->{p.edit().putBoolean("flickr_upload_enabled",c).apply();startDirect(false);toast(c?"Flickr uploads ON":"Phone-only mode ON");});body.addView(upload);
        Switch duplicate=new Switch(this);duplicate.setText("Duplicate protection");duplicate.setTextColor(white);duplicate.setChecked(p.getBoolean("duplicate_protection",true));duplicate.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("duplicate_protection",c).apply());body.addView(duplicate);
        Switch awake=new Switch(this);awake.setText("Keep screen awake while app is open");awake.setTextColor(white);awake.setChecked(p.getBoolean("keep_screen_awake",false));awake.setOnCheckedChangeListener((b,c)->{p.edit().putBoolean("keep_screen_awake",c).apply();applyKeepAwake();});body.addView(awake);
        Switch clearDone=new Switch(this);clearDone.setText("Clear completed upload list when app closes");clearDone.setTextColor(white);clearDone.setChecked(p.getBoolean("auto_clear_completed",true));clearDone.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("auto_clear_completed",c).apply());body.addView(clearDone);

        section("Flickr privacy");Switch privateOnly=new Switch(this);privateOnly.setText("Only me — keep new Flickr photos private");privateOnly.setTextColor(white);privateOnly.setChecked(!p.getBoolean("public",true));privateOnly.setOnCheckedChangeListener((b,c)->{p.edit().putBoolean("public",!c).apply();toast(c?"New Flickr photos will be PRIVATE — only you":"New Flickr photos will be PUBLIC");});body.addView(privateOnly);body.addView(txt("This changes new uploads only.",13,muted,false));

        section("Automatic Flickr albums");Switch albums=new Switch(this);albums.setText("Create date-based albums automatically");albums.setTextColor(white);albums.setChecked(p.getBoolean("daily_albums_enabled",true));albums.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("daily_albums_enabled",c).apply());body.addView(albums);EditText max=input("Maximum photos per album",String.valueOf(p.getInt("album_max_photos",999)));max.setInputType(InputType.TYPE_CLASS_NUMBER);body.addView(max);EditText suffix=input("Optional album suffix",p.getString("album_suffix",""));body.addView(suffix);String example=FlickrClient.albumBaseTitle(System.currentTimeMillis(),p.getString("album_suffix",""));body.addView(txt("Example: "+example+" → "+example+"-2 after the limit.",13,muted,false));Button saveAlbum=big("SAVE ALBUM SETTINGS");saveAlbum.setOnClickListener(v->{int n=999;try{n=Integer.parseInt(max.getText().toString().trim());}catch(Exception ignored){}n=Math.max(1,Math.min(999,n));String suf=clean(suffix.getText().toString());p.edit().putInt("album_max_photos",n).putString("album_suffix",suf).remove("flickr_album_cache_base").remove("flickr_album_cache_id").remove("flickr_album_cache_title").remove("flickr_album_cache_count").apply();max.setText(String.valueOf(n));toast("Album settings saved");});body.addView(saveAlbum);

        section("Phone cleanup");Switch autoDelete=new Switch(this);autoDelete.setText("Delete phone copy after verified Flickr upload");autoDelete.setTextColor(white);autoDelete.setChecked(p.getBoolean("auto_delete_after_upload",false));autoDelete.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("auto_delete_after_upload",c).apply());body.addView(autoDelete);body.addView(txt("OFF by default. Flickr and album placement must verify before automatic deletion.",13,muted,false));EditText days=input("Keep uploaded phone copies for days (0 = disabled)",String.valueOf(p.getInt("cleanup_keep_days",0)));days.setInputType(InputType.TYPE_CLASS_NUMBER);body.addView(days);Button saveCleanup=big("SAVE CLEANUP RULE");saveCleanup.setOnClickListener(v->{int n=0;try{n=Integer.parseInt(days.getText().toString().trim());}catch(Exception ignored){}n=Math.max(0,Math.min(365,n));p.edit().putInt("cleanup_keep_days",n).apply();days.setText(String.valueOf(n));toast(n==0?"Timed cleanup disabled":"Uploaded phone copies older than "+n+" day(s) can auto-delete");});body.addView(saveCleanup);

        section("Flickr account");EditText k=input("Flickr API key",p.getString("flickr_key",""));EditText s=input("Flickr API secret",p.getString("flickr_secret",""));s.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);body.addView(k);body.addView(s);Button save=big("SAVE FLICKR API KEY");save.setOnClickListener(v->{p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();toast("Saved");});body.addView(save);Button conn=big(p.getString("access_token","").isEmpty()?"CONNECT FLICKR":"RECONNECT FLICKR");conn.setOnClickListener(v->{p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();new Thread(()->{try{String u=new FlickrClient(this).beginAuth();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}}).start();});body.addView(conn);if(!p.getString("flickr_name","").isEmpty())body.addView(txt("Connected as: "+p.getString("flickr_name",""),15,green,true));
        section("Upload defaults");EditText tags=input("Default Flickr tags",p.getString("tags","nikon z8"));body.addView(tags);tags.setOnFocusChangeListener((v,f)->{if(!f)p.edit().putString("tags",tags.getText().toString()).apply();});

        section("Diagnostics & reliability");body.addView(txt("The app automatically checks the receiver, storage, cellular Flickr path and account state on Home. The receiver also restarts its FTP listener after a drop.",13,muted,false));Button diag=big("EXPORT DIAGNOSTIC LOG");diag.setOnClickListener(v->exportDiagnostics());body.addView(diag);Button battery=big("OPEN APP / BATTERY SETTINGS");battery.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("Could not open app settings");}});body.addView(battery);

        section("Permanent Z8 FTP login");body.addView(txt("Port: 2121\nUser: nikon\nPassive ports: 32768–61000",15,white,false));EditText ftpPass=input("FTP password",p.getString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD));ftpPass.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);body.addView(ftpPass);Button ftpSave=big("SAVE PERMANENT FTP PASSWORD");ftpSave.setOnClickListener(v->{String x=ftpPass.getText().toString().trim();if(x.length()<4){toast("Use at least 4 digits");return;}p.edit().putString("ftp_password",x).putBoolean("ftp_fixed_password_v031",true).apply();stopService(new Intent(this,DirectTransferService.class));startDirect(false);toast("FTP password saved");});body.addView(ftpSave);Button reset=big("RESET PASSWORD TO 47250558");reset.setOnClickListener(v->{p.edit().putString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD).putBoolean("ftp_fixed_password_v031",true).apply();ftpPass.setText(DirectTransferService.DEFAULT_FTP_PASSWORD);stopService(new Intent(this,DirectTransferService.class));startDirect(false);toast("FTP password reset to 47250558");});body.addView(reset);
        body.addView(txt("Version 0.3.5 adds game sessions, health checks, storage/battery status, cleanup and in-app deletion, favorites/share/filtering, duplicate protection, verified Flickr completion, pause controls and diagnostics.",13,muted,false));
    }

    void exportDiagnostics(){
        try{String name="NikonAutoUpload-Diagnostics-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".txt";ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,"text/plain");if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Nikon Auto Upload");v.put(MediaStore.Downloads.IS_PENDING,1);}Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new Exception("Could not create diagnostics file");try(OutputStream out=getContentResolver().openOutputStream(u,"w")){if(out==null)throw new Exception("Could not write diagnostics file");out.write(diagnosticText().getBytes(StandardCharsets.UTF_8));}if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,done,null,null);}Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"Share diagnostic log"));}catch(Exception e){toast("Diagnostic export failed: "+e.getMessage());}
    }
    String diagnosticText(){return "Nikon Auto Upload 0.3.5\nGenerated: "+formatTime(System.currentTimeMillis())+"\n\nReceiver running: "+p.getBoolean("receiver_running",false)+"\nReceiver paused: "+p.getBoolean("receiver_paused",false)+"\nFTP state: "+p.getString("ftp_state","")+"\nWi-Fi IP: "+p.getString("last_ip","")+"\nCellular: "+p.getString("cellular_state","")+"\nFlickr enabled: "+p.getBoolean("flickr_upload_enabled",true)+"\nFlickr connected: "+(!p.getString("access_token","").isEmpty())+"\nPrivacy: "+(p.getBoolean("public",true)?"PUBLIC":"PRIVATE")+"\nDaily albums: "+p.getBoolean("daily_albums_enabled",true)+"\nCurrent album: "+p.getString("last_album_name","")+"\nPending uploads: "+p.getStringSet("pending_uploads",Collections.emptySet()).size()+"\nFailed: "+p.getStringSet("failed_upload_uris",Collections.emptySet()).size()+"\nPhone folder size: "+formatBytes(nikonFolderBytes())+"\nFree storage: "+formatBytes(freeBytes())+"\nPhone battery: "+phoneBattery()+"%\n\nRecent events:\n"+p.getString("event_log","");}

    long nikonFolderBytes(){long total=0;try(Cursor c=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media.SIZE,MediaStore.Images.Media.RELATIVE_PATH},Build.VERSION.SDK_INT>=29?MediaStore.Images.Media.RELATIVE_PATH+" LIKE ?":null,Build.VERSION.SDK_INT>=29?new String[]{"Pictures/Nikon Auto Upload%"}:null,null)){while(c!=null&&c.moveToNext())total+=Math.max(0,c.getLong(0));}catch(Exception ignored){}return total;}
    long freeBytes(){try{File f=getExternalFilesDir(null);if(f==null)f=getFilesDir();StatFs s=new StatFs(f.getAbsolutePath());return s.getAvailableBytes();}catch(Exception e){return Long.MAX_VALUE;}}
    String storageSummary(){long free=freeBytes();return free==Long.MAX_VALUE?"Storage available":"Free: "+formatBytes(free);}
    int phoneBattery(){try{Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(i==null)return -1;int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);return scale>0?(int)Math.round(level*100.0/scale):-1;}catch(Exception e){return -1;}}
    String formatBytes(long n){if(n<1024)return n+" B";double v=n;String[]u={"B","KB","MB","GB","TB"};int i=0;while(v>=1024&&i<u.length-1){v/=1024;i++;}return String.format(Locale.US,i>=3?"%.1f %s":"%.0f %s",v,u[i]);}
    void applyKeepAwake(){if(p!=null&&p.getBoolean("keep_screen_awake",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    void toggleSet(String key,String value){Set<String>s=new HashSet<>(p.getStringSet(key,Collections.emptySet()));if(!s.add(value))s.remove(value);p.edit().putStringSet(key,s).apply();}
    String clean(String s){return s==null?"":s.replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}
    String formatPrefTime(String key){long t=p.getLong(key,0);return t==0?"":formatTime(t);}String formatTime(long t){return new SimpleDateFormat("MMM d, yyyy  h:mm:ss a",Locale.US).format(new Date(t));}
    LinearLayout statBox(String label,String value){LinearLayout x=panelBox();x.setGravity(Gravity.CENTER);x.addView(txt(value,23,green,true));x.addView(txt(label,10,muted,true));return x;}
    LinearLayout panelBox(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(12),dp(10),dp(12),dp(10));x.setBackgroundColor(panel);return x;}
    void requestPerms(){ArrayList<String>x=new ArrayList<>();if(Build.VERSION.SDK_INT>=33){if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.POST_NOTIFICATIONS);if(checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.READ_MEDIA_IMAGES);}else if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.READ_EXTERNAL_STORAGE);if(!x.isEmpty())requestPermissions(x.toArray(new String[0]),7);}
    void card(String a,String b,int c){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(18),dp(18),dp(18),dp(18));x.setBackgroundColor(panel);x.addView(txt(a,26,white,true));x.addView(txt(b,15,muted,false));body.addView(x,new LinearLayout.LayoutParams(-1,dp(120)));space();}
    void section(String s){space();body.addView(txt(s,18,blue,true));space();}void space(){Space s=new Space(this);body.addView(s,new LinearLayout.LayoutParams(1,dp(12)));}
    TextView txt(String s,int z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(null,1);t.setGravity(Gravity.CENTER_VERTICAL);t.setLineSpacing(0,1.08f);return t;}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(white);b.setBackgroundColor(Color.TRANSPARENT);return b;}
    Button big(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(8),0,dp(8));b.setLayoutParams(lp);return b;}
    EditText input(String hint,String val){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(muted);e.setTextColor(white);e.setText(val);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackgroundColor(panel);e.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(54)));return e;}
    void toast(String s){Toast.makeText(this,s==null?"Error":s,Toast.LENGTH_LONG).show();}int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
