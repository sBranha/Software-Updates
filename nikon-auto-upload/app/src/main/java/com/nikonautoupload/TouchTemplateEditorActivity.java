package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

/** Full-screen direct-manipulation editor for sports-card templates. */
public class TouchTemplateEditorActivity extends Activity {
    private static final int REQ_PHOTO=901,REQ_PNG=902,REQ_LOGO=903;
    private final int bg=Color.rgb(5,8,12),panel=Color.rgb(18,24,31),white=Color.rgb(245,247,250),muted=Color.rgb(160,170,181),blue=Color.rgb(33,150,243),green=Color.rgb(55,206,108),red=Color.rgb(239,83,80),amber=Color.rgb(255,193,7);
    private SharedPreferences p;
    private OverlayProcessor.Template template;
    private TouchCanvas canvas;
    private TextView title,status;

    @Override public void onCreate(Bundle b){super.onCreate(b);p=getSharedPreferences("settings",MODE_PRIVATE);String id=getIntent().getStringExtra("template_id");template=id==null?null:OverlayProcessor.findTemplate(this,id);if(template==null){template=new OverlayProcessor.Template();template.name="Touch Template";template.category="Custom";template.orientation=getIntent().getStringExtra("orientation");if(template.orientation==null)template.orientation="portrait";}draw();}

    private void draw(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));
        Button back=button("‹",Color.rgb(55,65,76));back.setTextSize(26);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(48)));
        title=text(template.name,18,white,true);top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button rename=button("NAME",Color.rgb(70,80,90));rename.setOnClickListener(v->renameTemplate());top.addView(rename,new LinearLayout.LayoutParams(dp(74),dp(46)));
        Button save=button("SAVE",green);save.setOnClickListener(v->saveTemplate());top.addView(save,new LinearLayout.LayoutParams(dp(74),dp(46)));root.addView(top);

        status=text("Tap an item to select it • drag with one finger • pinch to resize • twist to rotate • double-tap text to edit",12,muted,false);status.setPadding(dp(12),dp(3),dp(12),dp(6));root.addView(status);
        canvas=new TouchCanvas(this);root.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));

        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);LinearLayout tools=new LinearLayout(this);tools.setPadding(dp(6),dp(5),dp(6),dp(7));hsv.addView(tools);root.addView(hsv,new LinearLayout.LayoutParams(-1,dp(65)));
        addTool(tools,"PHOTO",blue,v->pick(REQ_PHOTO,"image/*"));
        addTool(tools,"PNG FRAME",green,v->pick(REQ_PNG,"image/png"));
        addTool(tools,"+ NAME",blue,v->addToken("{PLAYER_NAME}",.5f,.82f,.07f));
        addTool(tools,"+ #",blue,v->addToken("{NUMBER}",.82f,.82f,.09f));
        addTool(tools,"+ POSITION",blue,v->addToken("{POSITION}",.5f,.92f,.045f));
        addTool(tools,"+ TEXT",blue,v->addCustomText());
        addTool(tools,"+ LOGO",green,v->pick(REQ_LOGO,"image/*"));
        addTool(tools,"COLOR",Color.rgb(90,75,130),v->editColor());
        addTool(tools,"FRONT",Color.rgb(70,80,90),v->moveLayer(1));
        addTool(tools,"BACK",Color.rgb(70,80,90),v->moveLayer(-1));
        addTool(tools,"COPY",amber,v->duplicateSelected());
        addTool(tools,"DELETE",red,v->deleteSelected());
        addTool(tools,"LAYERS",Color.rgb(70,80,90),v->showLayers());
        setContentView(root);
    }

    private void addTool(LinearLayout row,String label,int color,View.OnClickListener l){Button b=button(label,color);b.setOnClickListener(l);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(98),dp(52));lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);}
    private void pick(int req,String type){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,req);}

    private void renameTemplate(){EditText e=input("Template name",template.name);new AlertDialog.Builder(this).setTitle("Template name").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty()){template.name=n;title.setText(n);}}).show();}
    private void saveTemplate(){OverlayProcessor.saveTemplate(this,template);String key="portrait".equals(template.orientation)?OverlayProcessor.PREF_PORTRAIT_TEMPLATE:OverlayProcessor.PREF_LANDSCAPE_TEMPLATE;p.edit().putString(key,template.id).apply();Toast.makeText(this,"Template saved",Toast.LENGTH_SHORT).show();}

    private void addToken(String token,float x,float y,float size){OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="text";e.text=token;e.x=x;e.y=y;e.textSize=size;e.align="center";e.bold=true;template.elements.add(e);canvas.select(template.elements.size()-1);}
    private void addCustomText(){EditText e=input("Text","Text");new AlertDialog.Builder(this).setTitle("Add text").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Add",(d,w)->{OverlayProcessor.Element x=new OverlayProcessor.Element();x.type="text";x.text=e.getText().toString();x.x=.5f;x.y=.5f;x.textSize=.06f;x.align="center";template.elements.add(x);canvas.select(template.elements.size()-1);}).show();}
    void editSelectedText(){OverlayProcessor.Element x=canvas.selectedElement();if(x==null||!"text".equals(x.type))return;EditText e=input("Text or token",x.text);new AlertDialog.Builder(this).setTitle("Edit text").setMessage("You can use {PLAYER_NAME}, {NUMBER}, {POSITION}, {TEAM}, {EVENT}, {DATE}, {PHOTOGRAPHER}").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{x.text=e.getText().toString();canvas.invalidate();}).show();}

    private void editColor(){OverlayProcessor.Element e=canvas.selectedElement();if(e==null){toast("Tap a layer first");return;}if("image".equals(e.type)){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);EditText hex=input("Tint #RRGGBB",String.format(Locale.US,"#%06X",0xFFFFFF&e.tintColor));box.addView(hex);SeekBar amount=new SeekBar(this);amount.setMax(100);amount.setProgress(Math.round(e.tint*100));box.addView(amount);new AlertDialog.Builder(this).setTitle("Recolor image layer").setMessage("0% keeps its original color. Increase tint to shift the PNG toward a team color.").setView(box).setNegativeButton("Cancel",null).setNeutralButton("Clear",(d,w)->{e.tint=0;e.tintColor=Color.WHITE;canvas.invalidate();}).setPositiveButton("Apply",(d,w)->{try{e.tintColor=Color.parseColor(hex.getText().toString().trim());e.tint=amount.getProgress()/100f;canvas.invalidate();}catch(Exception ex){toast("Use a color like #CC2027");}}).show();return;}EditText hex=input("Color #RRGGBB",String.format(Locale.US,"#%06X",0xFFFFFF&e.color));new AlertDialog.Builder(this).setTitle("Layer color").setView(hex).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{try{e.color=Color.parseColor(hex.getText().toString().trim());canvas.invalidate();}catch(Exception ex){toast("Use a color like #FFFFFF");}}).show();}

    private void moveLayer(int direction){if(canvas.selected<0)return;int from=canvas.selected,to=Math.max(0,Math.min(template.elements.size()-1,from+direction));if(from==to)return;OverlayProcessor.Element e=template.elements.remove(from);template.elements.add(to,e);canvas.select(to);}
    private void duplicateSelected(){OverlayProcessor.Element e=canvas.selectedElement();if(e==null)return;OverlayProcessor.Element c=e.copy();c.id=UUID.randomUUID().toString();c.x+=.025f;c.y+=.025f;template.elements.add(c);canvas.select(template.elements.size()-1);}
    private void deleteSelected(){if(canvas.selected<0)return;template.elements.remove(canvas.selected);canvas.selected=-1;canvas.invalidate();status.setText("Layer deleted");}
    private void showLayers(){if(template.elements.isEmpty()){toast("No layers yet");return;}String[] names=new String[template.elements.size()];for(int i=0;i<names.length;i++){OverlayProcessor.Element e=template.elements.get(i);String n="text".equals(e.type)?e.text:("image".equals(e.type)?"Image / PNG":"Shape");names[i]=(i==canvas.selected?"✓ ":"")+n;}new AlertDialog.Builder(this).setTitle("Layers — bottom to top").setItems(names,(d,i)->canvas.select(i)).setNegativeButton("Close",null).show();}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{if(req==REQ_PHOTO){canvas.loadPhoto(u);return;}String path=OverlayProcessor.copyAssetIntoModule(this,u,req==REQ_PNG?"template.png":"logo.png");OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="image";e.assetPath=path;e.x=.5f;e.y=.5f;e.w=req==REQ_PNG?1f:.25f;e.h=req==REQ_PNG?1f:.25f;e.alpha=1f;e.tint=0f;if(req==REQ_PNG)template.elements.add(0,e);else template.elements.add(e);canvas.select(req==REQ_PNG?0:template.elements.size()-1);}catch(Exception ex){toast("Could not add image: "+ex.getMessage());}}

    private String displayText(String s){String x=s==null?"":s;String player=p.getString("overlay_player","");if(player.isEmpty())player="PLAYER NAME";String number=p.getString("overlay_number","");if(number.isEmpty())number="00";String position=p.getString("overlay_position","");if(position.isEmpty())position="POSITION";String team=p.getString("overlay_team","");if(team.isEmpty())team="TEAM";String event=p.getString("overlay_event","");if(event.isEmpty())event="EVENT";return x.replace("{PLAYER_NAME}",player).replace("{NUMBER}",number).replace("{POSITION}",position).replace("{TEAM}",team).replace("{EVENT}",event).replace("{PHOTOGRAPHER}",p.getString("overlay_photographer","PHOTOGRAPHER")).replace("{DATE}","DATE");}

    private final class TouchCanvas extends View {
        int selected=-1;Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);RectF card=new RectF();Bitmap photo;Map<String,Bitmap> cache=new HashMap<>();float zoom=1f,panX=0,panY=0,lastX,lastY,startDistance,startAngle,startW,startH,startText,startRotation,startZoom;float startMidX,startMidY;boolean moved=false,pinching=false,canvasGesture=false;long lastTap=0;
        TouchCanvas(Context c){super(c);setBackgroundColor(Color.rgb(10,14,19));}
        void select(int i){selected=i;invalidate();OverlayProcessor.Element e=selectedElement();status.setText(e==null?"Nothing selected":"Selected: "+("text".equals(e.type)?displayText(e.text):("image".equals(e.type)?"PNG / graphic":"shape"))+" • drag • pinch • rotate");}
        OverlayProcessor.Element selectedElement(){return selected>=0&&selected<template.elements.size()?template.elements.get(selected):null;}
        void loadPhoto(Uri u){try{ImageDecoder.Source src=ImageDecoder.createSource(getContentResolver(),u);photo=ImageDecoder.decodeBitmap(src,(d,info,s)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();double sc=Math.min(1d,Math.sqrt(4000000d/Math.max(1d,(double)w*h)));if(sc<.999)d.setTargetSize(Math.max(1,(int)(w*sc)),Math.max(1,(int)(h*sc)));d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});invalidate();status.setText("Test photo loaded. Tap any card item and move it directly.");}catch(Exception e){toast("Could not open test photo");}}
        private void cardRect(){float pad=dp(12),aw=getWidth()-pad*2,ah=getHeight()-pad*2,ratio="portrait".equals(template.orientation)?.70f:1.50f,w=aw,h=w/ratio;if(h>ah){h=ah;w=h*ratio;}float cx=getWidth()/2f+panX,cy=getHeight()/2f+panY;w*=zoom;h*=zoom;card.set(cx-w/2,cy-h/2,cx+w/2,cy+h/2);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);cardRect();paint.setColor(Color.rgb(28,35,43));paint.setStyle(Paint.Style.FILL);c.drawRect(card,paint);if(photo!=null)drawCover(c,photo,card,paint);else{paint.setColor(Color.rgb(42,52,62));c.drawRect(card,paint);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(18));paint.setColor(Color.LTGRAY);c.drawText("CHOOSE PHOTO",card.centerX(),card.centerY(),paint);}float w=card.width(),h=card.height(),base=Math.min(w,h);
            for(int i=0;i<template.elements.size();i++){OverlayProcessor.Element e=template.elements.get(i);if(e==null||!e.visible)continue;float x=card.left+e.x*w,y=card.top+e.y*h;c.save();c.rotate(e.rotation,x,y);paint.setAlpha(Math.max(0,Math.min(255,(int)(255*e.alpha))));paint.setColorFilter(null);if("text".equals(e.type)){paint.setStyle(Paint.Style.FILL);paint.setColor(e.color);paint.setTextSize(Math.max(dp(10),e.textSize*base));paint.setTypeface(Typeface.create(e.font,e.bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextAlign("right".equals(e.align)?Paint.Align.RIGHT:("center".equals(e.align)?Paint.Align.CENTER:Paint.Align.LEFT));String[] lines=displayText(e.text).split("\\n",-1);float line=paint.getTextSize()*1.08f;for(int k=0;k<lines.length;k++)c.drawText(lines[k],x,y+k*line,paint);}else if("image".equals(e.type)){Bitmap b=image(e.assetPath);if(b!=null){if(e.tint>0.001f)paint.setColorFilter(OverlayProcessor.tintFilter(e.tintColor,e.tint));RectF d=new RectF(x-e.w*w/2f,y-e.h*h/2f,x+e.w*w/2f,y+e.h*h/2f);c.drawBitmap(b,null,d,paint);paint.setColorFilter(null);}}else{paint.setColor(e.color);paint.setStyle(e.fill?Paint.Style.FILL:Paint.Style.STROKE);paint.setStrokeWidth(Math.max(dp(2),base*.005f));c.drawRect(x,y,x+e.w*w,y+e.h*h,paint);}if(i==selected){paint.setAlpha(255);paint.setColor(amber);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2)/Math.max(1f,zoom));RectF b=bounds(e,x,y,w,h);c.drawRect(b,paint);float r=dp(5)/Math.max(1f,zoom);paint.setStyle(Paint.Style.FILL);c.drawCircle(b.left,b.top,r,paint);c.drawCircle(b.right,b.bottom,r,paint);}c.restore();}paint.setAlpha(255);paint.setColorFilter(null);}
        private Bitmap image(String path){if(path==null||path.isEmpty())return null;if(cache.containsKey(path))return cache.get(path);File f=new File(path);Bitmap b=f.exists()?BitmapFactory.decodeFile(path):null;if(b!=null)cache.put(path,b);return b;}
        private void drawCover(Canvas c,Bitmap b,RectF d,Paint p){float src=(float)b.getWidth()/b.getHeight(),dst=d.width()/d.height();Rect s;if(src>dst){int nw=(int)(b.getHeight()*dst),l=(b.getWidth()-nw)/2;s=new Rect(l,0,l+nw,b.getHeight());}else{int nh=(int)(b.getWidth()/dst),t=(b.getHeight()-nh)/2;s=new Rect(0,t,b.getWidth(),t+nh);}c.drawBitmap(b,s,d,p);}
        private RectF bounds(OverlayProcessor.Element e,float x,float y,float w,float h){if("text".equals(e.type)){paint.setTextSize(Math.max(dp(10),e.textSize*Math.min(w,h)));float tw=0;for(String line:displayText(e.text).split("\\n",-1))tw=Math.max(tw,paint.measureText(line));float th=paint.getTextSize()*1.25f;if("right".equals(e.align))return new RectF(x-tw,y-th,x,y+dp(8));if("center".equals(e.align))return new RectF(x-tw/2,y-th,x+tw/2,y+dp(8));return new RectF(x,y-th,x+tw,y+dp(8));}if("image".equals(e.type))return new RectF(x-e.w*w/2,y-e.h*h/2,x+e.w*w/2,y+e.h*h/2);return new RectF(x,y,x+e.w*w,y+e.h*h);}
        private int hit(float px,float py){float w=card.width(),h=card.height();for(int i=template.elements.size()-1;i>=0;i--){OverlayProcessor.Element e=template.elements.get(i);if(e==null||!e.visible)continue;float x=card.left+e.x*w,y=card.top+e.y*h;RectF b=bounds(e,x,y,w,h);b.inset(-dp(20),-dp(20));if(b.contains(px,py))return i;}return -1;}
        @Override public boolean onTouchEvent(MotionEvent ev){cardRect();switch(ev.getActionMasked()){
            case MotionEvent.ACTION_DOWN:{int h=hit(ev.getX(),ev.getY());selected=h;lastX=ev.getX();lastY=ev.getY();moved=false;pinching=false;canvasGesture=(h<0);invalidate();if(h>=0)status.setText("Selected • drag it • pinch to resize • twist to rotate");return true;}
            case MotionEvent.ACTION_POINTER_DOWN:{if(ev.getPointerCount()>=2){pinching=true;startDistance=distance(ev);startAngle=angle(ev);startMidX=(ev.getX(0)+ev.getX(1))/2f;startMidY=(ev.getY(0)+ev.getY(1))/2f;OverlayProcessor.Element e=selectedElement();if(e!=null&&!canvasGesture){startW=e.w;startH=e.h;startText=e.textSize;startRotation=e.rotation;}else{canvasGesture=true;startZoom=zoom;}}return true;}
            case MotionEvent.ACTION_MOVE:{moved=true;if(ev.getPointerCount()>=2&&pinching){float scale=startDistance<=0?1:distance(ev)/startDistance;OverlayProcessor.Element e=selectedElement();if(e!=null&&!canvasGesture){e.rotation=startRotation+(angle(ev)-startAngle);if("text".equals(e.type))e.textSize=clamp(startText*scale,.01f,.30f);else{e.w=clamp(startW*scale,.02f,2f);e.h=clamp(startH*scale,.02f,2f);}invalidate();return true;}float nz=clamp(startZoom*scale,.6f,5f);float mx=(ev.getX(0)+ev.getX(1))/2f,my=(ev.getY(0)+ev.getY(1))/2f;panX+=(mx-startMidX)*.08f;panY+=(my-startMidY)*.08f;zoom=nz;startMidX=mx;startMidY=my;invalidate();return true;}OverlayProcessor.Element e=selectedElement();if(e!=null&&!canvasGesture){float dx=(ev.getX()-lastX)/Math.max(1,card.width()),dy=(ev.getY()-lastY)/Math.max(1,card.height());e.x=clamp(e.x+dx,-.5f,1.5f);e.y=clamp(e.y+dy,-.5f,1.5f);lastX=ev.getX();lastY=ev.getY();invalidate();}else if(zoom>1f){panX+=ev.getX()-lastX;panY+=ev.getY()-lastY;lastX=ev.getX();lastY=ev.getY();invalidate();}return true;}
            case MotionEvent.ACTION_POINTER_UP:pinching=false;return true;
            case MotionEvent.ACTION_UP:{long now=System.currentTimeMillis();if(!moved&&selected>=0){if(now-lastTap<360){OverlayProcessor.Element e=selectedElement();if(e!=null&&"text".equals(e.type))editSelectedText();lastTap=0;}else lastTap=now;}return true;}
            case MotionEvent.ACTION_CANCEL:return true;}return true;}
        private float distance(MotionEvent e){float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return (float)Math.sqrt(dx*dx+dy*dy);}private float angle(MotionEvent e){return (float)Math.toDegrees(Math.atan2(e.getY(1)-e.getY(0),e.getX(1)-e.getX(0)));}private float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    }

    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(11);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(muted);e.setTextColor(white);e.setText(value);e.setSingleLine(true);return e;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
