from pathlib import Path

# Camera Auto Upload 0.8.6
# - Photoshop-style floating text properties on double-tap
# - Live font/color/size/style/alignment/outline/shadow/opacity preview
# - Image/PNG/photo hue-shift properties with live preview
# - Keep crop, rotation persistence, safe areas, camera transports, and Flickr intact

# -----------------------------------------------------------------------------
# OverlayProcessor: persist richer text styles and image/photo hue.
# -----------------------------------------------------------------------------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayProcessor.java')
s=p.read_text()

old='''        public int color=Color.WHITE;\n        public int tintColor=Color.WHITE;\n        public float tint=0f;\n        public boolean bold=true,fill=false,visible=true;'''
new='''        public int color=Color.WHITE;\n        public int tintColor=Color.WHITE;\n        public float tint=0f,hue=0f;\n        public int strokeColor=Color.BLACK,shadowColor=Color.BLACK;\n        public float strokeWidth=0f,shadowRadius=0f,shadowDx=.003f,shadowDy=.003f;\n        public boolean bold=true,italic=false,fill=false,visible=true;'''
if old not in s: raise SystemExit('0.8.6 element style fields target missing')
s=s.replace(old,new,1)

old='''            o.put("color",color).put("tintColor",tintColor).put("tint",tint).put("bold",bold).put("fill",fill).put("visible",visible);return o;'''
new='''            o.put("color",color).put("tintColor",tintColor).put("tint",tint).put("hue",hue).put("strokeColor",strokeColor).put("strokeWidth",strokeWidth).put("shadowColor",shadowColor).put("shadowRadius",shadowRadius).put("shadowDx",shadowDx).put("shadowDy",shadowDy).put("bold",bold).put("italic",italic).put("fill",fill).put("visible",visible);return o;'''
if old not in s: raise SystemExit('0.8.6 element style JSON write target missing')
s=s.replace(old,new,1)

old='''e.color=o.optInt("color",Color.WHITE);e.tintColor=o.optInt("tintColor",Color.WHITE);e.tint=(float)o.optDouble("tint",0);e.bold=o.optBoolean("bold",true);e.fill=o.optBoolean("fill",false);e.visible=o.optBoolean("visible",true);return e;'''
new='''e.color=o.optInt("color",Color.WHITE);e.tintColor=o.optInt("tintColor",Color.WHITE);e.tint=(float)o.optDouble("tint",0);e.hue=(float)o.optDouble("hue",0);e.strokeColor=o.optInt("strokeColor",Color.BLACK);e.strokeWidth=(float)o.optDouble("strokeWidth",0);e.shadowColor=o.optInt("shadowColor",Color.BLACK);e.shadowRadius=(float)o.optDouble("shadowRadius",0);e.shadowDx=(float)o.optDouble("shadowDx",.003);e.shadowDy=(float)o.optDouble("shadowDy",.003);e.bold=o.optBoolean("bold",true);e.italic=o.optBoolean("italic",false);e.fill=o.optBoolean("fill",false);e.visible=o.optBoolean("visible",true);return e;'''
if old not in s: raise SystemExit('0.8.6 element style JSON read target missing')
s=s.replace(old,new,1)

old='''        public float photoX=.5f,photoY=.5f,photoScale=1f,photoRotation=0f;\n        public float photoClipL=0f,photoClipT=0f,photoClipR=1f,photoClipB=1f;'''
new='''        public float photoX=.5f,photoY=.5f,photoScale=1f,photoRotation=0f,photoHue=0f;\n        public float photoClipL=0f,photoClipT=0f,photoClipR=1f,photoClipB=1f;'''
if old not in s: raise SystemExit('0.8.6 photo hue field target missing')
s=s.replace(old,new,1)

old='''.put("photoX",photoX).put("photoY",photoY).put("photoScale",photoScale).put("photoRotation",photoRotation).put("photoClipL",photoClipL)'''
new='''.put("photoX",photoX).put("photoY",photoY).put("photoScale",photoScale).put("photoRotation",photoRotation).put("photoHue",photoHue).put("photoClipL",photoClipL)'''
if old not in s: raise SystemExit('0.8.6 photo hue JSON write target missing')
s=s.replace(old,new,1)

old='''t.photoX=(float)o.optDouble("photoX",.5);t.photoY=(float)o.optDouble("photoY",.5);t.photoScale=(float)o.optDouble("photoScale",1);t.photoRotation=(float)o.optDouble("photoRotation",0);t.photoClipL='''
new='''t.photoX=(float)o.optDouble("photoX",.5);t.photoY=(float)o.optDouble("photoY",.5);t.photoScale=(float)o.optDouble("photoScale",1);t.photoRotation=(float)o.optDouble("photoRotation",0);t.photoHue=(float)o.optDouble("photoHue",0);t.photoClipL='''
if old not in s: raise SystemExit('0.8.6 photo hue JSON read target missing')
s=s.replace(old,new,1)

# Final render: photo hue.
old='''Paint photoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);canvas.save();'''
new='''Paint photoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);if(Math.abs(t.photoHue)>.01f)photoPaint.setColorFilter(imageColorFilter(Color.WHITE,0f,t.photoHue));canvas.save();'''
if old not in s: raise SystemExit('0.8.6 photo hue render target missing')
s=s.replace(old,new,1)

# Final render: richer text paint.
old='''Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);paint.setColor(e.color);paint.setAlpha(alpha);paint.setTextSize(Math.max(10,e.textSize*base));paint.setTypeface(Typeface.create(e.font,e.bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextAlign("right".equals(e.align)?Paint.Align.RIGHT:("center".equals(e.align)?Paint.Align.CENTER:Paint.Align.LEFT));\n                String text=resolveTokens(e.text,p);String[] lines=text.split("\\\\n",-1);float line=paint.getTextSize()*1.08f;for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],cx,cy+i*line,paint);'''
new='''Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);paint.setColor(e.color);paint.setAlpha(alpha);paint.setTextSize(Math.max(10,e.textSize*base));int style=(e.bold?Typeface.BOLD:0)|(e.italic?Typeface.ITALIC:0);paint.setTypeface(Typeface.create(e.font,style));paint.setTextAlign("right".equals(e.align)?Paint.Align.RIGHT:("center".equals(e.align)?Paint.Align.CENTER:Paint.Align.LEFT));if(e.shadowRadius>0f)paint.setShadowLayer(Math.max(1f,e.shadowRadius*base),e.shadowDx*base,e.shadowDy*base,e.shadowColor);\n                String text=resolveTokens(e.text,p);String[] lines=text.split("\\\\n",-1);float line=paint.getTextSize()*1.08f;for(int i=0;i<lines.length;i++){float yy=cy+i*line;if(e.strokeWidth>0f){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1f,e.strokeWidth*base));paint.setColor(e.strokeColor);canvas.drawText(lines[i],cx,yy,paint);}paint.setStyle(Paint.Style.FILL);paint.setColor(e.color);canvas.drawText(lines[i],cx,yy,paint);}paint.clearShadowLayer();'''
if old not in s: raise SystemExit('0.8.6 final text render target missing')
s=s.replace(old,new,1)

# Image hue + existing tint can be combined.
old='''if(e.tint>0.001f)paint.setColorFilter(tintFilter(e.tintColor,e.tint));RectF dst='''
new='''paint.setColorFilter(imageColorFilter(e.tintColor,e.tint,e.hue));RectF dst='''
if old not in s: raise SystemExit('0.8.6 image hue render target missing')
s=s.replace(old,new,1)

marker='''    private static Bitmap decode(Context c,Uri uri,long maxPixels) throws IOException {'''
helper=r'''    static ColorFilter imageColorFilter(int tintColor,float tint,float hueDegrees){
        float hue=Math.max(-180f,Math.min(180f,hueDegrees));float rad=(float)Math.toRadians(hue),cos=(float)Math.cos(rad),sin=(float)Math.sin(rad);float lr=.213f,lg=.715f,lb=.072f;
        ColorMatrix h=new ColorMatrix(new float[]{
            lr+cos*(1-lr)+sin*(-lr),lg+cos*(-lg)+sin*(-lg),lb+cos*(-lb)+sin*(1-lb),0,0,
            lr+cos*(-lr)+sin*.143f,lg+cos*(1-lg)+sin*.140f,lb+cos*(-lb)+sin*(-.283f),0,0,
            lr+cos*(-lr)+sin*(-(1-lr)),lg+cos*(-lg)+sin*lg,lb+cos*(1-lb)+sin*lb,0,0,
            0,0,0,1,0});
        float t=Math.max(0f,Math.min(1f,tint));if(t>0.001f){float k=1f-t,rr=Color.red(tintColor)/255f,gg=Color.green(tintColor)/255f,bb=Color.blue(tintColor)/255f;ColorMatrix tm=new ColorMatrix(new float[]{k+t*rr*.2126f,t*rr*.7152f,t*rr*.0722f,0,0,t*gg*.2126f,k+t*gg*.7152f,t*gg*.0722f,0,0,t*bb*.2126f,t*bb*.7152f,k+t*bb*.0722f,0,0,0,0,0,1,0});h.postConcat(tm);}return new ColorMatrixColorFilter(h);
    }

'''
if marker not in s: raise SystemExit('0.8.6 color filter helper marker missing')
s=s.replace(marker,helper+marker,1)
p.write_text(s)

# -----------------------------------------------------------------------------
# Touch editor: Photoshop-like properties popups, live preview, double-tap.
# -----------------------------------------------------------------------------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/TouchTemplateEditorActivity.java')
s=p.read_text()
if 'import android.text.*;' not in s:s=s.replace('import android.os.*;\n','import android.os.*;\nimport android.text.*;\n',1)

# Add discoverable PROPERTIES button next to COLOR.
old='''        tool(tools1,"COLOR",Color.rgb(90,75,130),v->editColor());\n        tool(tools1,"LAYERS",Color.rgb(70,80,90),v->showLayers());'''
new='''        tool(tools1,"COLOR",Color.rgb(90,75,130),v->editColor());\n        tool(tools1,"PROPERTIES",Color.rgb(75,90,125),v->openSelectedProperties());\n        tool(tools1,"LAYERS",Color.rgb(70,80,90),v->showLayers());'''
if old not in s: raise SystemExit('0.8.6 properties tool target missing')
s=s.replace(old,new,1)

old='''    void editSelectedText(){OverlayProcessor.Element x=canvas.selectedElement();if(x==null||!"text".equals(x.type))return;EditText e=input("Text or token",x.text);new AlertDialog.Builder(this).setTitle("Edit text").setMessage("Tokens: {PLAYER_NAME}, {NUMBER}, {POSITION}, {TEAM}, {EVENT}, {DATE}, {PHOTOGRAPHER}").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{pushUndo();x.text=e.getText().toString();canvas.invalidate();}).show();}\n'''
new=r'''    private String templateSnapshot(){try{return template.toJson().toString();}catch(Exception e){return "";}}
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
        size.setOnSeekBarChangeListener(seek(v->{x.textSize=.01f+size.getProgress()/1000f;canvas.invalidate();}));opacity.setOnSeekBarChangeListener(seek(v->{x.alpha=opacity.getProgress()/100f;canvas.invalidate();}));stroke.setOnSeekBarChangeListener(seek(v->{x.strokeWidth=.012f*stroke.getProgress()/100f;canvas.invalidate();}));shadow.setOnSeekBarChangeListener(seek(v->{x.shadowRadius=.02f*shadow.getProgress()/100f;canvas.invalidate();}));
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
'''
if old not in s: raise SystemExit('0.8.6 text properties method target missing')
s=s.replace(old,new,1)

# Preview: photo hue.
old='''paint.setAlpha(255);paint.setColorFilter(null);c.drawBitmap(photo,null,d,paint);'''
new='''paint.setAlpha(255);paint.setColorFilter(Math.abs(template.photoHue)>.01f?OverlayProcessor.imageColorFilter(Color.WHITE,0f,template.photoHue):null);c.drawBitmap(photo,null,d,paint);paint.setColorFilter(null);'''
if old not in s: raise SystemExit('0.8.6 canvas photo hue target missing')
s=s.replace(old,new,1)

# Preview: styled text + image hue.
old='''paint.setStyle(Paint.Style.FILL);paint.setColor(e.color);paint.setTextSize(Math.max(dp(10),e.textSize*base));paint.setTypeface(Typeface.create(e.font,e.bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextAlign("right".equals(e.align)?Paint.Align.RIGHT:("center".equals(e.align)?Paint.Align.CENTER:Paint.Align.LEFT));String[] lines=displayText(e.text).split("\\\\n",-1);float line=paint.getTextSize()*1.08f;for(int k=0;k<lines.length;k++)c.drawText(lines[k],x,y+k*line,paint);'''
new='''paint.setStyle(Paint.Style.FILL);paint.setColor(e.color);paint.setTextSize(Math.max(dp(10),e.textSize*base));int style=(e.bold?Typeface.BOLD:0)|(e.italic?Typeface.ITALIC:0);paint.setTypeface(Typeface.create(e.font,style));paint.setTextAlign("right".equals(e.align)?Paint.Align.RIGHT:("center".equals(e.align)?Paint.Align.CENTER:Paint.Align.LEFT));if(e.shadowRadius>0f)paint.setShadowLayer(Math.max(1f,e.shadowRadius*base),e.shadowDx*base,e.shadowDy*base,e.shadowColor);String[] lines=displayText(e.text).split("\\\\n",-1);float line=paint.getTextSize()*1.08f;for(int k=0;k<lines.length;k++){float yy=y+k*line;if(e.strokeWidth>0f){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1f,e.strokeWidth*base));paint.setColor(e.strokeColor);c.drawText(lines[k],x,yy,paint);}paint.setStyle(Paint.Style.FILL);paint.setColor(e.color);c.drawText(lines[k],x,yy,paint);}paint.clearShadowLayer();'''
if old not in s: raise SystemExit('0.8.6 canvas text preview target missing')
s=s.replace(old,new,1)
old='''if(e.tint>0.001f)paint.setColorFilter(OverlayProcessor.tintFilter(e.tintColor,e.tint));RectF d='''
new='''paint.setColorFilter(OverlayProcessor.imageColorFilter(e.tintColor,e.tint,e.hue));RectF d='''
if old not in s: raise SystemExit('0.8.6 canvas image hue target missing')
s=s.replace(old,new,1)

# Double-tap dispatch: text -> Text Properties; image/photo -> Image Properties.
old='''case MotionEvent.ACTION_UP:{long now=System.currentTimeMillis();if(!gestureChanged&&selected>=0){if(now-lastTap<360){OverlayProcessor.Element e=selectedElement();if(e!=null&&"text".equals(e.type))editSelectedText();lastTap=0;}else lastTap=now;}return true;}'''
new='''case MotionEvent.ACTION_UP:{long now=System.currentTimeMillis();if(!gestureChanged&&selected!=SEL_NONE){if(now-lastTap<360){if(selected==SEL_PHOTO)editSelectedImage();else{OverlayProcessor.Element e=selectedElement();if(e!=null&&"text".equals(e.type))editSelectedText();else if(e!=null&&"image".equals(e.type))editSelectedImage();}lastTap=0;}else lastTap=now;}return true;}'''
if old not in s: raise SystemExit('0.8.6 double-tap dispatch target missing')
s=s.replace(old,new,1)

s=s.replace('Camera Auto Upload 0.8.5','Camera Auto Upload 0.8.6').replace('Version 0.8.5 beta','Version 0.8.6 beta')
p.write_text(s)

# Version after 0.8.5.
p=Path('nikon-auto-upload/app/build.gradle')
s=p.read_text().replace('versionCode 30','versionCode 31').replace("versionName '0.8.5'","versionName '0.8.6'")
p.write_text(s)

p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.8.5 beta','Version 0.8.6 beta')
    s+='''\n\nTEXT + IMAGE PROPERTIES (0.8.6)\n--------------------------------\nDouble-tap any text layer to open Text Properties over the canvas. Text content, font family, size, text color, bold, italic, alignment, opacity, outline color/width, and shadow color/strength update live while the window is open. Apply keeps the edit; Cancel restores the layer.\n\nDouble-tap the main photo, a PNG frame, logo, or other image layer to open Image Properties. Drag Hue from -180 to +180 degrees to shift colors while keeping transparency and shading. PNG/image layers also keep the existing optional tint controls.\n'''
    p.write_text(s)
