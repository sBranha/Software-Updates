from pathlib import Path

# Camera Auto Upload 0.8.1
# - Finish the visible Photo Overlay / Sports Card template-builder workflow
# - Import a user's full-size transparent PNG as a reusable template base
# - Add editable element color and filled-shape controls
# - Add Phone Camera as another independent photo source
# Camera transports and Flickr internals are intentionally not replaced here.

# ----- MainActivity: add Phone Camera beside the overlay entry created by 0.8.0 -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text()
needle='''        Button overlays=smallButton("PHOTO OVERLAYS / SPORTS CARDS",blue);overlays.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));controls.addView(overlays);'''
replacement=needle+'''\n        Button phoneCamera=smallButton("PHONE CAMERA",green);phoneCamera.setOnClickListener(v->startActivity(new Intent(this,PhoneCameraActivity.class)));controls.addView(phoneCamera);'''
if needle not in s: raise SystemExit('0.8.1 Home overlay entry target missing')
s=s.replace(needle,replacement,1)

needle='''        Button overlayMethod=smallButton("PHOTO OVERLAYS / SPORTS CARDS",blue);overlayMethod.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));methods.addView(overlayMethod);'''
replacement=needle+'''\n        Button phoneMethod=smallButton("PHONE CAMERA",green);phoneMethod.setOnClickListener(v->startActivity(new Intent(this,PhoneCameraActivity.class)));methods.addView(phoneMethod);'''
if needle in s:s=s.replace(needle,replacement,1)
s=s.replace('Nikon Auto Upload 0.8.0','Camera Auto Upload 0.8.1').replace('Version 0.8.0 beta','Version 0.8.1 beta')
p.write_text(s)

# ----- Overlay Studio: imported base template + color/fill controls -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayStudioActivity.java')
s=p.read_text()
s=s.replace('private static final int REQ_LOGO=301,REQ_PREVIEW=302;','private static final int REQ_LOGO=301,REQ_PREVIEW=302,REQ_BASE=303;',1)

needle='''        EditText name=input("Template name",editingTemplate.name);body.addView(name);Spinner orient=new Spinner(this);orient.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"landscape","portrait"}));orient.setSelection("portrait".equals(editingTemplate.orientation)?1:0);body.addView(orient,new LinearLayout.LayoutParams(-1,dp(50)));\n        editorView=new EditorView(this,editingTemplate);body.addView(editorView,new LinearLayout.LayoutParams(-1,dp(420)));'''
replacement='''        EditText name=input("Template name",editingTemplate.name);body.addView(name);Spinner orient=new Spinner(this);orient.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"landscape","portrait"}));orient.setSelection("portrait".equals(editingTemplate.orientation)?1:0);body.addView(orient,new LinearLayout.LayoutParams(-1,dp(50)));\n        Button importBase=button("IMPORT YOUR PNG TEMPLATE",green);importBase.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_BASE);});body.addView(importBase,new LinearLayout.LayoutParams(-1,dp(54)));\n        body.addView(text("Use a transparent PNG from Canva, Photoshop, etc. Leave spaces such as a circle for the player number, then place editable fields over those spots.",12,muted,false));\n        editorView=new EditorView(this,editingTemplate);body.addView(editorView,new LinearLayout.LayoutParams(-1,dp(420)));'''
if needle not in s: raise SystemExit('0.8.1 template import insertion target missing')
s=s.replace(needle,replacement,1)

needle='''        CheckBox bold=new CheckBox(this);bold.setText("Bold text");bold.setTextColor(white);bold.setOnCheckedChangeListener((b,on)->{OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e!=null&&"text".equals(e.type)){e.bold=on;editorView.invalidate();}});select.addView(bold);'''
replacement=needle+'''\n        Button elementColor=button("ELEMENT COLOR / HEX",Color.rgb(70,80,90));elementColor.setOnClickListener(v->chooseElementColor());select.addView(elementColor,new LinearLayout.LayoutParams(-1,dp(50)));\n        CheckBox fillShape=new CheckBox(this);fillShape.setText("Fill selected shape");fillShape.setTextColor(white);fillShape.setOnCheckedChangeListener((b,on)->{OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e!=null&&"rect".equals(e.type)){e.fill=on;editorView.invalidate();}});select.addView(fillShape);'''
if needle not in s: raise SystemExit('0.8.1 element-color target missing')
s=s.replace(needle,replacement,1)

needle='''        editorView.selectionChanged=()->{OverlayProcessor.Element e=editorView.selectedElement();if(e!=null){bold.setChecked(e.bold);opacity.setProgress((int)(e.alpha*100));size.setProgress("text".equals(e.type)?Math.max(0,Math.min(100,(int)((e.textSize-.018f)/.12f*100))):Math.max(0,Math.min(100,(int)((e.w-.06f)/.75f*100))));}};'''
replacement='''        editorView.selectionChanged=()->{OverlayProcessor.Element e=editorView.selectedElement();if(e!=null){bold.setChecked(e.bold);fillShape.setChecked(e.fill);opacity.setProgress((int)(e.alpha*100));size.setProgress("text".equals(e.type)?Math.max(0,Math.min(100,(int)((e.textSize-.018f)/.12f*100))):Math.max(0,Math.min(100,(int)((e.w-.06f)/.75f*100))));}};'''
if needle not in s: raise SystemExit('0.8.1 selected-control target missing')
s=s.replace(needle,replacement,1)

needle='''    private void editSelectedText(){OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e==null||!"text".equals(e.type)){toast("Select a text element first");return;}EditText x=input("Text / tokens",e.text);new AlertDialog.Builder(this).setTitle("Edit text").setMessage("Tokens: {PLAYER_NAME}, {NUMBER}, {TEAM}, {EVENT}, {DATE}, {PHOTOGRAPHER}").setView(x).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{e.text=x.getText().toString();editorView.invalidate();}).show();}\n'''
replacement=needle+'''    private void chooseElementColor(){OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e==null){toast("Select a text or shape element first");return;}if("image".equals(e.type)){toast("PNG colors come from the imported graphic");return;}EditText x=input("Hex color, for example #FFFFFF",String.format(Locale.US,"#%06X",(0xFFFFFF&e.color)));new AlertDialog.Builder(this).setTitle("Element color").setMessage("Enter any RGB hex color. This is saved with the template.").setView(x).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{try{e.color=Color.parseColor(x.getText().toString().trim());editorView.invalidate();}catch(Exception ex){toast("Use a color like #1E88E5");}}).show();}\n'''
if needle not in s: raise SystemExit('0.8.1 color helper target missing')
s=s.replace(needle,replacement,1)

needle='''    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(requestCode==REQ_LOGO&&editingTemplate!=null){try{String path=OverlayProcessor.copyAssetIntoModule(this,u,"overlay.png");OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="image";e.assetPath=path;e.x=.5f;e.y=.5f;e.w=.28f;e.h=.28f;editingTemplate.elements.add(e);editorView.selected=editingTemplate.elements.size()-1;editorView.invalidate();toast("Graphic added");}catch(Exception e){toast("Could not add graphic: "+e.getMessage());}}else if(requestCode==REQ_PREVIEW){previewSource=u;renderPreview();}}'''
replacement='''    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(requestCode==REQ_BASE&&editingTemplate!=null){try{String path=OverlayProcessor.copyAssetIntoModule(this,u,"template.png");OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="image";e.assetPath=path;e.x=.5f;e.y=.5f;e.w=1f;e.h=1f;editingTemplate.elements.add(0,e);editorView.selected=0;editorView.invalidate();toast("PNG template imported — add editable fields over it");}catch(Exception e){toast("Could not import template: "+e.getMessage());}}else if(requestCode==REQ_LOGO&&editingTemplate!=null){try{String path=OverlayProcessor.copyAssetIntoModule(this,u,"overlay.png");OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="image";e.assetPath=path;e.x=.5f;e.y=.5f;e.w=.28f;e.h=.28f;editingTemplate.elements.add(e);editorView.selected=editingTemplate.elements.size()-1;editorView.invalidate();toast("Graphic added");}catch(Exception e){toast("Could not add graphic: "+e.getMessage());}}else if(requestCode==REQ_PREVIEW){previewSource=u;renderPreview();}}'''
if needle not in s: raise SystemExit('0.8.1 onActivityResult target missing')
s=s.replace(needle,replacement,1)
p.write_text(s)

# ----- Phone Camera activity -----
phone=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/PhoneCameraActivity.java')
phone.write_text(r'''package com.nikonautoupload;

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
    private void kickUploader(){Intent i=new Intent(this,DirectTransferService.class);i.putExtra("command","retry_failed");if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private String displayName(Uri u){try(android.database.Cursor c=getContentResolver().query(u,new String[]{MediaStore.Images.Media.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "PhonePhoto.jpg";}
    private void cleanupCapture(){if(captureUri!=null){try{getContentResolver().delete(captureUri,null,null);}catch(Exception ignored){}captureUri=null;}}
    private String shortMessage(Throwable e){String s=e==null?"Unknown error":e.getMessage();return s==null?e.getClass().getSimpleName():s;}
    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(13),dp(13),dp(13),dp(13));android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(panel);g.setCornerRadius(dp(16));g.setStroke(dp(1),line);x.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(12));x.setLayoutParams(lp);return x;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(5),dp(6),dp(5),dp(6));if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(color));return b;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
''')

# ----- Manifest -----
p=Path('nikon-auto-upload/app/src/main/AndroidManifest.xml')
s=p.read_text()
if '.PhoneCameraActivity' not in s:
    marker='        <activity android:name=".OverlayStudioActivity" android:exported="false"/>\n'
    if marker not in s: raise SystemExit('0.8.1 manifest overlay target missing')
    s=s.replace(marker,marker+'        <activity android:name=".PhoneCameraActivity" android:exported="false"/>\n',1)
p.write_text(s)

# ----- Version metadata after 0.8.0 patch -----
p=Path('nikon-auto-upload/app/build.gradle')
s=p.read_text().replace('versionCode 25','versionCode 26').replace("versionName '0.8.0'","versionName '0.8.1'")
p.write_text(s)

# ----- Help -----
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.8.0 beta','Version 0.8.1 beta')
    s += '''\n\n0.8.1 TEMPLATE BUILDER + PHONE CAMERA\n-------------------------------------\nOverlay Studio is now a complete reachable template workflow. Import a transparent PNG made in Canva/Photoshop as a full-card base, then place editable fields such as {NUMBER}, {PLAYER_NAME}, {TEAM}, {EVENT}, {DATE}, and {PHOTOGRAPHER} over intentional blank areas in the design. Element colors accept any RGB hex value and shapes may be outlines or filled color blocks. Templates remain reusable and separate for portrait and landscape.\n\nPHONE CAMERA is an independent source for times when the main camera is not available. TAKE PHOTO WITH PHONE opens the Android phone camera at full-resolution output, saves the original to Pictures/Camera Auto Upload/Phone, then passes that saved JPEG through the same optional overlay/card and Flickr queue. The original remains untouched. Existing Nikon/Sony/Canon/Fujifilm transport code is not replaced by Phone Camera.\n'''
    p.write_text(s)
