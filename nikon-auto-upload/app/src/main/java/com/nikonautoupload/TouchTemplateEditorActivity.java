package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
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
        tool(tools1,"PROPERTIES",Color.rgb(75,90,125),v->openSelectedProperties());
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
    private String templateSnapshot(){try{return template.toJson().toString();}catch(Exception e){return "";}}
    private void rememberBefore(String json){if(json==null||json.isEmpty())return;undo.push(json);while(undo.size()>40)undo.removeLast();redo.clear();}
    private int colorOr(String s,int fallback){try{return Color.parseColor(s.trim());}catch(Exception e){return fallback;}}
    private void openSelectedProperties(){if(canvas.selected==SEL_PHOTO){editSelectedImage();return;}OverlayProcessor.Element e=canvas.selectedElement();if(e==null){toast("Tap text, a photo, PNG, or logo first");return;}if("text".equals(e.type))editSelectedText();else if("image".equals(e.type))editSelectedImage();else editColor();}

    void editSelectedText(){
        final int index=canvas.selected;final OverlayProcessor.Element x=canvas.selectedElement();if(x==null||!"text".equals(x.type))return;final OverlayProcessor.Element original=x.copy();final String before=templateSnapshot();final boolean[] applied={false};
        ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(6),dp(18),dp(8));scroll.addView(box);
        box.addView(text("TEXT",11,muted,true));EditText content=input("Text / token",x.text);box.addView(content);
        box.addView(text("FONT",11,muted,true));String[] fontNames={"Sans","Serif","Monospace","Condensed","Cursive"};String[] fontIds={"sans-serif","serif","monospace","sans-serif-condensed","cursive"};Spinner font=new Spinner(this);font.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,fontNames));int fp=0;for(int i=0;i<fontIds.length;i++)if(fontIds[i].equals(x.font))fp=i;font.setSelection(fp);box.addView(font,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout styles=new LinearLayout(this);CheckBox bold=new CheckBox(this);bold.setText("Bold");bold.setTextColor(white);bold.setChecked(x.bold);styles.addView(bold,new LinearLayout.LayoutParams(0,dp(48),1));CheckBox italic=new CheckBox(this);italic.setText("Italic");italic.setTextColor(white);italic.setChecked(x.italic);styles.addView(italic,new LinearLayout.LayoutParams(0,dp(48),1));box.addView(styles);
        box.addView(text("SIZE",11,muted,true));SeekBar size=new SeekBar(this);size.setMax(340);size.setProgress(Math.max(0,Math.min(340,Math.round((x.textSize-.01f)*1000f))));box.addView(size);
        box.addView(text("TEXT COLOR",11,muted,true));EditText color=input("#FFFFFF",String.format(Locale.US,"#%06X",0xFFFFFF&x.color));box.addView(color);
        box.addView(text("ALIGNMENT",11,muted,true));String[] aligns={"Left","Center","Right"};Spinner align=new Spinner(this);align.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,aligns));align.setSelection("right".equals(x.align)?2:("center".equals(x.align)?1:0));box.addView(align,new LinearLayout.LayoutParams(-1,dp(48)));
        box.addView(text("OPACITY",11,muted,true));SeekBar opacity=new SeekBar(this);opacity.setMax(100);opacity.setProgress(Math.round(x.alpha*100));box.addView(opacity);
        box.addView(text("OUTLINE / STROKE",11,muted,true));EditText strokeColor=input("Outline color",String.format(Locale.US,"#%06X",0xFFFFFF&x.strokeColor));box.addView(strokeColor);SeekBar stroke=new SeekBar(this);stroke.setMax(100);stroke.setProgress(Math.round(x.strokeWidth/.012f*100));box.addView(stroke);
        box.addView(text("SHADOW",11,muted,true));EditText shadowColor=input("Shadow color",String.format(Locale.US,"#%06X",0xFFFFFF&x.shadowColor));box.addView(shadowColor);SeekBar shadow=new SeekBar(this);shadow.setMax(100);shadow.setProgress(Math.round(x.shadowRadius/.02f*100));box.addView(shadow);
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence a,int b,int c,int d){}public void onTextChanged(CharSequence a,int b,int c,int d){x.text=content.getText().toString();x.color=colorOr(color.getText().toString(),x.color);x.strokeColor=colorOr(strokeColor.getText().toString(),x.strokeColor);x.shadowColor=colorOr(shadowColor.getText().toString(),x.shadowColor);canvas.invalidate();}public void afterTextChanged(Editable e){}};content.addTextChangedListener(watcher);color.addTextChangedListener(watcher);strokeColor.addTextChangedListener(watcher);shadowColor.addTextChangedListener(watcher);
        font.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int pos,long id){x.font=fontIds[pos];canvas.invalidate();}});bold.setOnCheckedChangeListener((b,on)->{x.bold=on;canvas.invalidate();});italic.setOnCheckedChangeListener((b,on)->{x.italic=on;canvas.invalidate();});align.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int pos,long id){x.align=pos==2?"right":(pos==1?"center":"left");canvas.invalidate();}});
        size.setOnSeekBarChangeListener(seek(()->{x.textSize=.01f+size.getProgress()/1000f;canvas.invalidate();}));opacity.setOnSeekBarChangeListener(seek(()->{x.alpha=opacity.getProgress()/100f;canvas.invalidate();}));stroke.setOnSeekBarChangeListener(seek(()->{x.strokeWidth=.012f*stroke.getProgress()/100f;canvas.invalidate();}));shadow.setOnSeekBarChangeListener(seek(()->{x.shadowRadius=.02f*shadow.getProgress()/100f;canvas.invalidate();}));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Text Properties").setView(scroll).setNegativeButton("Cancel",(d,w)->{if(index>=0&&index<template.elements.size())template.elements.set(index,original);canvas.invalidate();}).setPositiveButton("Apply",(d,w)->{applied[0]=true;rememberBefore(before);canvas.invalidate();}).create();dlg.setOnCancelListener(d->{if(!applied[0]&&index>=0&&index<template.elements.size()){template.elements.set(index,original);canvas.invalidate();}});dlg.show();
    }

    private SeekBar.OnSeekBarChangeListener seek(final Runnable r){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){r.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};}

    void editSelectedImage(){
        final boolean photo=canvas.selected==SEL_PHOTO;final OverlayProcessor.Element e=photo?null:canvas.selectedElement();if(!photo&&(e==null||!"image".equals(e.type))){toast("Select a photo, PNG, logo, or image layer first");return;}final String before=templateSnapshot();final float oldHue=photo?template.photoHue:e.hue;final float oldTint=photo?0f:e.tint;final int oldTintColor=photo?Color.WHITE:e.tintColor;final boolean[] applied={false};
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(8),dp(18),dp(8));box.addView(text("HUE — drag to change colors",12,white,true));TextView hueValue=text("",12,muted,false);box.addView(hueValue);SeekBar hue=new SeekBar(this);hue.setMax(360);hue.setProgress(Math.round(oldHue)+180);box.addView(hue);
        EditText tintHex=null;SeekBar tintAmount=null;if(!photo){box.addView(text("OPTIONAL TINT",11,muted,true));tintHex=input("Tint color",String.format(Locale.US,"#%06X",0xFFFFFF&e.tintColor));box.addView(tintHex);tintAmount=new SeekBar(this);tintAmount.setMax(100);tintAmount.setProgress(Math.round(e.tint*100));box.addView(tintAmount);}
        final EditText th=tintHex;final SeekBar ta=tintAmount;Runnable update=()->{float h=hue.getProgress()-180f;if(photo)template.photoHue=h;else{e.hue=h;if(th!=null)e.tintColor=colorOr(th.getText().toString(),e.tintColor);if(ta!=null)e.tint=ta.getProgress()/100f;}hueValue.setText(String.format(Locale.US,"Hue: %+.0f°",h));canvas.invalidate();};hue.setOnSeekBarChangeListener(seek(update));if(ta!=null)ta.setOnSeekBarChangeListener(seek(update));if(th!=null)th.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence a,int b,int c,int d){}public void onTextChanged(CharSequence a,int b,int c,int d){update.run();}public void afterTextChanged(Editable x){}});update.run();
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(photo?"Photo Color Properties":"Image Properties").setView(box).setNeutralButton("Reset Hue",(d,w)->{if(photo)template.photoHue=0f;else e.hue=0f;rememberBefore(before);canvas.invalidate();}).setNegativeButton("Cancel",(d,w)->{if(photo)template.photoHue=oldHue;else{e.hue=oldHue;e.tint=oldTint;e.tintColor=oldTintColor;}canvas.invalidate();}).setPositiveButton("Apply",(d,w)->{applied[0]=true;rememberBefore(before);canvas.invalidate();}).create();dlg.setOnCancelListener(d->{if(!applied[0]){if(photo)template.photoHue=oldHue;else{e.hue=oldHue;e.tint=oldTint;e.tintColor=oldTintColor;}canvas.invalidate();}});dlg.show();
    }

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
        private void drawPhoto(Canvas c){if(photo==null){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(40,49,59));c.drawRect(card,paint);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(18));paint.setColor(Color.LTGRAY);c.drawText("TAP PHOTO TO CHOOSE AN IMAGE",card.centerX(),card.centerY(),paint);return;}RectF d=photoBounds();float cx=d.centerX(),cy=d.centerY();c.save();c.rotate(template.photoRotation,cx,cy);RectF clip=new RectF(d.left+template.photoClipL*d.width(),d.top+template.photoClipT*d.height(),d.left+template.photoClipR*d.width(),d.top+template.photoClipB*d.height());c.clipRect(clip);paint.setAlpha(255);paint.setColorFilter(Math.abs(template.photoHue)>.01f?OverlayProcessor.imageColorFilter(Color.WHITE,0f,template.photoHue):null);c.drawBitmap(photo,null,d,paint);paint.setColorFilter(null);c.restore();if(selected==SEL_PHOTO)drawSelection(c,d,template.photoRotation,template.photoClipL,template.photoClipT,template.photoClipR,template.photoClipB);}
        private void drawElements(Canvas c){float w=card.width(),h=card.height(),base=Math.min(w,h);for(int i=0;i<template.elements.size();i++){OverlayProcessor.Element e=template.elements.get(i);if(e==null||!e.visible)continue;float x=card.left+e.x*w,y=card.top+e.y*h;c.save();c.rotate(e.rotation,x,y);paint.setAlpha(Math.max(0,Math.min(255,(int)(255*e.alpha))));paint.setColorFilter(null);if("text".equals(e.type)){paint.setStyle(Paint.Style.FILL);paint.setColor(e.color);paint.setTextSize(Math.max(dp(10),e.textSize*base));int style=(e.bold?Typeface.BOLD:0)|(e.italic?Typeface.ITALIC:0);paint.setTypeface(Typeface.create(e.font,style));paint.setTextAlign("right".equals(e.align)?Paint.Align.RIGHT:("center".equals(e.align)?Paint.Align.CENTER:Paint.Align.LEFT));if(e.shadowRadius>0f)paint.setShadowLayer(Math.max(1f,e.shadowRadius*base),e.shadowDx*base,e.shadowDy*base,e.shadowColor);String[] lines=displayText(e.text).split("\\n",-1);float line=paint.getTextSize()*1.08f;for(int k=0;k<lines.length;k++){float yy=y+k*line;if(e.strokeWidth>0f){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1f,e.strokeWidth*base));paint.setColor(e.strokeColor);c.drawText(lines[k],x,yy,paint);}paint.setStyle(Paint.Style.FILL);paint.setColor(e.color);c.drawText(lines[k],x,yy,paint);}paint.clearShadowLayer();}else if("image".equals(e.type)){Bitmap b=image(e.assetPath);if(b!=null){paint.setColorFilter(OverlayProcessor.imageColorFilter(e.tintColor,e.tint,e.hue));RectF d=new RectF(x-e.w*w/2,y-e.h*h/2,x+e.w*w/2,y+e.h*h/2);RectF clip=new RectF(d.left+e.clipL*d.width(),d.top+e.clipT*d.height(),d.left+e.clipR*d.width(),d.top+e.clipB*d.height());c.clipRect(clip);c.drawBitmap(b,null,d,paint);paint.setColorFilter(null);}}else{paint.setColor(e.color);paint.setStyle(e.fill?Paint.Style.FILL:Paint.Style.STROKE);paint.setStrokeWidth(Math.max(dp(2),base*.005f));c.drawRect(x,y,x+e.w*w,y+e.h*h,paint);}c.restore();if(i==selected){RectF b=bounds(e,x,y,w,h);drawSelection(c,b,e.rotation,e.clipL,e.clipT,e.clipR,e.clipB);}}paint.setAlpha(255);paint.setColorFilter(null);}
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
                case MotionEvent.ACTION_UP:{long now=System.currentTimeMillis();if(!gestureChanged&&selected!=SEL_NONE){if(now-lastTap<360){if(selected==SEL_PHOTO)editSelectedImage();else{OverlayProcessor.Element e=selectedElement();if(e!=null&&"text".equals(e.type))editSelectedText();else if(e!=null&&"image".equals(e.type))editSelectedImage();}lastTap=0;}else lastTap=now;}return true;}
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
