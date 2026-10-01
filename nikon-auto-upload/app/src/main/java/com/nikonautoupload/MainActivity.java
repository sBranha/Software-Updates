package com.nikonautoupload;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.io.File;
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

    final int bg=Color.rgb(7,11,15),panel=Color.rgb(16,23,31),panel2=Color.rgb(23,32,42),line=Color.rgb(42,54,66);
    final int white=Color.rgb(245,247,250),muted=Color.rgb(155,166,178),blue=Color.rgb(33,150,243),green=Color.rgb(55,206,108),amber=Color.rgb(255,193,7),red=Color.rgb(239,83,80);

    static class PhotoItem { Uri uri; long added; long size; PhotoItem(Uri u,long a,long s){uri=u;added=a;size=s;} }
    static class Health { String title,detail; int color; Health(String t,String d,int c){title=t;detail=d;color=c;} }

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
        migrateStableSettings();applyKeepAwake();requestPerms();registerDirectReceiver();drawHome();handleCallback(getIntent());
        if(p.getBoolean("auto_start_receiver",true))startDirect(false);
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
    @Override protected void onStop(){super.onStop();if(!isChangingConfigurations()&&p.getBoolean("auto_clear_completed",true))p.edit().remove("completed_upload_log").remove("event_log").apply();}
    @Override protected void onDestroy(){if(receiverRegistered)unregisterReceiver(directReceiver);super.onDestroy();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_DELETE_MEDIA){selectedPhotos.clear();photoSelectMode=false;drawPhotos();}}

    void registerDirectReceiver(){IntentFilter f=new IntentFilter(DirectTransferService.ACTION_STATUS);if(Build.VERSION.SDK_INT>=33)registerReceiver(directReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(directReceiver,f);receiverRegistered=true;}

    void handleCallback(Intent i){
        Uri d=i.getData();
        if(d!=null&&"nikonautoupload".equals(d.getScheme()))new Thread(()->{
            try{new FlickrClient(this).finishAuth(d);runOnUiThread(()->{toast("Flickr connected");drawSettings();});}
            catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}
        }).start();
    }

    void base(String title,String active){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(14),dp(6),dp(14),dp(10));

        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(4),dp(6),dp(4),dp(8));
        if("Home".equals(active)){
            ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_camera);header.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        }
        TextView ht=txt(title,22,white,true);LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(0,dp(48),1);htp.setMargins(dp(8),0,0,0);header.addView(ht,htp);
        if(!"Settings".equals(active)){
            TextView gear=iconText("⚙",24,muted);gear.setOnClickListener(v->drawSettings());header.addView(gear,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(62)));

        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,0,0,dp(10));
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setVerticalScrollBarEnabled(false);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(2),dp(6),dp(2),dp(6));nav.setBackground(rounded(Color.rgb(9,14,19),18,line,1));
        addNav("⌂","Home",active);addNav("▣","Photos",active);addNav("⇧","Uploads",active);addNav("⚙","Settings",active);
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,dp(68));nlp.setMargins(0,dp(6),0,0);root.addView(nav,nlp);
        setContentView(root);

        if(Build.VERSION.SDK_INT>=20){root.setOnApplyWindowInsetsListener((v,insets)->{int bottom=insets.getSystemWindowInsetBottom();root.setPadding(dp(14),dp(6),dp(14),Math.max(dp(10),bottom+dp(4)));return insets;});root.requestApplyInsets();}
    }

    void addNav(String icon,String label,String active){
        TextView t=txt(icon+"\n"+label,11,label.equals(active)?blue:muted,label.equals(active));t.setGravity(Gravity.CENTER);t.setLineSpacing(0,.95f);t.setOnClickListener(v->{if(label.equals("Home"))drawHome();else if(label.equals("Photos"))drawPhotos();else if(label.equals("Uploads"))drawUploads();else drawSettings();});nav.addView(t,new LinearLayout.LayoutParams(0,-1,1));
    }

    void drawHome(){
        base("Nikon Auto Upload","Home");
        boolean flickrOn=p.getBoolean("flickr_upload_enabled",true),isPublic=p.getBoolean("public",true),paused=p.getBoolean("receiver_paused",false);
        String ftp=p.getString("ftp_state","Waiting for Z8"),cell=p.getString("cellular_state","Checking cellular");
        boolean cameraConnected=isCameraConnected(),cellReady=cell.toLowerCase(Locale.US).contains("ready");

        LinearLayout hero=cardBox();hero.setGravity(Gravity.CENTER_HORIZONTAL);hero.setPadding(dp(18),dp(16),dp(18),dp(18));
        ImageView camera=new ImageView(this);camera.setImageResource(R.drawable.ic_camera);camera.setScaleType(ImageView.ScaleType.FIT_CENTER);hero.addView(camera,new LinearLayout.LayoutParams(dp(190),dp(150)));
        TextView z8=txt("Nikon Z8",22,white,true);z8.setGravity(Gravity.CENTER);hero.addView(z8,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView conn=txt((cameraConnected?"●  Connected by Wi-Fi FTP":"●  Waiting for camera"),14,cameraConnected?green:muted,true);conn.setGravity(Gravity.CENTER);hero.addView(conn,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);chips.setGravity(Gravity.CENTER);chips.addView(chip("Wi-Fi FTP",cameraConnected?green:muted),new LinearLayout.LayoutParams(0,dp(58),1));chips.addView(chip(flickrOn?(cellReady?"5G/LTE Ready":"Cellular Check"):"Flickr Paused",flickrOn?(cellReady?blue:amber):muted),new LinearLayout.LayoutParams(0,dp(58),1));hero.addView(chips,new LinearLayout.LayoutParams(-1,dp(62)));
        body.addView(hero);space(10);

        Health h=health();TextView ready=txt(h.title,17,Color.WHITE,true);ready.setGravity(Gravity.CENTER);ready.setBackground(rounded(h.color,16,h.color,0));ready.setPadding(dp(12),0,dp(12),0);body.addView(ready,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView hd=txt(h.detail,12,muted,false);hd.setGravity(Gravity.CENTER);body.addView(hd,new LinearLayout.LayoutParams(-1,dp(42)));

        PhotoItem latest=firstPhoto();
        if(latest!=null){
            sectionHeader("Latest photo");LinearLayout live=cardBox();ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);try{image.setImageBitmap(getContentResolver().loadThumbnail(latest.uri,new Size(1400,900),null));}catch(Exception e){image.setImageResource(R.drawable.ic_camera);}live.addView(image,new LinearLayout.LayoutParams(-1,dp(190)));
            LinearLayout liveInfo=new LinearLayout(this);liveInfo.setOrientation(LinearLayout.HORIZONTAL);liveInfo.setGravity(Gravity.CENTER_VERTICAL);liveInfo.setPadding(dp(12),dp(8),dp(12),dp(8));TextView li=txt(displayName(latest.uri)+"\n"+formatBytes(latest.size),13,white,true);liveInfo.addView(li,new LinearLayout.LayoutParams(0,dp(48),1));TextView view=txt("VIEW ›",12,blue,true);view.setGravity(Gravity.CENTER);view.setOnClickListener(v->{ArrayList<Uri>x=new ArrayList<>();x.add(latest.uri);openPhotoViewer(x,0);});liveInfo.addView(view,new LinearLayout.LayoutParams(dp(72),dp(48)));live.addView(liveInfo);body.addView(live);space(8);
        }

        if(p.getBoolean("session_active",false)){
            sectionHeader("Current session");LinearLayout game=cardBox();game.addView(txt(p.getString("session_name","").isEmpty()?"Game session":p.getString("session_name",""),18,white,true));game.addView(txt("Received "+p.getInt("session_received",0)+"   •   Uploaded "+p.getInt("session_uploaded",0)+"   •   Albums "+p.getInt("session_albums",0),12,muted,false));Button end=big("END GAME / SESSION",red);end.setOnClickListener(v->endGame());game.addView(end);body.addView(game);
        }else{
            Button game=big("START GAME / SESSION",blue);game.setOnClickListener(v->startGameDialog());body.addView(game);
        }

        sectionHeader("Quick controls");LinearLayout controls=cardBox();
        Switch flickr=new Switch(this);styleSwitch(flickr,"Upload new photos to Flickr",flickrOn);flickr.setOnCheckedChangeListener((b,c)->{p.edit().putBoolean("flickr_upload_enabled",c).apply();startDirect(false);toast(c?"Flickr uploads ON":"Flickr paused — camera transfer stays ON");drawHome();});controls.addView(flickr);
        Button receive=smallButton(paused?"RESUME CAMERA RECEIVING":"PAUSE CAMERA RECEIVING",paused?green:amber);receive.setOnClickListener(v->{sendServiceCommand(paused?"resume_receiver":"pause_receiver");p.edit().putBoolean("receiver_paused",!paused).apply();drawHome();});controls.addView(receive);body.addView(controls);

        sectionHeader("Status");LinearLayout statusCard=cardBox();connectionInfo=txt("",13,white,false);statusCard.addView(connectionInfo);body.addView(statusCard);status=txt("● "+p.getString("last_status","Ready"),12,statusColor(p.getString("last_status","")),true);body.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));refreshLiveViews(null);
    }

    Health health(){
        boolean running=p.getBoolean("receiver_running",false),paused=p.getBoolean("receiver_paused",false),flickr=p.getBoolean("flickr_upload_enabled",true);long free=freeBytes();int battery=phoneBattery();
        if(paused)return new Health("RECEIVING PAUSED","Tap resume before shooting.",amber);
        if(!running)return new Health("START RECEIVER","The Z8 receiver is not running.",red);
        if(free<1024L*1024L*1024L)return new Health("LOW PHONE STORAGE","Less than 1 GB free.",amber);
        if(battery>=0&&battery<15)return new Health("LOW PHONE BATTERY","Charge the phone before a long shoot.",amber);
        if(flickr&&p.getString("access_token","").isEmpty())return new Health("FLICKR NOT CONNECTED","Camera transfer still works; connect Flickr in Settings.",amber);
        if(flickr&&!p.getString("cellular_state","").toLowerCase(Locale.US).contains("ready"))return new Health("GETTING READY","Waiting for the cellular Flickr path.",blue);
        if(isCameraConnected())return new Health("CAMERA CONNECTED","Ready to receive full-size JPEGs from the Z8.",green);
        return new Health("READY — WAITING FOR Z8","Turn on your Z8 WLAN2 FTP profile. No outside Wi-Fi is required.",green);
    }

    boolean isCameraConnected(){String ftp=p.getString("ftp_state","").toLowerCase(Locale.US);return ftp.contains("connected")||ftp.contains("logged")||ftp.contains("receiv")||ftp.contains("transfer")||ftp.contains("photo");}

    void refreshLiveViews(String newest){
        if(status!=null){String s=newest==null?p.getString("last_status","Ready"):newest;status.setText("● "+s);status.setTextColor(statusColor(s));}
        if(connectionInfo!=null){
            boolean on=p.getBoolean("flickr_upload_enabled",true),pub=p.getBoolean("public",true);String album=p.getString("last_album_name","");
            StringBuilder b=new StringBuilder();
            b.append("Camera: ").append(p.getString("ftp_state","Waiting for Z8")).append('\n');
            b.append("Receiver: ").append(p.getBoolean("receiver_paused",false)?"PAUSED":"RUNNING").append('\n');
            b.append("Phone storage: ").append(formatBytes(freeBytes())).append(" free").append('\n');
            b.append("Flickr: ").append(on?("ON • "+(pub?"PUBLIC":"PRIVATE")):"OFF • PHONE ONLY").append('\n');
            if(on)b.append("Cellular: ").append(p.getString("cellular_state","Checking")).append('\n');
            if(!album.isEmpty())b.append("Album: ").append(album).append("  ").append(p.getInt("last_album_count",0)).append('/').append(p.getInt("album_max_photos",999)).append('\n');
            b.append("Pending uploads: ").append(p.getStringSet("pending_uploads",Collections.emptySet()).size());
            connectionInfo.setText(b.toString());
        }
    }

    int statusColor(String s){String x=s==null?"":s.toLowerCase(Locale.US);if(x.contains("error")||x.contains("rejected")||x.contains("failed"))return amber;if(x.contains("waiting")||x.contains("stopped")||x.contains("not ready")||x.contains("paused"))return muted;return green;}

    void startGameDialog(){
        EditText name=input("Game / session name (optional)","");
        new AlertDialog.Builder(this).setTitle("Start Game / Session").setMessage("The session name can become the Flickr album suffix.").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Start",(d,w)->{
            String n=clean(name.getText().toString());SharedPreferences.Editor e=p.edit().putBoolean("session_active",true).putString("session_name",n).putLong("session_start",System.currentTimeMillis()).putInt("session_received",0).putInt("session_uploaded",0).putInt("session_albums",0).putBoolean("receiver_paused",false).putStringSet("current_session_uris",new HashSet<>()).putStringSet("session_album_names",new HashSet<>());e.remove("flickr_album_cache_base").remove("flickr_album_cache_id").remove("flickr_album_cache_title").remove("flickr_album_cache_count").apply();sendServiceCommand("resume_receiver");startDirect(false);drawHome();
        }).show();
    }

    void endGame(){
        int received=p.getInt("session_received",0),uploaded=p.getInt("session_uploaded",0),albums=p.getInt("session_albums",0),pending=p.getStringSet("pending_uploads",Collections.emptySet()).size();String name=p.getString("session_name","");p.edit().putBoolean("session_active",false).putLong("session_end",System.currentTimeMillis()).putBoolean("receiver_paused",true).apply();sendServiceCommand("pause_receiver");
        new AlertDialog.Builder(this).setTitle("Session complete"+(name.isEmpty()?"":" — "+name)).setMessage("Received: "+received+"\nUploaded: "+uploaded+"\nAlbums: "+albums+"\nStill pending: "+pending).setPositiveButton("OK",(d,w)->drawHome()).show();
    }

    void startDirect(boolean toastIt){Intent i=new Intent(this,DirectTransferService.class).putExtra("mode","z8ap");if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);p.edit().putBoolean("receiver_running",true).apply();if(toastIt)toast("Z8 receiver started");}
    void sendServiceCommand(String cmd){Intent i=new Intent(this,DirectTransferService.class).putExtra("mode","z8ap").putExtra("command",cmd);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}

    void drawPhotos(){
        base("Photos","Photos");
        LinearLayout toolbar=cardBox();toolbar.setOrientation(LinearLayout.HORIZONTAL);Spinner spin=new Spinner(this);String[] filters={"All","Today","This game","Uploaded","Phone only","Favorites","Failed"};ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filters);spin.setAdapter(a);spin.setSelection(Math.max(0,Arrays.asList(filters).indexOf(photoFilter)),false);spin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){String f=filters[position];if(!f.equals(photoFilter)){photoFilter=f;selectedPhotos.clear();drawPhotos();}}});toolbar.addView(spin,new LinearLayout.LayoutParams(0,dp(48),1));Button select=smallButton(photoSelectMode?"CANCEL":"SELECT",blue);select.setOnClickListener(v->{photoSelectMode=!photoSelectMode;selectedPhotos.clear();drawPhotos();});toolbar.addView(select,new LinearLayout.LayoutParams(dp(104),dp(48)));body.addView(toolbar);space(8);

        if(photoSelectMode){Button del=big("DELETE SELECTED ("+selectedPhotos.size()+")",red);del.setOnClickListener(v->{if(selectedPhotos.isEmpty())toast("Select at least one photo");else confirmDelete(new ArrayList<>(selectedPhotos));});body.addView(del);}

        ArrayList<PhotoItem> items=loadPhotos(photoFilter);if(items.isEmpty()){body.addView(emptyState("No photos match this filter."));return;}
        LinkedHashMap<String,ArrayList<PhotoItem>> groups=new LinkedHashMap<>();SimpleDateFormat df=new SimpleDateFormat("MMM d, yyyy",Locale.US);for(PhotoItem x:items){String k=df.format(new Date(x.added));groups.computeIfAbsent(k,z->new ArrayList<>()).add(x);}
        ArrayList<Uri> allUris=new ArrayList<>();for(PhotoItem x:items)allUris.add(x.uri);
        for(Map.Entry<String,ArrayList<PhotoItem>> g:groups.entrySet()){
            TextView date=txt(g.getKey()+"  ("+g.getValue().size()+")",14,white,true);body.addView(date,new LinearLayout.LayoutParams(-1,dp(40)));
            LinearLayout row=null;int col=0;
            for(PhotoItem item:g.getValue()){
                if(col%3==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);body.addView(row,new LinearLayout.LayoutParams(-1,dp(photoSelectMode?148:118)));}
                Uri uri=item.uri;int index=allUris.indexOf(uri);FrameLayout cell=new FrameLayout(this);LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(photoSelectMode?142:112),1);clp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(cell,clp);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(panel2);cell.addView(image,new FrameLayout.LayoutParams(-1,-1));try{image.setImageBitmap(getContentResolver().loadThumbnail(uri,new Size(700,700),null));}catch(Exception e){image.setImageResource(R.drawable.ic_camera);}if(photoSelectMode){CheckBox cb=new CheckBox(this);cb.setChecked(selectedPhotos.contains(uri.toString()));cb.setButtonTintList(ColorStateList.valueOf(blue));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.END);cell.addView(cb,cp);cb.setOnCheckedChangeListener((b,c)->{if(c)selectedPhotos.add(uri.toString());else selectedPhotos.remove(uri.toString());});image.setOnClickListener(v->cb.setChecked(!cb.isChecked()));}else image.setOnClickListener(v->openPhotoViewer(allUris,index));col++;
            }
            while(col%3!=0){Space filler=new Space(this);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(112),1);fp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(filler,fp);col++;}
            space(8);
        }
        Button deleteUploaded=smallButton("DELETE VERIFIED UPLOADED COPIES FROM PHONE",amber);deleteUploaded.setOnClickListener(v->confirmDeleteUploaded());body.addView(deleteUploaded,new LinearLayout.LayoutParams(-1,dp(50)));
    }

    ArrayList<PhotoItem> loadPhotos(String filter){
        ArrayList<PhotoItem> out=new ArrayList<>();Set<String> uploaded=p.getStringSet("uploaded_uris",Collections.emptySet()),phone=p.getStringSet("phone_only_uris",Collections.emptySet()),fav=p.getStringSet("favorite_uris",Collections.emptySet()),failed=p.getStringSet("failed_upload_uris",Collections.emptySet()),session=p.getStringSet("current_session_uris",Collections.emptySet());Calendar cal=Calendar.getInstance();cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);long today=cal.getTimeInMillis();
        try(Cursor c=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.SIZE,MediaStore.Images.Media.RELATIVE_PATH},Build.VERSION.SDK_INT>=29?MediaStore.Images.Media.RELATIVE_PATH+" LIKE ?":null,Build.VERSION.SDK_INT>=29?new String[]{"Pictures/Nikon Auto Upload%"}:null,MediaStore.Images.Media.DATE_ADDED+" DESC")){
            while(c!=null&&c.moveToNext()&&out.size()<500){Uri u=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0));String s=u.toString();long added=c.getLong(1)*1000L,size=c.getLong(2);boolean keep="All".equals(filter)||("Today".equals(filter)&&added>=today)||("This game".equals(filter)&&session.contains(s))||("Uploaded".equals(filter)&&uploaded.contains(s))||("Phone only".equals(filter)&&phone.contains(s))||("Favorites".equals(filter)&&fav.contains(s))||("Failed".equals(filter)&&failed.contains(s));if(keep)out.add(new PhotoItem(u,added,size));}
        }catch(Exception e){toast("Could not read Nikon photos: "+e.getMessage());}return out;
    }

    PhotoItem firstPhoto(){ArrayList<PhotoItem>x=loadPhotos("All");return x.isEmpty()?null:x.get(0);}

    void openPhotoViewer(ArrayList<Uri> photos,int start){
        if(photos.isEmpty())return;final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(12),dp(10),dp(12),dp(12));page.setBackgroundColor(bg);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=iconText("‹",34,white);top.addView(back,new LinearLayout.LayoutParams(dp(50),dp(54)));TextView title=txt("Photo Details",18,white,true);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));TextView menu=iconText("⋮",28,muted);top.addView(menu,new LinearLayout.LayoutParams(dp(50),dp(54)));page.addView(top);
        final ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(Color.BLACK);page.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout details=cardBox();TextView name=txt("",15,white,true),date=txt("",12,muted,false);details.addView(name);details.addView(date);TextView phone=txt("✓  Downloaded to phone",13,green,true),flickr=txt("",13,muted,true),album=txt("",13,muted,true);details.addView(phone,new LinearLayout.LayoutParams(-1,dp(38)));details.addView(flickr,new LinearLayout.LayoutParams(-1,dp(38)));details.addView(album,new LinearLayout.LayoutParams(-1,dp(38)));page.addView(details);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER);TextView star=actionItem("☆","Favorite"),share=actionItem("↗","Share"),del=actionItem("⌫","Delete");actions.addView(star,new LinearLayout.LayoutParams(0,dp(68),1));actions.addView(share,new LinearLayout.LayoutParams(0,dp(68),1));actions.addView(del,new LinearLayout.LayoutParams(0,dp(68),1));page.addView(actions);
        final int[] index={Math.max(0,Math.min(start,photos.size()-1))};final float[] zoom={1f};
        final Runnable show=()->{Uri u=photos.get(index[0]);try{zoom[0]=1f;image.setScaleX(1f);image.setScaleY(1f);image.setImageBitmap(getContentResolver().loadThumbnail(u,new Size(2600,2600),null));}catch(Exception e){image.setImageURI(u);}name.setText(displayName(u));date.setText(photoInfo(u));Set<String>up=p.getStringSet("uploaded_uris",Collections.emptySet()),fail=p.getStringSet("failed_upload_uris",Collections.emptySet()),pend=p.getStringSet("pending_uploads",Collections.emptySet());if(up.contains(u.toString())){flickr.setText("☁  Uploaded to Flickr — completed");flickr.setTextColor(green);String a=albumForUri(u.toString());album.setText(a.isEmpty()?"▣  Flickr album: verified":"▣  Saved to album: "+a);album.setTextColor(green);}else if(fail.contains(u.toString())){flickr.setText("!  Flickr upload failed — queued to retry");flickr.setTextColor(amber);album.setText("▣  Album waits for successful upload");album.setTextColor(muted);}else if(pend.contains(u.toString())){flickr.setText("◷  Waiting to upload to Flickr");flickr.setTextColor(amber);album.setText("▣  Album filing pending");album.setTextColor(muted);}else{flickr.setText("○  Saved on phone only");flickr.setTextColor(muted);album.setText("▣  Not sent to Flickr");album.setTextColor(muted);}star.setText((p.getStringSet("favorite_uris",Collections.emptySet()).contains(u.toString())?"★":"☆")+"\nFavorite");};
        back.setOnClickListener(v->d.dismiss());star.setOnClickListener(v->{toggleSet("favorite_uris",photos.get(index[0]).toString());show.run();});share.setOnClickListener(v->sharePhoto(photos.get(index[0]));del.setOnClickListener(v->{Uri u=photos.get(index[0]);new AlertDialog.Builder(this).setTitle("Delete from phone?").setMessage("This removes only the phone copy. Flickr and the Z8 card are not touched.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->{d.dismiss();deleteUris(Collections.singletonList(u));}).show();});
        final ScaleGestureDetector scaleDetector=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector detector){zoom[0]*=detector.getScaleFactor();zoom[0]=Math.max(1f,Math.min(4f,zoom[0]));image.setScaleX(zoom[0]);image.setScaleY(zoom[0]);return true;}});final GestureDetector gesture=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onFling(MotionEvent e1,MotionEvent e2,float vx,float vy){if(e1==null||e2==null||zoom[0]>1.05f)return false;float dx=e2.getX()-e1.getX();if(Math.abs(dx)<dp(80))return false;if(dx<0&&index[0]<photos.size()-1){index[0]++;show.run();return true;}if(dx>0&&index[0]>0){index[0]--;show.run();return true;}return false;}});image.setOnTouchListener((v,e)->{scaleDetector.onTouchEvent(e);gesture.onTouchEvent(e);return true;});show.run();d.setContentView(page);d.show();
    }

    String photoInfo(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.SIZE},null,null,null)){if(c!=null&&c.moveToFirst())return formatTime(c.getLong(0)*1000L)+"   •   "+formatBytes(c.getLong(1));}catch(Exception ignored){}return "Saved on phone";}
    String albumForUri(String uri){String key="album_for_"+Integer.toHexString(uri.hashCode());return p.getString(key,"");}
    TextView actionItem(String icon,String label){TextView t=txt(icon+"\n"+label,12,white,true);t.setGravity(Gravity.CENTER);t.setBackground(rounded(Color.rgb(11,17,23),14,line,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(68),1);lp.setMargins(dp(3),dp(6),dp(3),0);t.setLayoutParams(lp);return t;}
    void sharePhoto(Uri u){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("image/jpeg");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share photo"));}catch(Exception e){toast("Could not share photo");}}

    void confirmDelete(ArrayList<String> items){new AlertDialog.Builder(this).setTitle("Delete "+items.size()+" photo(s) from phone?").setMessage("Flickr copies and the Z8 memory card are not deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{ArrayList<Uri> uris=new ArrayList<>();for(String s:items)uris.add(Uri.parse(s));deleteUris(uris);}).show();}
    void confirmDeleteUploaded(){ArrayList<Uri> uris=new ArrayList<>();Set<String> uploaded=p.getStringSet("uploaded_uris",Collections.emptySet());for(PhotoItem x:loadPhotos("All"))if(uploaded.contains(x.uri.toString()))uris.add(x.uri);if(uris.isEmpty()){toast("No verified uploaded phone copies to delete");return;}new AlertDialog.Builder(this).setTitle("Delete uploaded phone copies?").setMessage(uris.size()+" photo(s) are verified on Flickr. Only the phone copies will be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->deleteUris(uris)).show();}
    void deleteUris(List<Uri> uris){ArrayList<Uri> denied=new ArrayList<>();int deleted=0;for(Uri u:uris){try{if(getContentResolver().delete(u,null,null)>0){deleted++;removePhotoTracking(u.toString());}}catch(SecurityException e){denied.add(u);}catch(Exception e){denied.add(u);}}selectedPhotos.clear();photoSelectMode=false;if(!denied.isEmpty()&&Build.VERSION.SDK_INT>=30){try{PendingIntent pi=MediaStore.createDeleteRequest(getContentResolver(),denied);startIntentSenderForResult(pi.getIntentSender(),REQ_DELETE_MEDIA,null,0,0,0);}catch(Exception e){toast("Android could not open delete approval");drawPhotos();}}else{toast("Deleted "+deleted+" photo(s) from phone");drawPhotos();}}
    void removePhotoTracking(String s){String[]keys={"favorite_uris","phone_only_uris","failed_upload_uris","pending_uploads","current_session_uris"};SharedPreferences.Editor e=p.edit();for(String k:keys){Set<String>x=new HashSet<>(p.getStringSet(k,Collections.emptySet()));x.remove(s);e.putStringSet(k,x);}e.apply();}

    void drawUploads(){
        base("Flickr Uploads","Uploads");boolean on=p.getBoolean("flickr_upload_enabled",true);int pending=p.getStringSet("pending_uploads",Collections.emptySet()).size(),failed=p.getStringSet("failed_upload_uris",Collections.emptySet()).size();
        LinearLayout account=cardBox();LinearLayout ar=new LinearLayout(this);ar.setOrientation(LinearLayout.HORIZONTAL);ar.setGravity(Gravity.CENTER_VERTICAL);TextView f=txt("●  Flickr",22,white,true);ar.addView(f,new LinearLayout.LayoutParams(0,dp(46),1));TextView state=txt(p.getString("access_token","").isEmpty()?"Not connected":"Connected",13,p.getString("access_token","").isEmpty()?amber:green,true);state.setGravity(Gravity.CENTER);ar.addView(state,new LinearLayout.LayoutParams(dp(110),dp(46)));account.addView(ar);if(!p.getString("flickr_name","").isEmpty())account.addView(txt(p.getString("flickr_name",""),12,muted,false));body.addView(account);space(8);

        if(on&&!p.getString("cellular_state","").toLowerCase(Locale.US).contains("ready")){TextView offline=txt("!  Cellular upload path is not ready. Photos stay safely queued and will upload automatically later.",13,Color.rgb(43,35,0),true);offline.setBackground(rounded(Color.rgb(255,211,64),14,Color.rgb(255,211,64),0));offline.setPadding(dp(12),dp(10),dp(12),dp(10));body.addView(offline);space(8);}

        int uploaded=p.getInt("total_uploaded",0),total=uploaded+pending;int pct=total==0?100:(int)Math.round(uploaded*100.0/total);LinearLayout progressCard=cardBox();progressCard.addView(txt(uploaded+" of "+total+" photos uploaded",14,white,true));ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(pct);pb.setProgressTintList(ColorStateList.valueOf(green));progressCard.addView(pb,new LinearLayout.LayoutParams(-1,dp(14)));TextView pp=txt(pct+"%",12,muted,true);pp.setGravity(Gravity.END);progressCard.addView(pp);body.addView(progressCard);space(8);

        Set<String> queue=new LinkedHashSet<>(p.getStringSet("pending_uploads",Collections.emptySet()));Set<String> failSet=p.getStringSet("failed_upload_uris",Collections.emptySet());
        if(queue.isEmpty())body.addView(emptyState("All uploads are caught up."));
        else{
            TextView qh=txt("UPLOAD QUEUE  •  "+queue.size(),13,blue,true);body.addView(qh,new LinearLayout.LayoutParams(-1,dp(38)));
            int n=0;for(String s:queue){if(n++>=30)break;Uri u=Uri.parse(s);LinearLayout item=uploadRow(u,failSet.contains(s)?"Failed — will retry":"Queued",failSet.contains(s)?amber:muted);body.addView(item);space(4);}
        }

        Button retry=big(on?"RETRY FAILED / PENDING NOW":"FLICKR UPLOADS ARE PAUSED",blue);retry.setEnabled(on);retry.setOnClickListener(v->{sendServiceCommand("retry_failed");toast("Retry requested");});body.addView(retry);

        String completed=p.getString("completed_upload_log","");if(!completed.isEmpty()){sectionHeader("Completed this session");LinearLayout done=cardBox();done.addView(txt(completed,12,white,false));body.addView(done);}
        Button clear=smallButton("CLEAR UPLOAD HISTORY & DIAGNOSTICS",muted);clear.setOnClickListener(v->{p.edit().remove("completed_upload_log").remove("event_log").apply();toast("History cleared");drawUploads();});body.addView(clear,new LinearLayout.LayoutParams(-1,dp(50)));
    }

    LinearLayout uploadRow(Uri u,String state,int stateColor){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(7),dp(8),dp(7));row.setBackground(rounded(panel,14,line,1));ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);try{im.setImageBitmap(getContentResolver().loadThumbnail(u,new Size(300,300),null));}catch(Exception e){im.setImageResource(R.drawable.ic_camera);}row.addView(im,new LinearLayout.LayoutParams(dp(62),dp(62)));LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(10),0,0,0);info.addView(txt(displayName(u),13,white,true));info.addView(txt(state,12,stateColor,true));row.addView(info,new LinearLayout.LayoutParams(0,dp(62),1));return row;}

    void drawSettings(){
        base("Settings","Settings");
        sectionHeader("Camera Connection");LinearLayout camera=cardBox();camera.addView(statusRow("Wi-Fi FTP",isCameraConnected()?"Nikon Z8 Connected":"Waiting for Nikon Z8",isCameraConnected()?green:muted));Button wifi=smallButton("OPEN PHONE WI-FI SETTINGS",blue);wifi.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));camera.addView(wifi);Button restart=smallButton("START / RESTART Z8 RECEIVER",green);restart.setOnClickListener(v->startDirect(true));camera.addView(restart);body.addView(camera);

        sectionHeader("Transfer Options");LinearLayout transfer=cardBox();Switch auto=new Switch(this);styleSwitch(auto,"Auto start camera receiver",p.getBoolean("auto_start_receiver",true));auto.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("auto_start_receiver",c).apply());transfer.addView(auto);Switch upload=new Switch(this);styleSwitch(upload,"Upload JPEG files to Flickr",p.getBoolean("flickr_upload_enabled",true));upload.setOnCheckedChangeListener((b,c)->{p.edit().putBoolean("flickr_upload_enabled",c).apply();startDirect(false);});transfer.addView(upload);Switch keep=new Switch(this);styleSwitch(keep,"Keep a copy on phone",!p.getBoolean("auto_delete_after_upload",false));keep.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("auto_delete_after_upload",!c).apply());transfer.addView(keep);Switch dup=new Switch(this);styleSwitch(dup,"Duplicate protection",p.getBoolean("duplicate_protection",true));dup.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("duplicate_protection",c).apply());transfer.addView(dup);Switch awake=new Switch(this);styleSwitch(awake,"Keep screen awake while app is open",p.getBoolean("keep_screen_awake",false));awake.setOnCheckedChangeListener((b,c)->{p.edit().putBoolean("keep_screen_awake",c).apply();applyKeepAwake();});transfer.addView(awake);body.addView(transfer);

        sectionHeader("Flickr Options");LinearLayout flickr=cardBox();flickr.addView(statusRow("Account",p.getString("flickr_name","").isEmpty()?"Not connected":p.getString("flickr_name",""),p.getString("access_token","").isEmpty()?amber:green));Switch privateOnly=new Switch(this);styleSwitch(privateOnly,"Only me — keep new photos private",!p.getBoolean("public",true));privateOnly.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("public",!c).apply());flickr.addView(privateOnly);Switch albums=new Switch(this);styleSwitch(albums,"Create date-based albums automatically",p.getBoolean("daily_albums_enabled",true));albums.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("daily_albums_enabled",c).apply());flickr.addView(albums);EditText max=input("Maximum photos per album",String.valueOf(p.getInt("album_max_photos",999)));max.setInputType(InputType.TYPE_CLASS_NUMBER);flickr.addView(max);EditText suffix=input("Album suffix (optional)",p.getString("album_suffix",""));flickr.addView(suffix);EditText tags=input("Default Flickr tags",p.getString("tags","nikon z8"));flickr.addView(tags);Button saveFlickr=smallButton("SAVE FLICKR OPTIONS",blue);saveFlickr.setOnClickListener(v->{int n=999;try{n=Integer.parseInt(max.getText().toString().trim());}catch(Exception ignored){}n=Math.max(1,Math.min(999,n));p.edit().putInt("album_max_photos",n).putString("album_suffix",clean(suffix.getText().toString())).putString("tags",tags.getText().toString().trim()).remove("flickr_album_cache_base").remove("flickr_album_cache_id").remove("flickr_album_cache_title").remove("flickr_album_cache_count").apply();toast("Flickr options saved");});flickr.addView(saveFlickr);body.addView(flickr);

        sectionHeader("Flickr Account");LinearLayout acct=cardBox();EditText k=input("Flickr API key",p.getString("flickr_key",""));EditText s=input("Flickr API secret",p.getString("flickr_secret",""));s.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);acct.addView(k);acct.addView(s);Button conn=smallButton(p.getString("access_token","").isEmpty()?"CONNECT FLICKR":"RECONNECT FLICKR",green);conn.setOnClickListener(v->{p.edit().putString("flickr_key",k.getText().toString().trim()).putString("flickr_secret",s.getText().toString().trim()).apply();new Thread(()->{try{String u=new FlickrClient(this).beginAuth();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}}).start();});acct.addView(conn);body.addView(acct);

        sectionHeader("Phone Cleanup");LinearLayout cleanup=cardBox();EditText days=input("Keep uploaded phone copies for days (0 = off)",String.valueOf(p.getInt("cleanup_keep_days",0)));days.setInputType(InputType.TYPE_CLASS_NUMBER);cleanup.addView(days);Button saveCleanup=smallButton("SAVE CLEANUP RULE",blue);saveCleanup.setOnClickListener(v->{int n=0;try{n=Integer.parseInt(days.getText().toString().trim());}catch(Exception ignored){}n=Math.max(0,Math.min(365,n));p.edit().putInt("cleanup_keep_days",n).apply();toast(n==0?"Timed cleanup disabled":"Cleanup rule saved");});cleanup.addView(saveCleanup);cleanup.addView(txt("Nikon folder: "+formatBytes(nikonFolderBytes())+"   •   Free: "+formatBytes(freeBytes()),12,muted,false));body.addView(cleanup);

        sectionHeader("Diagnostics & Reliability");LinearLayout diag=cardBox();diag.addView(statusRow("Phone battery",phoneBattery()+"%",phoneBattery()<15?amber:green));diag.addView(statusRow("Cellular",p.getString("cellular_state","Checking"),p.getString("cellular_state","").toLowerCase(Locale.US).contains("ready")?green:amber));Switch clearDone=new Switch(this);styleSwitch(clearDone,"Clear upload history & diagnostics when app closes",p.getBoolean("auto_clear_completed",true));clearDone.setOnCheckedChangeListener((b,c)->p.edit().putBoolean("auto_clear_completed",c).apply());diag.addView(clearDone);Button export=smallButton("EXPORT DIAGNOSTIC LOG",blue);export.setOnClickListener(v->exportDiagnostics());diag.addView(export);Button clear=smallButton("CLEAR DIAGNOSTICS NOW",muted);clear.setOnClickListener(v->{p.edit().remove("event_log").remove("completed_upload_log").apply();toast("Diagnostics cleared");});diag.addView(clear);body.addView(diag);

        sectionHeader("Advanced Z8 FTP");LinearLayout ftp=cardBox();ftp.addView(txt("Port 2121   •   User nikon   •   Passive 32768–61000",12,muted,false));EditText ftpPass=input("FTP password",p.getString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD));ftpPass.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);ftp.addView(ftpPass);Button ftpSave=smallButton("SAVE FTP PASSWORD",blue);ftpSave.setOnClickListener(v->{String x=ftpPass.getText().toString().trim();if(x.length()<4){toast("Use at least 4 digits");return;}p.edit().putString("ftp_password",x).putBoolean("ftp_fixed_password_v031",true).apply();stopService(new Intent(this,DirectTransferService.class));startDirect(false);toast("FTP password saved");});ftp.addView(ftpSave);body.addView(ftp);

        TextView version=txt("Nikon Auto Upload 0.4.0  •  Preview-style redesign  •  Wi-Fi FTP only — no Bluetooth",11,muted,false);version.setGravity(Gravity.CENTER);body.addView(version,new LinearLayout.LayoutParams(-1,dp(54)));
    }

    LinearLayout statusRow(String label,String value,int color){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(4),0,dp(4));r.addView(txt(label,13,white,true),new LinearLayout.LayoutParams(0,dp(42),1));TextView v=txt("●  "+value,12,color,true);v.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);r.addView(v,new LinearLayout.LayoutParams(0,dp(42),1));return r;}

    void exportDiagnostics(){
        try{String name="NikonAutoUpload-Diagnostics-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".txt";ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,"text/plain");if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Nikon Auto Upload");v.put(MediaStore.Downloads.IS_PENDING,1);}Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new Exception("Could not create diagnostics file");try(OutputStream out=getContentResolver().openOutputStream(u,"w")){if(out==null)throw new Exception("Could not write diagnostics file");out.write(diagnosticText().getBytes(StandardCharsets.UTF_8));}if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,done,null,null);}Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"Share diagnostic log"));}catch(Exception e){toast("Diagnostic export failed: "+e.getMessage());}
    }
    String diagnosticText(){return "Nikon Auto Upload 0.4.0\nGenerated: "+formatTime(System.currentTimeMillis())+"\n\nReceiver running: "+p.getBoolean("receiver_running",false)+"\nReceiver paused: "+p.getBoolean("receiver_paused",false)+"\nFTP state: "+p.getString("ftp_state","")+"\nWi-Fi IP: "+p.getString("last_ip","")+"\nCellular: "+p.getString("cellular_state","")+"\nFlickr enabled: "+p.getBoolean("flickr_upload_enabled",true)+"\nFlickr connected: "+(!p.getString("access_token","").isEmpty())+"\nPrivacy: "+(p.getBoolean("public",true)?"PUBLIC":"PRIVATE")+"\nDaily albums: "+p.getBoolean("daily_albums_enabled",true)+"\nCurrent album: "+p.getString("last_album_name","")+"\nPending uploads: "+p.getStringSet("pending_uploads",Collections.emptySet()).size()+"\nFailed: "+p.getStringSet("failed_upload_uris",Collections.emptySet()).size()+"\nPhone folder size: "+formatBytes(nikonFolderBytes())+"\nFree storage: "+formatBytes(freeBytes())+"\nPhone battery: "+phoneBattery()+"%\n\nRecent events:\n"+p.getString("event_log","");}

    long nikonFolderBytes(){long total=0;try(Cursor c=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media.SIZE,MediaStore.Images.Media.RELATIVE_PATH},Build.VERSION.SDK_INT>=29?MediaStore.Images.Media.RELATIVE_PATH+" LIKE ?":null,Build.VERSION.SDK_INT>=29?new String[]{"Pictures/Nikon Auto Upload%"}:null,null)){while(c!=null&&c.moveToNext())total+=Math.max(0,c.getLong(0));}catch(Exception ignored){}return total;}
    long freeBytes(){try{File f=getExternalFilesDir(null);if(f==null)f=getFilesDir();StatFs s=new StatFs(f.getAbsolutePath());return s.getAvailableBytes();}catch(Exception e){return Long.MAX_VALUE;}}
    int phoneBattery(){try{Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(i==null)return -1;int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);return scale>0?(int)Math.round(level*100.0/scale):-1;}catch(Exception e){return -1;}}
    String displayName(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "Nikon Z8 photo";}
    String formatBytes(long n){if(n<0)return "—";if(n<1024)return n+" B";double v=n;String[]u={"B","KB","MB","GB","TB"};int i=0;while(v>=1024&&i<u.length-1){v/=1024;i++;}return String.format(Locale.US,i>=3?"%.1f %s":"%.0f %s",v,u[i]);}
    String formatTime(long t){return new SimpleDateFormat("MMM d, yyyy  h:mm:ss a",Locale.US).format(new Date(t));}
    void applyKeepAwake(){if(p!=null&&p.getBoolean("keep_screen_awake",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    void toggleSet(String key,String value){Set<String>s=new HashSet<>(p.getStringSet(key,Collections.emptySet()));if(!s.add(value))s.remove(value);p.edit().putStringSet(key,s).apply();}
    String clean(String s){return s==null?"":s.replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}

    LinearLayout cardBox(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(12),dp(10),dp(12),dp(10));x.setBackground(rounded(panel,16,line,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(4));x.setLayoutParams(lp);return x;}
    LinearLayout chip(String label,int color){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);x.setPadding(dp(6),dp(5),dp(6),dp(5));x.setBackground(rounded(Color.rgb(11,17,23),14,line,1));x.addView(txt("●",18,color,true));TextView l=txt(label,11,white,true);l.setGravity(Gravity.CENTER);x.addView(l);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(58),1);lp.setMargins(dp(3),dp(4),dp(3),0);x.setLayoutParams(lp);return x;}
    GradientDrawable rounded(int color,int radius,int strokeColor,int strokeWidth){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(strokeWidth>0)g.setStroke(dp(strokeWidth),strokeColor);return g;}
    void sectionHeader(String s){TextView t=txt(s,14,blue,true);body.addView(t,new LinearLayout.LayoutParams(-1,dp(42)));}
    void space(int h){Space s=new Space(this);body.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    TextView txt(String s,int z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);t.setLineSpacing(0,1.06f);return t;}
    TextView iconText(String s,int z,int c){TextView t=txt(s,z,c,false);t.setGravity(Gravity.CENTER);return t;}
    TextView emptyState(String s){TextView t=txt(s,15,muted,true);t.setGravity(Gravity.CENTER);t.setBackground(rounded(panel,16,line,1));t.setPadding(dp(16),dp(22),dp(16),dp(22));t.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(110)));return t;}
    Button big(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackgroundTintList(ColorStateList.valueOf(color));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(6),0,dp(6));b.setLayoutParams(lp);return b;}
    Button smallButton(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackgroundTintList(ColorStateList.valueOf(color));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(lp);return b;}
    EditText input(String hint,String val){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(muted);e.setTextColor(white);e.setText(val);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setBackground(rounded(panel2,12,line,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(0,dp(4),0,dp(4));e.setLayoutParams(lp);return e;}
    void styleSwitch(Switch s,String label,boolean checked){s.setText(label);s.setTextColor(white);s.setTextSize(14);s.setChecked(checked);s.setPadding(0,dp(2),0,dp(2));s.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(54)));}
    void requestPerms(){ArrayList<String>x=new ArrayList<>();if(Build.VERSION.SDK_INT>=33){if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.POST_NOTIFICATIONS);if(checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.READ_MEDIA_IMAGES);}else if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.READ_EXTERNAL_STORAGE);if(!x.isEmpty())requestPermissions(x.toArray(new String[0]),7);}
    void toast(String s){Toast.makeText(this,s==null?"Error":s,Toast.LENGTH_LONG).show();}
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
