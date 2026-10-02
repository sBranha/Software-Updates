package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Size;
import android.view.*;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Optional phone-camera input source.
 *
 * This activity intentionally does not own or change USB-C, FTP, Wi-Fi,
 * Bluetooth, PTP, or external-camera transports. It asks Android's normal
 * camera app to capture a full-resolution JPEG into MediaStore, then hands the
 * saved Uri to DirectTransferService so it enters the same overlay/Flickr
 * post-import workflow as every other source.
 */
public class PhoneCameraActivity extends Activity {
    private static final int REQ_CAPTURE=501;
    private final int bg=Color.rgb(7,11,15),panel=Color.rgb(16,23,31),line=Color.rgb(42,54,66),white=Color.rgb(245,247,250),muted=Color.rgb(155,166,178),blue=Color.rgb(33,150,243),green=Color.rgb(55,206,108),amber=Color.rgb(255,193,7),red=Color.rgb(239,83,80);
    private SharedPreferences p;
    private Uri captureUri;
    private String captureName="";
    private TextView status;
    private ImageView preview;

    @Override public void onCreate(Bundle b){super.onCreate(b);p=getSharedPreferences("settings",MODE_PRIVATE);draw();}

    private void draw(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(14),dp(8),dp(14),dp(12));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹",Color.rgb(55,65,76));back.setTextSize(26);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(54),dp(52)));TextView title=text("Phone Camera",22,white,true);top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(top);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout info=card();info.addView(text("USE YOUR PHONE AS THE CAMERA",13,blue,true));info.addView(text("Take a full-resolution photo with the phone's normal camera. The finished JPEG enters the same Camera Auto Upload workflow: original preserved, optional sports-card/overlay copy, then Flickr when internet is available.",13,muted,false));body.addView(info);

        LinearLayout flow=card();flow.addView(statusRow("Source","Phone Camera",green));flow.addView(statusRow("Overlay",p.getBoolean(OverlayProcessor.PREF_AUTO,false)?"Automatic overlay ON":"Automatic overlay OFF",p.getBoolean(OverlayProcessor.PREF_AUTO,false)?green:muted));String q=p.getString(OverlayProcessor.PREF_UPLOAD_CHOICE,OverlayProcessor.CHOICE_ORIGINAL);flow.addView(statusRow("Upload choice",OverlayProcessor.CHOICE_BOTH.equals(q)?"Original + Overlay":(OverlayProcessor.CHOICE_OVERLAY.equals(q)?"Overlay only":"Original only"),blue));body.addView(flow);

        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.setBackgroundColor(Color.rgb(9,14,19));body.addView(preview,new LinearLayout.LayoutParams(-1,dp(260)));
        status=text("Ready to take a photo",13,muted,true);status.setGravity(Gravity.CENTER);body.addView(status,new LinearLayout.LayoutParams(-1,dp(52)));

        Button take=button("TAKE PHOTO",green);take.setTextSize(16);take.setOnClickListener(v->takePhoto());body.addView(take,new LinearLayout.LayoutParams(-1,dp(62)));
        Button overlay=button("PHOTO OVERLAY / SPORTS CARDS",blue);overlay.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));body.addView(overlay,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView note=text("Tip: set your team, player number, template and Original / Overlay / Both choice in Photo Overlay / Sports Cards first. Then come back here and shoot normally.",12,muted,false);note.setPadding(dp(8),dp(10),dp(8),dp(16));body.addView(note);

        setContentView(root);
        if(Build.VERSION.SDK_INT>=20){root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(14),dp(8),dp(14),Math.max(dp(12),insets.getSystemWindowInsetBottom()+dp(4)));return insets;});root.requestApplyInsets();}
    }

    private void takePhoto(){
        if(captureUri!=null){try{getContentResolver().delete(captureUri,null,null);}catch(Exception ignored){}captureUri=null;}
        captureName="PHONE_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".jpg";
        ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,captureName);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Camera Auto Upload");v.put(MediaStore.Images.Media.IS_PENDING,1);}
        captureUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
        if(captureUri==null){setStatus("Could not create phone photo",true);return;}
        Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,captureUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(i.resolveActivity(getPackageManager())==null){try{getContentResolver().delete(captureUri,null,null);}catch(Exception ignored){}captureUri=null;setStatus("No phone camera app is available",true);return;}
        setStatus("Opening phone camera…",false);startActivityForResult(i,REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_CAPTURE)return;
        Uri u=captureUri;captureUri=null;if(u==null)return;
        if(resultCode!=RESULT_OK){try{getContentResolver().delete(u,null,null);}catch(Exception ignored){}setStatus("Photo canceled",false);return;}
        try{
            if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}
            try{preview.setImageBitmap(getContentResolver().loadThumbnail(u,new Size(1400,1000),null));}catch(Exception ignored){preview.setImageURI(u);}
            p.edit().putString("phone_camera_last_uri",u.toString()).putString("phone_camera_last_name",captureName).putLong("phone_camera_last_time",System.currentTimeMillis()).putString("phone_camera_state","Photo captured").apply();
            Intent svc=new Intent(this,DirectTransferService.class);svc.putExtra("command","import_uri");svc.putExtra("uri",u.toString());svc.putExtra("file_name",captureName);svc.putExtra("source_label","Phone Camera");if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);
            setStatus("Photo saved • sending through overlay / Flickr workflow",false);
        }catch(Exception e){setStatus("Phone photo error: "+shortMsg(e),true);}
    }

    private void setStatus(String s,boolean error){if(status!=null){status.setText(s);status.setTextColor(error?red:muted);}p.edit().putString("phone_camera_state",s).apply();}
    private String shortMsg(Throwable e){String m=e==null?"Unknown error":e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(5),dp(4),dp(5));if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(color));return b;}
    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(12),dp(12),dp(12),dp(12));GradientDrawable g=new GradientDrawable();g.setColor(panel);g.setCornerRadius(dp(16));g.setStroke(dp(1),line);x.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));x.setLayoutParams(lp);return x;}
    private TextView statusRow(String k,String v,int color){TextView t=text(k+"\n"+v,13,color,true);GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(11,17,23));g.setCornerRadius(dp(12));g.setStroke(dp(1),line);t.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,0,0,dp(6));t.setLayoutParams(lp);return t;}
}
