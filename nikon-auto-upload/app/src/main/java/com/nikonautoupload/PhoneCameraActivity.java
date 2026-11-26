package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Phone-camera source feeding the same post-import overlay/Flickr workflow. */
public class PhoneCameraActivity extends Activity {
    private static final int REQ_CAPTURE=801;
    private SharedPreferences p;
    private Uri captureUri;
    private TextView status;
    private final int bg=Color.rgb(7,11,15),panel=Color.rgb(16,23,31),line=Color.rgb(42,54,66),white=Color.rgb(245,247,250),muted=Color.rgb(155,166,178),blue=Color.rgb(33,150,243),green=Color.rgb(55,206,108),amber=Color.rgb(255,193,7);

    @Override public void onCreate(Bundle b){super.onCreate(b);p=getSharedPreferences("settings",MODE_PRIVATE);draw();}

    private void draw(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(16),dp(12),dp(16),dp(16));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹",Color.rgb(55,65,76));back.setTextSize(25);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(54),dp(50)));TextView title=text("Phone Camera",22,white,true);top.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(top);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout info=card();info.addView(text("PHONE-ONLY CAMERA MODE",13,blue,true));info.addView(text("Use your phone when the Nikon, Sony, Canon or Fujifilm camera is not with you. The full-resolution phone photo is saved first, stays untouched, and then uses the same optional card/overlay and Flickr queue.",13,muted,false));body.addView(info);

        LinearLayout flow=card();flow.addView(text("Current processing",13,white,true));flow.addView(text("Overlay: "+(p.getBoolean(OverlayProcessor.PREF_AUTO,false)?"Automatic ON":"Off")+"\nFlickr output: "+uploadChoice()+"\nInternet unavailable: photo stays safely queued on the phone",13,muted,false));body.addView(flow);

        Button take=button("TAKE PHOTO WITH PHONE",green);take.setOnClickListener(v->takePhoto());body.addView(take,new LinearLayout.LayoutParams(-1,dp(62)));
        Button studio=button("PHOTO OVERLAY / SPORTS CARD BUILDER",blue);studio.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));body.addView(studio,new LinearLayout.LayoutParams(-1,dp(56)));
        status=text("Ready to use the phone camera.",13,white,true);status.setPadding(dp(10),dp(18),dp(10),dp(10));body.addView(status);
        setContentView(root);
    }

    private String uploadChoice(){String q=p.getString(OverlayProcessor.PREF_UPLOAD_CHOICE,OverlayProcessor.CHOICE_ORIGINAL);if(OverlayProcessor.CHOICE_OVERLAY.equals(q))return "Card version only";if(OverlayProcessor.CHOICE_BOTH.equals(q))return "Original + card";return "Original only";}

    private void takePhoto(){
        try{
            String name="PHONE_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".jpg";
            ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Camera Auto Upload/Phone");v.put(MediaStore.Images.Media.IS_PENDING,1);}captureUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(captureUri==null)throw new Exception("Could not create phone photo");
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,captureUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("Camera Auto Upload photo",captureUri));
            if(i.resolveActivity(getPackageManager())==null)throw new Exception("No phone camera app is available");
            status.setText("Opening phone camera…");startActivityForResult(i,REQ_CAPTURE);
        }catch(Exception e){cleanupCapture();status.setText("Phone camera error: "+shortMessage(e));}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_CAPTURE)return;if(resultCode!=RESULT_OK){cleanupCapture();status.setText("Photo canceled.");return;}if(captureUri==null){status.setText("Photo was taken, but its saved location was lost.");return;}try{if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(captureUri,done,null,null);}String name=displayName(captureUri);Uri original=captureUri;captureUri=null;status.setText("Photo saved. Processing…");new Thread(()->processPhoto(original,name)).start();}catch(Exception e){status.setText("Could not finish phone photo: "+shortMessage(e));}}

    private void processPhoto(Uri original,String name){
        try{
            boolean flickr=p.getBoolean("flickr_upload_enabled",true);OverlayProcessor.ProcessResult r=OverlayProcessor.onImported(this,p,original,name);String originalText=original.toString();
            if(flickr&&r.queueOriginal)queueForFlickr(original);else addSet("phone_only_uris",originalText);
            if(r.overlayUri!=null){String o=r.overlayUri.toString();if(flickr&&r.queueOverlay)queueForFlickr(r.overlayUri);else if(!r.holdOverlay)addSet("phone_only_uris",o);}
            if(p.getBoolean("session_active",false)){addSet("current_session_uris",originalText);p.edit().putInt("session_received",p.getInt("session_received",0)+1).apply();}
            p.edit().putString("phone_camera_state","Last photo saved").putString("last_received_name",name).putLong("last_received_time",System.currentTimeMillis()).apply();
            if(flickr)kickUploader();String msg="Phone photo saved"+(r.message==null||r.message.isEmpty()?"":" • "+r.message)+(flickr?" • Flickr workflow ready":" • phone only");runOnUiThread(()->status.setText(msg));
        }catch(Exception e){runOnUiThread(()->status.setText("Phone photo processing error: "+shortMessage(e)));}
    }

    private void queueForFlickr(Uri u){String x=u.toString();addSet("pending_uploads",x);String suffix=p.getBoolean("session_active",false)?p.getString("session_name",""):p.getString("album_suffix","");String base=FlickrClient.albumBaseTitle(System.currentTimeMillis(),suffix==null?"":suffix.trim());Set<String>s=new HashSet<>(p.getStringSet("pending_album_bases",Collections.emptySet()));Iterator<String>it=s.iterator();while(it.hasNext())if(it.next().startsWith(x+"\u001f"))it.remove();s.add(x+"\u001f"+base);p.edit().putStringSet("pending_album_bases",s).apply();}
    private synchronized void addSet(String key,String value){Set<String>s=new HashSet<>(p.getStringSet(key,Collections.emptySet()));s.add(value);p.edit().putStringSet(key,s).apply();}
    private void kickUploader(){Intent i=new Intent(this,DirectTransferService.class);i.putExtra("command","kick_uploads");if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private String displayName(Uri u){try(android.database.Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "PhonePhoto.jpg";}
    private void cleanupCapture(){if(captureUri!=null){try{getContentResolver().delete(captureUri,null,null);}catch(Exception ignored){}captureUri=null;}}
    private String shortMessage(Throwable e){String s=e==null?"Unknown error":e.getMessage();return s==null?e.getClass().getSimpleName():s;}
    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(13),dp(13),dp(13),dp(13));android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(panel);g.setCornerRadius(dp(16));g.setStroke(dp(1),line);x.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(12));x.setLayoutParams(lp);return x;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(5),dp(6),dp(5),dp(6));if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(color));return b;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
