from pathlib import Path

# Camera Auto Upload 0.8.5
# - Real crop/mask box for photo and PNG/image layers
# - Preserve live editor state across phone rotation
# - Respect Android status/navigation safe areas so editor tools stay tappable
# - Keep the existing camera transports and Flickr queue untouched

# -----------------------------------------------------------------------------
# OverlayProcessor: persist non-destructive crop/mask rectangles on image layers
# and the main photo. Rendering clips instead of stretching cropped artwork.
# -----------------------------------------------------------------------------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayProcessor.java')
s=p.read_text()

old='''        public float x=.08f,y=.82f,w=.32f,h=.12f,rotation=0f,alpha=1f,textSize=.055f;\n        public int color=Color.WHITE;'''
new='''        public float x=.08f,y=.82f,w=.32f,h=.12f,rotation=0f,alpha=1f,textSize=.055f;\n        public float clipL=0f,clipT=0f,clipR=1f,clipB=1f;\n        public int color=Color.WHITE;'''
if old not in s: raise SystemExit('0.8.5 element clip fields target missing')
s=s.replace(old,new,1)

old='''            o.put("x",x).put("y",y).put("w",w).put("h",h).put("rotation",rotation).put("alpha",alpha).put("textSize",textSize);'''
new='''            o.put("x",x).put("y",y).put("w",w).put("h",h).put("rotation",rotation).put("alpha",alpha).put("textSize",textSize).put("clipL",clipL).put("clipT",clipT).put("clipR",clipR).put("clipB",clipB);'''
if old not in s: raise SystemExit('0.8.5 element clip JSON write target missing')
s=s.replace(old,new,1)

old='''e.x=(float)o.optDouble("x",.08);e.y=(float)o.optDouble("y",.82);e.w=(float)o.optDouble("w",.32);e.h=(float)o.optDouble("h",.12);e.rotation=(float)o.optDouble("rotation",0);e.alpha=(float)o.optDouble("alpha",1);e.textSize=(float)o.optDouble("textSize",.055);'''
new='''e.x=(float)o.optDouble("x",.08);e.y=(float)o.optDouble("y",.82);e.w=(float)o.optDouble("w",.32);e.h=(float)o.optDouble("h",.12);e.rotation=(float)o.optDouble("rotation",0);e.alpha=(float)o.optDouble("alpha",1);e.textSize=(float)o.optDouble("textSize",.055);e.clipL=(float)o.optDouble("clipL",0);e.clipT=(float)o.optDouble("clipT",0);e.clipR=(float)o.optDouble("clipR",1);e.clipB=(float)o.optDouble("clipB",1);'''
if old not in s: raise SystemExit('0.8.5 element clip JSON read target missing')
s=s.replace(old,new,1)

old='''        public float photoX=.5f,photoY=.5f,photoScale=1f,photoRotation=0f;\n        public final ArrayList<Element> elements=new ArrayList<>();'''
new='''        public float photoX=.5f,photoY=.5f,photoScale=1f,photoRotation=0f;\n        public float photoClipL=0f,photoClipT=0f,photoClipR=1f,photoClipB=1f;\n        public final ArrayList<Element> elements=new ArrayList<>();'''
if old not in s: raise SystemExit('0.8.5 photo clip fields target missing')
s=s.replace(old,new,1)

old='''.put("photoX",photoX).put("photoY",photoY).put("photoScale",photoScale).put("photoRotation",photoRotation);JSONArray a='''
new='''.put("photoX",photoX).put("photoY",photoY).put("photoScale",photoScale).put("photoRotation",photoRotation).put("photoClipL",photoClipL).put("photoClipT",photoClipT).put("photoClipR",photoClipR).put("photoClipB",photoClipB);JSONArray a='''
if old not in s: raise SystemExit('0.8.5 photo clip JSON write target missing')
s=s.replace(old,new,1)

old='''t.photoX=(float)o.optDouble("photoX",.5);t.photoY=(float)o.optDouble("photoY",.5);t.photoScale=(float)o.optDouble("photoScale",1);t.photoRotation=(float)o.optDouble("photoRotation",0);JSONArray a='''
new='''t.photoX=(float)o.optDouble("photoX",.5);t.photoY=(float)o.optDouble("photoY",.5);t.photoScale=(float)o.optDouble("photoScale",1);t.photoRotation=(float)o.optDouble("photoRotation",0);t.photoClipL=(float)o.optDouble("photoClipL",0);t.photoClipT=(float)o.optDouble("photoClipT",0);t.photoClipR=(float)o.optDouble("photoClipR",1);t.photoClipB=(float)o.optDouble("photoClipB",1);JSONArray a='''
if old not in s: raise SystemExit('0.8.5 photo clip JSON read target missing')
s=s.replace(old,new,1)

old='''canvas.save();canvas.rotate(t.photoRotation,pcx,pcy);canvas.drawBitmap(src,null,new RectF(pcx-pw/2f,pcy-ph/2f,pcx+pw/2f,pcy+ph/2f),photoPaint);canvas.restore();'''
new='''RectF photoDst=new RectF(pcx-pw/2f,pcy-ph/2f,pcx+pw/2f,pcy+ph/2f);canvas.save();canvas.rotate(t.photoRotation,pcx,pcy);RectF photoClip=new RectF(photoDst.left+t.photoClipL*photoDst.width(),photoDst.top+t.photoClipT*photoDst.height(),photoDst.left+t.photoClipR*photoDst.width(),photoDst.top+t.photoClipB*photoDst.height());canvas.clipRect(photoClip);canvas.drawBitmap(src,null,photoDst,photoPaint);canvas.restore();'''
if old not in s: raise SystemExit('0.8.5 photo render clip target missing')
s=s.replace(old,new,1)

old='''RectF dst=new RectF((e.x-e.w/2f)*w,(e.y-e.h/2f)*h,(e.x+e.w/2f)*w,(e.y+e.h/2f)*h);canvas.drawBitmap(img,null,dst,paint);img.recycle();'''
new='''RectF dst=new RectF((e.x-e.w/2f)*w,(e.y-e.h/2f)*h,(e.x+e.w/2f)*w,(e.y+e.h/2f)*h);RectF clip=new RectF(dst.left+e.clipL*dst.width(),dst.top+e.clipT*dst.height(),dst.left+e.clipR*dst.width(),dst.top+e.clipB*dst.height());canvas.clipRect(clip);canvas.drawBitmap(img,null,dst,paint);img.recycle();'''
if old not in s: raise SystemExit('0.8.5 image render clip target missing')
s=s.replace(old,new,1)
p.write_text(s)

# -----------------------------------------------------------------------------
# Full replacement of the touch editor. The photo is a first-class selectable
# layer. Crop is a non-destructive visible-area mask drawn directly on canvas.
# -----------------------------------------------------------------------------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/TouchTemplateEditorActivity.java')
p.write_text(r'''package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.File;
import java.util.*;

/** Photo-first, direct-touch sports-card/photo editor. */
public class TouchTemplateEditorActivity extends Activity {
    private static final int REQ_PHOTO=901,REQ_PNG=902,REQ_LOGO=903;
    private static final int SEL_NONE=-1,SEL_PHOTO=-2;
    private final int bg=Color.rgb(5,8,12),panel=Color.rgb(18,24,31),white=Color.rgb(245,247,250),muted=Color.rgb(160,170,181),blue=Color.rgb(33,150,243),green=Color.rgb(55,206,108),red=Color.rgb(239,83,80),amber=Color.rgb(255,193,7);
    private SharedPreferences p;
    private OverlayProcessor.Template template;
    private TouchCanvas canvas;
    private TextView title,status;
    private LinearLayout root;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);p=getSharedPreferences("settings",MODE_PRIVATE);loadTemplate();draw();
    }

    private void loadTemplate(){
        String id=getIntent().getStringExtra("template_id");
        if(id==null||id.isEmpty()){
            boolean land=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
            id=p.getString(land?OverlayProcessor.PREF_LANDSCAPE_TEMPLATE:OverlayProcessor.PREF_PORTRAIT_TEMPLATE,"");
        }
        template=id==null?null:OverlayProcessor.findTemplate(this,id);
        if(template==null){ArrayList<OverlayProcessor.Template> all=OverlayProcessor.loadTemplates(this);if(!all.isEmpty())template=all.get(0);}
        if(template==null){template=new OverlayProcessor.Template();template.name="My Photo Layout";template.category="Custom";template.orientation="portrait";}
    }

    private void draw(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(5),dp(8),dp(5));
        Button back=button("‹",Color.rgb(55,65,76));back.setTextSize(26);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(46)));
        title=text(template.name,17,white,true);title.setSingleLine(true);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button save=button("SAVE",green);save.setOnClickListener(v->saveTemplate(false));top.addView(save,new LinearLayout.LayoutParams(dp(72),dp(44)));
        Button lock=button(p.getBoolean("editor_layout_locked",false)?"UNLOCK":"LOCK",p.getBoolean("editor_layout_locked",false)?amber:red);lock.setOnClickListener(v->{boolean on=!p.getBoolean("editor_layout_locked",false);if(on)saveTemplate(true);else{p.edit().putBoolean("editor_layout_locked",false).apply();toast("Automatic editor layout unlocked");draw();}});top.addView(lock,new LinearLayout.LayoutParams(dp(82),dp(44)));root.addView(top);

        status=text("Tap PHOTO or any layer • drag • pinch • twist • CROP draws a keep-box",11,muted,false);status.setPadding(dp(10),dp(2),dp(10),dp(4));root.addView(status);
        canvas=new TouchCanvas(this);root.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));

        HorizontalScrollView row1=new HorizontalScrollView(this);row1.setHorizontalScrollBarEnabled(false);LinearLayout tools1=new LinearLayout(this);tools1.setPadding(dp(5),dp(4),dp(5),dp(4));row1.addView(tools1);root.addView(row1,new LinearLayout.LayoutParams(-1,dp(61)));
        tool(tools1,"PHOTO",blue,v->pick(REQ_PHOTO,"image/*"));
        tool(tools1,"PNG FRAME",green,v->pick(REQ_PNG,"image/png"));
        tool(tools1,"+ TEXT",blue,v->addCustomText());
        tool(tools1,"+ NAME",blue,v->addToken("{PLAYER_NAME}",.5f,.82f,.07f));
        tool(tools1,"+ #",blue,v->addToken("{NUMBER}",.82f,.82f,.09f));
        tool(tools1,"+ POSITION",blue,v->addToken("{POSITION}",.5f,.92f,.045f));
        tool(tools1,"+ LOGO",green,v->pick(REQ_LOGO,"image/*"));
        tool(tools1,"COLOR",Color.rgb(90,75,130),v->editColor());
        tool(tools1,"LAYERS",Color.rgb(70,80,90),v->showLayers());

        HorizontalScrollView row2=new HorizontalScrollView(this);row2.setHorizontalScrollBarEnabled(false);LinearLayout tools2=new LinearLayout(this);tools2.setPadding(dp(5),dp(3),dp(5),dp(5));row2.addView(tools2);root.addView(row2,new LinearLayout.LayoutParams(-1,dp(60)));
        tool(tools2,"UNDO",Color.rgb(70,80,90),v->undo());
        tool(tools2,"REDO",Color.rgb(70,80,90),v->redo());
        tool(tools2,"CROP",amber,v->beginCrop());
        tool(tools2,"RESET CROP",Color.rgb(70,80,90),v->resetCrop());
        tool(tools2,"FIT PHOTO",Color.rgb(70,80,90),v->fitPhoto());
        tool(tools2,"FRONT",Color.rgb(70,80,90),v->moveLayer(1));
        tool(tools2,"BACK",Color.rgb(70,80,90),v->moveLayer(-1));
        tool(tools2,"COPY",amber,v->duplicateSelected());
        tool(tools2,"DELETE",red,v->deleteSelected());

        setContentView(root);applySafeInsets();
        String saved=p.getString("editor_preview_uri","");if(!saved.isEmpty())canvas.post(()->canvas.loadPhoto(Uri.parse(saved),false));
    }

    private void applySafeInsets(){
        if(Build.VERSION.SDK_INT>=20){root.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;});root.requestApplyInsets();}
    }

    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(canvas!=null){canvas.requestLayout();canvas.invalidate();}if(root!=null)root.requestApplyInsets();}

    private void tool(LinearLayout row,String label,int color,View.OnClickListener l){Button b=button(label,color);b.setOnClickListener(l);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(label.length()>8?112:92),dp(50));lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);}
    private void pick(int req,String type){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,req);}

    private void saveTemplate(boolean lock){
        OverlayProcessor.saveTemplate(this,template);String key="portrait".equals(template.orientation)?OverlayProcessor.PREF_PORTRAIT_TEMPLATE:OverlayProcessor.PREF_LANDSCAPE_TEMPLATE;SharedPreferences.Editor e=p.edit().putString(key,template.id);if(lock)e.putBoolean("editor_layout_locked",true).putBoolean(OverlayProcessor.PREF_AUTO,true);e.apply();toast(lock?"Layout locked — incoming JPEGs can use it automatically":"Template saved");if(lock)draw();
    }

    private void pushUndo(){try{undo.push(template.toJson().toString());while(undo.size()>40)undo.removeLast();redo.clear();}catch(Exception ignored){}}
    private void restore(String json){try{template=OverlayProcessor.Template.fromJson(new JSONObject(json));OverlayProcessor.saveTemplate(this,template);canvas.clearSelection();canvas.invalidate();title.setText(template.name);}catch(Exception e){toast("Could not restore edit");}}
    private void undo(){if(undo.isEmpty()){toast("Nothing to undo");return;}try{redo.push(template.toJson().toString());}catch(Exception ignored){}restore(undo.pop());}
    private void redo(){if(redo.isEmpty()){toast("Nothing to redo");return;}try{undo.push(template.toJson().toString());}catch(Exception ignored){}restore(redo.pop());}

    private void addToken(String token,float x,float y,float size){pushUndo();OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="text";e.text=token;e.x=x;e.y=y;e.textSize=size;e.align="center";e.bold=true;template.elements.add(e);canvas.select(template.elements.size()-1);}
    private void addCustomText(){EditText e=input("Text","Text");new AlertDialog.Builder(this).setTitle("Add text").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Add",(d,w)->{pushUndo();OverlayProcessor.Element x=new OverlayProcessor.Element();x.type="text";x.text=e.getText().toString();x.x=.5f;x.y=.5f;x.textSize=.06f;x.align="center";template.elements.add(x);canvas.select(template.elements.size()-1);}).show();}
    void editSelectedText(){OverlayProcessor.Element x=canvas.selectedElement();if(x==null||!"text".equals(x.type))return;EditText e=input("Text or token",x.text);new AlertDialog.Builder(this).setTitle("Edit text").setMessage("Tokens: {PLAYER_NAME}, {NUMBER}, {POSITION}, {TEAM}, {EVENT}, {DATE}, {PHOTOGRAPHER}").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{pushUndo();x.text=e.getText().toString();canvas.invalidate();}).show();}

    private void editColor(){OverlayProcessor.Element e=canvas.selectedElement();if(e==null){toast("Tap a text, shape, logo, or PNG layer first");return;}if("image".equals(e.type)){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);EditText hex=input("Tint #RRGGBB",String.format(Locale.US,"#%06X",0xFFFFFF&e.tintColor));box.addView(hex);SeekBar amount=new SeekBar(this);amount.setMax(100);amount.setProgress(Math.round(e.tint*100));box.addView(amount);new AlertDialog.Builder(this).setTitle("Recolor image layer").setView(box).setNegativeButton("Cancel",null).setNeutralButton("Clear",(d,w)->{pushUndo();e.tint=0;e.tintColor=Color.WHITE;canvas.invalidate();}).setPositiveButton("Apply",(d,w)->{try{pushUndo();e.tintColor=Color.parseColor(hex.getText().toString().trim());e.tint=amount.getProgress()/100f;canvas.invalidate();}catch(Exception ex){toast("Use a color like #CC2027");}}).show();return;}EditText hex=input("Color #RRGGBB",String.format(Locale.US,"#%06X",0xFFFFFF&e.color));new AlertDialog.Builder(this).setTitle("Layer color").setView(hex).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{try{pushUndo();e.color=Color.parseColor(hex.getText().toString().trim());canvas.invalidate();}catch(Exception ex){toast("Use a color like #FFFFFF");}}).show();}

    private void beginCrop(){if(canvas.selected==SEL_NONE){toast("Tap PHOTO or an image/PNG layer first");return;}if(canvas.selected>=0){OverlayProcessor.Element e=canvas.selectedElement();if(e==null||!"image".equals(e.type)){toast("Crop works on PHOTO, PNG frames, logos, and image layers");return;}}canvas.cropMode=true;status.setText("CROP MODE • drag a box over the part you WANT TO KEEP");canvas.invalidate();}
    private void resetCrop(){if(canvas.selected==SEL_PHOTO){pushUndo();template.photoClipL=0;template.photoClipT=0;template.photoClipR=1;template.photoClipB=1;canvas.invalidate();toast("Photo crop reset");return;}OverlayProcessor.Element e=canvas.selectedElement();if(e==null||!"image".equals(e.type)){toast("Select an image layer first");return;}pushUndo();e.clipL=0;e.clipT=0;e.clipR=1;e.clipB=1;canvas.invalidate();toast("Image crop reset");}
    private void fitPhoto(){pushUndo();template.photoX=.5f;template.photoY=.5f;template.photoScale=1f;template.photoRotation=0f;template.photoClipL=0;template.photoClipT=0;template.photoClipR=1;template.photoClipB=1;canvas.select(SEL_PHOTO);}
    private void moveLayer(int direction){if(canvas.selected<0)return;int from=canvas.selected,to=Math.max(0,Math.min(template.elements.size()-1,from+direction));if(from==to)return;pushUndo();OverlayProcessor.Element e=template.elements.remove(from);template.elements.add(to,e);canvas.select(to);}
    private void duplicateSelected(){OverlayProcessor.Element e=canvas.selectedElement();if(e==null)return;pushUndo();OverlayProcessor.Element c=e.copy();c.id=UUID.randomUUID().toString();c.x+=.025f;c.y+=.025f;template.elements.add(c);canvas.select(template.elements.size()-1);}
    private void deleteSelected(){if(canvas.selected==SEL_PHOTO){toast("The base photo is kept. Use FIT PHOTO or choose another photo.");return;}if(canvas.selected<0)return;pushUndo();template.elements.remove(canvas.selected);canvas.clearSelection();toast("Layer deleted");}
    private void showLayers(){ArrayList<String> names=new ArrayList<>();names.add(canvas.selected==SEL_PHOTO?"✓ PHOTO":"PHOTO");for(int i=0;i<template.elements.size();i++){OverlayProcessor.Element e=template.elements.get(i);String n="text".equals(e.type)?displayText(e.text):("image".equals(e.type)?"PNG / IMAGE":"SHAPE");names.add((i==canvas.selected?"✓ ":"")+n);}new AlertDialog.Builder(this).setTitle("Layers — bottom to top").setItems(names.toArray(new String[0]),(d,i)->{if(i==0)canvas.select(SEL_PHOTO);else canvas.select(i-1);}).setNegativeButton("Close",null).show();}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(req==REQ_PHOTO){p.edit().putString("editor_preview_uri",u.toString()).apply();canvas.loadPhoto(u,true);canvas.select(SEL_PHOTO);return;}pushUndo();String path=OverlayProcessor.copyAssetIntoModule(this,u,req==REQ_PNG?"template.png":"logo.png");OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="image";e.assetPath=path;e.x=.5f;e.y=.5f;e.w=req==REQ_PNG?1f:.25f;e.h=req==REQ_PNG?1f:.25f;e.alpha=1f;e.tint=0f;if(req==REQ_PNG)template.elements.add(0,e);else template.elements.add(e);canvas.select(req==REQ_PNG?0:template.elements.size()-1);}catch(Exception ex){toast("Could not add image: "+ex.getMessage());}}

    private String displayText(String s){String x=s==null?"":s;String player=p.getString("overlay_player","");if(player.isEmpty())player="PLAYER NAME";String number=p.getString("overlay_number","");if(number.isEmpty())number="00";String position=p.getString("overlay_position","");if(position.isEmpty())position="POSITION";String team=p.getString("overlay_team","");if(team.isEmpty())team="TEAM";String event=p.getString("overlay_event","");if(event.isEmpty())event="EVENT";return x.replace("{PLAYER_NAME}",player).replace("{NUMBER}",number).replace("{POSITION}",position).replace("{TEAM}",team).replace("{EVENT}",event).replace("{PHOTOGRAPHER}",p.getString("overlay_photographer","PHOTOGRAPHER")).replace("{DATE}","DATE");}

    private final class TouchCanvas extends View {
        int selected=SEL_NONE;boolean cropMode=false,cropDrawing=false,gestureChanged=false,pinching=false,canvasGesture=false;Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);RectF card=new RectF(),cropPreview=new RectF();Bitmap photo;Map<String,Bitmap> cache=new HashMap<>();float viewZoom=1f,panX=0,panY=0,lastX,lastY,startDistance,startAngle,startW,startH,startText,startRotation,startScale,startPhotoRot,startPhotoX,startPhotoY,startZoom,startMidX,startMidY,cropU0,cropV0,cropU1,cropV1;long lastTap=0;
        TouchCanvas(Context c){super(c);setBackgroundColor(Color.rgb(9,13,18));}
        void clearSelection(){selected=SEL_NONE;cropMode=false;invalidate();status.setText("Tap PHOTO or any layer to edit it directly");}
        void select(int i){selected=i;cropMode=false;invalidate();status.setText(i==SEL_PHOTO?"Selected: PHOTO • drag • pinch • rotate • CROP":"Selected layer • drag • pinch • rotate • CROP for image layers");}
        OverlayProcessor.Element selectedElement(){return selected>=0&&selected<template.elements.size()?template.elements.get(selected):null;}
        void loadPhoto(Uri u,boolean message){try{ImageDecoder.Source src=ImageDecoder.createSource(getContentResolver(),u);photo=ImageDecoder.decodeBitmap(src,(d,info,s)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();double sc=Math.min(1d,Math.sqrt(5000000d/Math.max(1d,(double)w*h)));if(sc<.999)d.setTargetSize(Math.max(1,(int)(w*sc)),Math.max(1,(int)(h*sc)));d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});invalidate();if(message)status.setText("Photo loaded • tap it, drag it, pinch it, rotate it, or crop it");}catch(Exception e){if(message)toast("Could not open photo");}}
        private void cardRect(){float pad=dp(10),aw=Math.max(1,getWidth()-pad*2),ah=Math.max(1,getHeight()-pad*2),ratio="portrait".equals(template.orientation)?.70f:1.50f,w=aw,h=w/ratio;if(h>ah){h=ah;w=h*ratio;}float cx=getWidth()/2f+panX,cy=getHeight()/2f+panY;w*=viewZoom;h*=viewZoom;card.set(cx-w/2,cy-h/2,cx+w/2,cy+h/2);}
        private RectF photoBounds(){if(photo==null)return new RectF(card);float cover=Math.max(card.width()/photo.getWidth(),card.height()/photo.getHeight());float w=photo.getWidth()*cover*Math.max(.15f,template.photoScale),h=photo.getHeight()*cover*Math.max(.15f,template.photoScale);float cx=card.left+template.photoX*card.width(),cy=card.top+template.photoY*card.height();return new RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);cardRect();paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(26,33,41));c.drawRect(card,paint);c.save();c.clipRect(card);drawPhoto(c);drawElements(c);c.restore();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(Color.rgb(80,90,100));c.drawRect(card,paint);}
        private void drawPhoto(Canvas c){if(photo==null){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(40,49,59));c.drawRect(card,paint);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(18));paint.setColor(Color.LTGRAY);c.drawText("TAP PHOTO TO CHOOSE AN IMAGE",card.centerX(),card.centerY(),paint);return;}RectF d=photoBounds();float cx=d.centerX(),cy=d.centerY();c.save();c.rotate(template.photoRotation,cx,cy);RectF clip=new RectF(d.left+template.photoClipL*d.width(),d.top+template.photoClipT*d.height(),d.left+template.photoClipR*d.width(),d.top+template.photoClipB*d.height());c.clipRect(clip);paint.setAlpha(255);paint.setColorFilter(null);c.drawBitmap(photo,null,d,paint);c.restore();if(selected==SEL_PHOTO)drawSelection(c,d,template.photoRotation,template.photoClipL,template.photoClipT,template.photoClipR,template.photoClipB);}
        private void drawElements(Canvas c){float w=card.width(),h=card.height(),base=Math.min(w,h);for(int i=0;i<template.elements.size();i++){OverlayProcessor.Element e=template.elements.get(i);if(e==null||!e.visible)continue;float x=card.left+e.x*w,y=card.top+e.y*h;c.save();c.rotate(e.rotation,x,y);paint.setAlpha(Math.max(0,Math.min(255,(int)(255*e.alpha))));paint.setColorFilter(null);if("text".equals(e.type)){paint.setStyle(Paint.Style.FILL);paint.setColor(e.color);paint.setTextSize(Math.max(dp(10),e.textSize*base));paint.setTypeface(Typeface.create(e.font,e.bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextAlign("right".equals(e.align)?Paint.Align.RIGHT:("center".equals(e.align)?Paint.Align.CENTER:Paint.Align.LEFT));String[] lines=displayText(e.text).split("\\n",-1);float line=paint.getTextSize()*1.08f;for(int k=0;k<lines.length;k++)c.drawText(lines[k],x,y+k*line,paint);}else if("image".equals(e.type)){Bitmap b=image(e.assetPath);if(b!=null){if(e.tint>0.001f)paint.setColorFilter(OverlayProcessor.tintFilter(e.tintColor,e.tint));RectF d=new RectF(x-e.w*w/2,y-e.h*h/2,x+e.w*w/2,y+e.h*h/2);RectF clip=new RectF(d.left+e.clipL*d.width(),d.top+e.clipT*d.height(),d.left+e.clipR*d.width(),d.top+e.clipB*d.height());c.clipRect(clip);c.drawBitmap(b,null,d,paint);paint.setColorFilter(null);}}else{paint.setColor(e.color);paint.setStyle(e.fill?Paint.Style.FILL:Paint.Style.STROKE);paint.setStrokeWidth(Math.max(dp(2),base*.005f));c.drawRect(x,y,x+e.w*w,y+e.h*h,paint);}c.restore();if(i==selected){RectF b=bounds(e,x,y,w,h);drawSelection(c,b,e.rotation,e.clipL,e.clipT,e.clipR,e.clipB);}}paint.setAlpha(255);paint.setColorFilter(null);}
        private void drawSelection(Canvas c,RectF b,float rot,float cl,float ct,float cr,float cb){c.save();c.rotate(rot,b.centerX(),b.centerY());paint.setAlpha(255);paint.setColor(amber);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));c.drawRect(b,paint);if(cropMode){RectF r=cropDrawing?new RectF(b.left+Math.min(cropU0,cropU1)*b.width(),b.top+Math.min(cropV0,cropV1)*b.height(),b.left+Math.max(cropU0,cropU1)*b.width(),b.top+Math.max(cropV0,cropV1)*b.height()):new RectF(b.left+cl*b.width(),b.top+ct*b.height(),b.left+cr*b.width(),b.top+cb*b.height());paint.setColor(Color.WHITE);paint.setStrokeWidth(dp(2));c.drawRect(r,paint);paint.setColor(Color.argb(120,0,0,0));paint.setStyle(Paint.Style.FILL);c.drawRect(b.left,b.top,b.right,r.top,paint);c.drawRect(b.left,r.bottom,b.right,b.bottom,paint);c.drawRect(b.left,r.top,r.left,r.bottom,paint);c.drawRect(r.right,r.top,b.right,r.bottom,paint);}c.restore();}
        private Bitmap image(String path){if(path==null||path.isEmpty())return null;if(cache.containsKey(path))return cache.get(path);Bitmap b=new File(path).exists()?BitmapFactory.decodeFile(path):null;if(b!=null)cache.put(path,b);return b;}
        private RectF bounds(OverlayProcessor.Element e,float x,float y,float w,float h){if("text".equals(e.type)){paint.setTextSize(Math.max(dp(10),e.textSize*Math.min(w,h)));float tw=0;for(String line:displayText(e.text).split("\\n",-1))tw=Math.max(tw,paint.measureText(line));float th=paint.getTextSize()*1.35f;if("right".equals(e.align))return new RectF(x-tw,y-th,x,y+dp(8));if("center".equals(e.align))return new RectF(x-tw/2,y-th,x+tw/2,y+dp(8));return new RectF(x,y-th,x+tw,y+dp(8));}if("image".equals(e.type))return new RectF(x-e.w*w/2,y-e.h*h/2,x+e.w*w/2,y+e.h*h/2);return new RectF(x,y,x+e.w*w,y+e.h*h);}
        private int hit(float px,float py){float w=card.width(),h=card.height();for(int i=template.elements.size()-1;i>=0;i--){OverlayProcessor.Element e=template.elements.get(i);if(e==null||!e.visible)continue;float x=card.left+e.x*w,y=card.top+e.y*h;RectF b=bounds(e,x,y,w,h);PointF q=unrotate(px,py,b.centerX(),b.centerY(),e.rotation);b.inset(-dp(18),-dp(18));if(b.contains(q.x,q.y))return i;}if(card.contains(px,py))return SEL_PHOTO;return SEL_NONE;}
        private PointF unrotate(float x,float y,float cx,float cy,float deg){double a=Math.toRadians(-deg),dx=x-cx,dy=y-cy;return new PointF((float)(cx+dx*Math.cos(a)-dy*Math.sin(a)),(float)(cy+dx*Math.sin(a)+dy*Math.cos(a)));}
        private float[] cropPoint(float sx,float sy){RectF b;float rot;if(selected==SEL_PHOTO){b=photoBounds();rot=template.photoRotation;}else{OverlayProcessor.Element e=selectedElement();if(e==null)return null;float x=card.left+e.x*card.width(),y=card.top+e.y*card.height();b=bounds(e,x,y,card.width(),card.height());rot=e.rotation;}PointF q=unrotate(sx,sy,b.centerX(),b.centerY(),rot);return new float[]{clamp((q.x-b.left)/Math.max(1,b.width()),0,1),clamp((q.y-b.top)/Math.max(1,b.height()),0,1)};}
        private void applyCrop(){float l=Math.min(cropU0,cropU1),r=Math.max(cropU0,cropU1),t=Math.min(cropV0,cropV1),b=Math.max(cropV0,cropV1);if(r-l<.03f||b-t<.03f){toast("Draw a larger crop box");cropMode=false;cropDrawing=false;invalidate();return;}pushUndo();if(selected==SEL_PHOTO){template.photoClipL=l;template.photoClipT=t;template.photoClipR=r;template.photoClipB=b;}else{OverlayProcessor.Element e=selectedElement();if(e!=null){e.clipL=l;e.clipT=t;e.clipR=r;e.clipB=b;}}cropMode=false;cropDrawing=false;status.setText("Crop applied • RESET CROP restores the whole image");invalidate();}
        @Override public boolean onTouchEvent(MotionEvent ev){cardRect();if(cropMode){if(ev.getActionMasked()==MotionEvent.ACTION_DOWN){float[]q=cropPoint(ev.getX(),ev.getY());if(q==null)return true;cropU0=cropU1=q[0];cropV0=cropV1=q[1];cropDrawing=true;invalidate();return true;}if(ev.getActionMasked()==MotionEvent.ACTION_MOVE&&cropDrawing){float[]q=cropPoint(ev.getX(),ev.getY());if(q!=null){cropU1=q[0];cropV1=q[1];invalidate();}return true;}if(ev.getActionMasked()==MotionEvent.ACTION_UP&&cropDrawing){float[]q=cropPoint(ev.getX(),ev.getY());if(q!=null){cropU1=q[0];cropV1=q[1];}applyCrop();return true;}return true;}
            switch(ev.getActionMasked()){
                case MotionEvent.ACTION_DOWN:{selected=hit(ev.getX(),ev.getY());lastX=ev.getX();lastY=ev.getY();gestureChanged=false;pinching=false;canvasGesture=(selected==SEL_NONE);if(selected!=SEL_NONE)pushUndo();invalidate();return true;}
                case MotionEvent.ACTION_POINTER_DOWN:{if(ev.getPointerCount()>=2){pinching=true;startDistance=distance(ev);startAngle=angle(ev);startMidX=(ev.getX(0)+ev.getX(1))/2f;startMidY=(ev.getY(0)+ev.getY(1))/2f;if(selected==SEL_PHOTO){startScale=template.photoScale;startPhotoRot=template.photoRotation;startPhotoX=template.photoX;startPhotoY=template.photoY;}else{OverlayProcessor.Element e=selectedElement();if(e!=null&&!canvasGesture){startW=e.w;startH=e.h;startText=e.textSize;startRotation=e.rotation;}else{canvasGesture=true;startZoom=viewZoom;}}}return true;}
                case MotionEvent.ACTION_MOVE:{gestureChanged=true;if(ev.getPointerCount()>=2&&pinching){float scale=startDistance<=0?1:distance(ev)/startDistance;if(selected==SEL_PHOTO&&!canvasGesture){template.photoScale=clamp(startScale*scale,.15f,6f);template.photoRotation=startPhotoRot+(angle(ev)-startAngle);invalidate();return true;}OverlayProcessor.Element e=selectedElement();if(e!=null&&!canvasGesture){e.rotation=startRotation+(angle(ev)-startAngle);if("text".equals(e.type))e.textSize=clamp(startText*scale,.01f,.35f);else{e.w=clamp(startW*scale,.02f,3f);e.h=clamp(startH*scale,.02f,3f);}invalidate();return true;}viewZoom=clamp(startZoom*scale,.5f,5f);float mx=(ev.getX(0)+ev.getX(1))/2f,my=(ev.getY(0)+ev.getY(1))/2f;panX+=mx-startMidX;panY+=my-startMidY;startMidX=mx;startMidY=my;invalidate();return true;}if(selected==SEL_PHOTO&&!canvasGesture){template.photoX=clamp(template.photoX+(ev.getX()-lastX)/Math.max(1,card.width()),-.75f,1.75f);template.photoY=clamp(template.photoY+(ev.getY()-lastY)/Math.max(1,card.height()),-.75f,1.75f);lastX=ev.getX();lastY=ev.getY();invalidate();return true;}OverlayProcessor.Element e=selectedElement();if(e!=null&&!canvasGesture){e.x=clamp(e.x+(ev.getX()-lastX)/Math.max(1,card.width()),-.75f,1.75f);e.y=clamp(e.y+(ev.getY()-lastY)/Math.max(1,card.height()),-.75f,1.75f);lastX=ev.getX();lastY=ev.getY();invalidate();}else if(viewZoom>1f){panX+=ev.getX()-lastX;panY+=ev.getY()-lastY;lastX=ev.getX();lastY=ev.getY();invalidate();}return true;}
                case MotionEvent.ACTION_POINTER_UP:pinching=false;return true;
                case MotionEvent.ACTION_UP:{long now=System.currentTimeMillis();if(!gestureChanged&&selected>=0){if(now-lastTap<360){OverlayProcessor.Element e=selectedElement();if(e!=null&&"text".equals(e.type))editSelectedText();lastTap=0;}else lastTap=now;}return true;}
            }return true;
        }
        private float distance(MotionEvent e){float dx=e.getX(1)-e.getX(0),dy=e.getY(1)-e.getY(0);return (float)Math.hypot(dx,dy);}
        private float angle(MotionEvent e){return (float)Math.toDegrees(Math.atan2(e.getY(1)-e.getY(0),e.getX(1)-e.getX(0)));}
    }

    private float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setBackgroundColor(color);b.setPadding(dp(5),0,dp(5),0);return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.GRAY);e.setTextColor(Color.WHITE);e.setText(value);e.setSingleLine(false);e.setPadding(dp(12),dp(8),dp(12),dp(8));return e;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
''')

# Keep the Activity alive across orientation changes so its loaded preview bitmap,
# selection, crop mode, transforms, undo stack, and unsaved edits rotate in place.
p=Path('nikon-auto-upload/app/src/main/AndroidManifest.xml')
s=p.read_text()
old='''        <activity android:name=".TouchTemplateEditorActivity" android:exported="false"/>'''
new='''        <activity android:name=".TouchTemplateEditorActivity" android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden"/>'''
if old not in s: raise SystemExit('0.8.5 editor manifest target missing')
s=s.replace(old,new,1)
p.write_text(s)

# Version after 0.8.4.
p=Path('nikon-auto-upload/app/build.gradle')
s=p.read_text().replace('versionCode 29','versionCode 30').replace("versionName '0.8.4'","versionName '0.8.5'")
p.write_text(s)

# User-facing help.
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.8.4 beta','Version 0.8.5 beta')
    s+='''\n\nEDITOR CROP + ROTATION + SAFE AREA (0.8.5)\n-----------------------------------------\nCROP is a non-destructive mask. Select PHOTO or a PNG/image layer, tap CROP, then drag a box around the part you want to keep. Everything outside the box is hidden without stretching the artwork. RESET CROP restores the full layer.\n\nRotating the Android phone no longer recreates the editor. The loaded preview photo, current layer positions, scale/rotation, crop, selection, and undo/redo state remain in place while the interface rotates with the phone.\n\nThe editor now respects Android status-bar and navigation-bar safe areas so the top controls are below the status area and the bottom tools stay above Back/Home/Recents or gesture navigation.\n'''
    p.write_text(s)
