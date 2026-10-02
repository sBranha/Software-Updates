from pathlib import Path

# Camera Auto Upload 0.8.2
# True layer-based sports-card template editor. This patch runs after 0.8.1.
# It does NOT change USB-C, Bluetooth, Wi-Fi, FTP, PTP, import, or Flickr ownership.

# -----------------------------------------------------------------------------
# OverlayProcessor: every template part can have its own visibility + image tint.
# -----------------------------------------------------------------------------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayProcessor.java')
s=p.read_text()

old='''        public int color=Color.WHITE;\n        public boolean bold=true,fill=false;'''
new='''        public int color=Color.WHITE;\n        public int tintColor=Color.WHITE;\n        public float tint=0f;\n        public boolean bold=true,fill=false,visible=true;'''
if old not in s: raise SystemExit('0.8.2 Element fields target missing')
s=s.replace(old,new,1)

old='''            o.put("color",color).put("bold",bold).put("fill",fill);return o;'''
new='''            o.put("color",color).put("tintColor",tintColor).put("tint",tint).put("bold",bold).put("fill",fill).put("visible",visible);return o;'''
if old not in s: raise SystemExit('0.8.2 Element JSON write target missing')
s=s.replace(old,new,1)

old='''e.color=o.optInt("color",Color.WHITE);e.bold=o.optBoolean("bold",true);e.fill=o.optBoolean("fill",false);return e;'''
new='''e.color=o.optInt("color",Color.WHITE);e.tintColor=o.optInt("tintColor",Color.WHITE);e.tint=(float)o.optDouble("tint",0);e.bold=o.optBoolean("bold",true);e.fill=o.optBoolean("fill",false);e.visible=o.optBoolean("visible",true);return e;'''
if old not in s: raise SystemExit('0.8.2 Element JSON read target missing')
s=s.replace(old,new,1)

old='''        for(Element e:t.elements){if(e==null)continue;int alpha=Math.max(0,Math.min(255,(int)(255f*e.alpha)));canvas.save();'''
new='''        for(Element e:t.elements){if(e==null||!e.visible)continue;int alpha=Math.max(0,Math.min(255,(int)(255f*e.alpha)));canvas.save();'''
if old not in s: raise SystemExit('0.8.2 render visibility target missing')
s=s.replace(old,new,1)

old='''File f=new File(e.assetPath==null?"":e.assetPath);if(f.exists()){Bitmap img=BitmapFactory.decodeFile(f.getAbsolutePath());if(img!=null){Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);paint.setAlpha(alpha);RectF dst='''
new='''File f=new File(e.assetPath==null?"":e.assetPath);if(f.exists()){Bitmap img=BitmapFactory.decodeFile(f.getAbsolutePath());if(img!=null){Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);paint.setAlpha(alpha);if(e.tint>0.001f)paint.setColorFilter(tintFilter(e.tintColor,e.tint));RectF dst='''
if old not in s: raise SystemExit('0.8.2 image tint render target missing')
s=s.replace(old,new,1)

marker='''    private static Bitmap decode(Context c,Uri uri,long maxPixels) throws IOException {'''
helper='''    static ColorFilter tintFilter(int color,float amount){\n        float t=Math.max(0f,Math.min(1f,amount)),k=1f-t;\n        float rr=Color.red(color)/255f,gg=Color.green(color)/255f,bb=Color.blue(color)/255f;\n        float lr=.2126f,lg=.7152f,lb=.0722f;\n        ColorMatrix m=new ColorMatrix(new float[]{\n            k+t*rr*lr,t*rr*lg,t*rr*lb,0,0,\n            t*gg*lr,k+t*gg*lg,t*gg*lb,0,0,\n            t*bb*lr,t*bb*lg,k+t*bb*lb,0,0,\n            0,0,0,1,0});\n        return new ColorMatrixColorFilter(m);\n    }\n\n'''
if marker not in s: raise SystemExit('0.8.2 tint helper marker missing')
s=s.replace(marker,helper+marker,1)
p.write_text(s)

# -----------------------------------------------------------------------------
# OverlayStudioActivity: layers, real-photo editor preview, nudge/size/order,
# per-part colors/tints, visibility, duplication, and team color presets.
# -----------------------------------------------------------------------------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayStudioActivity.java')
s=p.read_text()

s=s.replace('private static final int REQ_LOGO=301,REQ_PREVIEW=302,REQ_BASE=303;',
            'private static final int REQ_LOGO=301,REQ_PREVIEW=302,REQ_BASE=303,REQ_EDITOR_PHOTO=304;',1)

# Add real test-photo button directly above the editor canvas.
old='''        body.addView(text("Use a transparent PNG from Canva, Photoshop, etc. Leave spaces such as a circle for the player number, then place editable fields over those spots.",12,muted,false));\n        editorView=new EditorView(this,editingTemplate);body.addView(editorView,new LinearLayout.LayoutParams(-1,dp(420)));'''
new='''        body.addView(text("Use a transparent PNG from Canva, Photoshop, etc. Leave spaces such as a circle for the player number, then place editable fields over those spots.",12,muted,false));\n        Button testPhoto=button("CHOOSE TEST PHOTO BEHIND TEMPLATE",blue);testPhoto.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_EDITOR_PHOTO);});body.addView(testPhoto,new LinearLayout.LayoutParams(-1,dp(52)));\n        editorView=new EditorView(this,editingTemplate);body.addView(editorView,new LinearLayout.LayoutParams(-1,dp(420)));'''
if old not in s: raise SystemExit('0.8.2 editor test-photo target missing')
s=s.replace(old,new,1)

# Layer manager and color theme controls below Add buttons.
old='''        LinearLayout add=new LinearLayout(this);Button addText=button("+ TEXT",blue);addText.setOnClickListener(v->addText());add.addView(addText,new LinearLayout.LayoutParams(0,dp(52),1));Button addLogo=button("+ LOGO / PNG",green);addLogo.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_LOGO);});add.addView(addLogo,new LinearLayout.LayoutParams(0,dp(52),1));Button addRect=button("+ GRAPHIC",Color.rgb(70,80,90));addRect.setOnClickListener(v->{OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="rect";e.x=.15f;e.y=.15f;e.w=.7f;e.h=.7f;e.fill=false;e.alpha=.8f;editingTemplate.elements.add(e);editorView.selected=editingTemplate.elements.size()-1;editorView.invalidate();syncControls();});add.addView(addRect,new LinearLayout.LayoutParams(0,dp(52),1));body.addView(add);'''
new=old+'''\n        LinearLayout layerCard=card();layerCard.addView(text("TEMPLATE PARTS / LAYERS",14,white,true));layerCard.addView(text("Every text field, shape, logo and imported PNG is its own editable layer. Select one, then edit only that part.",12,muted,false));Button layers=button("OPEN LAYER LIST",blue);layers.setOnClickListener(v->showLayerManager());layerCard.addView(layers);body.addView(layerCard);\n        LinearLayout themes=card();themes.addView(text("COLOR THEMES",14,white,true));themes.addView(text("Presets recolor editable text and shape layers. You can still change every layer individually afterward.",12,muted,false));LinearLayout tr1=new LinearLayout(this);Button rb=button("RED / BLACK",Color.rgb(150,20,24));rb.setOnClickListener(v->applyTheme(Color.rgb(190,25,30),Color.BLACK,Color.WHITE));tr1.addView(rb,new LinearLayout.LayoutParams(0,dp(48),1));Button rg=button("RED / GREEN",Color.rgb(35,125,65));rg.setOnClickListener(v->applyTheme(Color.rgb(200,25,30),Color.rgb(25,135,65),Color.WHITE));tr1.addView(rg,new LinearLayout.LayoutParams(0,dp(48),1));themes.addView(tr1);LinearLayout tr2=new LinearLayout(this);Button wb=button("WHITE / BLUE",Color.rgb(35,100,190));wb.setOnClickListener(v->applyTheme(Color.WHITE,Color.rgb(30,95,185),Color.rgb(15,35,70)));tr2.addView(wb,new LinearLayout.LayoutParams(0,dp(48),1));Button bgold=button("BLACK / GOLD",Color.rgb(145,105,20));bgold.setOnClickListener(v->applyTheme(Color.BLACK,Color.rgb(218,165,32),Color.WHITE));tr2.addView(bgold,new LinearLayout.LayoutParams(0,dp(48),1));themes.addView(tr2);Button customTheme=button("CUSTOM PRIMARY / SECONDARY / TEXT COLORS",Color.rgb(70,80,90));customTheme.setOnClickListener(v->customThemeDialog());themes.addView(customTheme);body.addView(themes);'''
if old not in s: raise SystemExit('0.8.2 layer/theme insertion target missing')
s=s.replace(old,new,1)

# More selected-part controls after color/fill controls added in 0.8.1.
old='''        CheckBox fillShape=new CheckBox(this);fillShape.setText("Fill selected shape");fillShape.setTextColor(white);fillShape.setOnCheckedChangeListener((b,on)->{OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e!=null&&"rect".equals(e.type)){e.fill=on;editorView.invalidate();}});select.addView(fillShape);'''
new=old+'''\n        CheckBox visible=new CheckBox(this);visible.setText("Show selected layer");visible.setTextColor(white);visible.setChecked(true);visible.setOnCheckedChangeListener((b,on)->{OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e!=null){e.visible=on;editorView.invalidate();}});select.addView(visible);\n        Button imageTint=button("IMAGE LAYER TINT / RECOLOR",Color.rgb(70,80,90));imageTint.setOnClickListener(v->chooseImageTint());select.addView(imageTint,new LinearLayout.LayoutParams(-1,dp(50)));\n        select.addView(text("Fine position",12,muted,true));LinearLayout n1=new LinearLayout(this);Button up=button("↑",Color.rgb(70,80,90));up.setOnClickListener(v->nudge(0,-.005f));n1.addView(up,new LinearLayout.LayoutParams(0,dp(46),1));Button down=button("↓",Color.rgb(70,80,90));down.setOnClickListener(v->nudge(0,.005f));n1.addView(down,new LinearLayout.LayoutParams(0,dp(46),1));Button leftN=button("←",Color.rgb(70,80,90));leftN.setOnClickListener(v->nudge(-.005f,0));n1.addView(leftN,new LinearLayout.LayoutParams(0,dp(46),1));Button rightN=button("→",Color.rgb(70,80,90));rightN.setOnClickListener(v->nudge(.005f,0));n1.addView(rightN,new LinearLayout.LayoutParams(0,dp(46),1));select.addView(n1);\n        select.addView(text("Independent width / height",12,muted,true));LinearLayout n2=new LinearLayout(this);Button wm=button("WIDTH −",Color.rgb(70,80,90));wm.setOnClickListener(v->resizeSelected(-.02f,0));n2.addView(wm,new LinearLayout.LayoutParams(0,dp(46),1));Button wp=button("WIDTH +",Color.rgb(70,80,90));wp.setOnClickListener(v->resizeSelected(.02f,0));n2.addView(wp,new LinearLayout.LayoutParams(0,dp(46),1));Button hm=button("HEIGHT −",Color.rgb(70,80,90));hm.setOnClickListener(v->resizeSelected(0,-.02f));n2.addView(hm,new LinearLayout.LayoutParams(0,dp(46),1));Button hp=button("HEIGHT +",Color.rgb(70,80,90));hp.setOnClickListener(v->resizeSelected(0,.02f));n2.addView(hp,new LinearLayout.LayoutParams(0,dp(46),1));select.addView(n2);\n        select.addView(text("Layer order",12,muted,true));LinearLayout n3=new LinearLayout(this);Button backLayer=button("SEND BACK",Color.rgb(70,80,90));backLayer.setOnClickListener(v->moveLayer(-1));n3.addView(backLayer,new LinearLayout.LayoutParams(0,dp(46),1));Button frontLayer=button("BRING FORWARD",Color.rgb(70,80,90));frontLayer.setOnClickListener(v->moveLayer(1));n3.addView(frontLayer,new LinearLayout.LayoutParams(0,dp(46),1));Button dupLayer=button("DUPLICATE PART",amber);dupLayer.setOnClickListener(v->duplicateSelected());n3.addView(dupLayer,new LinearLayout.LayoutParams(0,dp(46),1));select.addView(n3);'''
if old not in s: raise SystemExit('0.8.2 selected-layer extras target missing')
s=s.replace(old,new,1)

# Selection controls update visibility too.
old='''fillShape.setChecked(e.fill);opacity.setProgress((int)(e.alpha*100));'''
new='''fillShape.setChecked(e.fill);visible.setChecked(e.visible);opacity.setProgress((int)(e.alpha*100));'''
if old not in s: raise SystemExit('0.8.2 selection visible sync target missing')
s=s.replace(old,new,1)

# Add helper methods before activity result.
marker='''    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){'''
helpers=r'''    private String layerName(OverlayProcessor.Element e,int index){
        if(e==null)return "Layer "+(index+1);if("text".equals(e.type)){String x=e.text==null?"Text":e.text;return "TEXT • "+(x.length()>30?x.substring(0,30)+"…":x);}if("image".equals(e.type))return index==0?"IMAGE • Template artwork":"IMAGE • Logo / graphic";return "SHAPE • "+(e.fill?"Filled":"Outline");
    }
    private void showLayerManager(){
        if(editingTemplate==null||editorView==null)return;if(editingTemplate.elements.isEmpty()){toast("This template has no layers yet");return;}String[] names=new String[editingTemplate.elements.size()];for(int i=0;i<names.length;i++){OverlayProcessor.Element e=editingTemplate.elements.get(i);names[i]=(e.visible?"● ":"○ ")+layerName(e,i);}
        new AlertDialog.Builder(this).setTitle("Template layers — bottom to top").setItems(names,(d,which)->{editorView.selected=which;editorView.invalidate();syncControls();toast("Selected "+layerName(editingTemplate.elements.get(which),which));}).setNegativeButton("Close",null).show();
    }
    private void nudge(float dx,float dy){OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e==null){toast("Select a layer first");return;}e.x=Math.max(-.25f,Math.min(1.25f,e.x+dx));e.y=Math.max(-.25f,Math.min(1.25f,e.y+dy));editorView.invalidate();}
    private void resizeSelected(float dw,float dh){OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e==null){toast("Select a layer first");return;}if("text".equals(e.type)){e.textSize=Math.max(.012f,Math.min(.20f,e.textSize+(dw!=0?dw*.12f:dh*.12f)));}else{e.w=Math.max(.02f,Math.min(1.5f,e.w+dw));e.h=Math.max(.02f,Math.min(1.5f,e.h+dh));}editorView.invalidate();syncControls();}
    private void moveLayer(int delta){if(editorView==null||editingTemplate==null||editorView.selected<0)return;int from=editorView.selected,to=Math.max(0,Math.min(editingTemplate.elements.size()-1,from+delta));if(from==to)return;OverlayProcessor.Element e=editingTemplate.elements.remove(from);editingTemplate.elements.add(to,e);editorView.selected=to;editorView.invalidate();toast(delta>0?"Layer moved forward":"Layer moved back");}
    private void duplicateSelected(){OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e==null){toast("Select a layer first");return;}OverlayProcessor.Element copy=e.copy();copy.id=UUID.randomUUID().toString();copy.x=Math.min(1.2f,copy.x+.025f);copy.y=Math.min(1.2f,copy.y+.025f);editingTemplate.elements.add(editorView.selected+1,copy);editorView.selected++;editorView.invalidate();syncControls();toast("Layer duplicated");}
    private void applyTheme(int primary,int secondary,int textColor){if(editingTemplate==null)return;int shape=0;for(OverlayProcessor.Element e:editingTemplate.elements){if(e==null||"image".equals(e.type))continue;if("rect".equals(e.type)){e.color=(shape++%2==0)?primary:secondary;}else if("text".equals(e.type)){String x=e.text==null?"":e.text.toUpperCase(Locale.US);e.color=(x.contains("TEAM")||x.contains("EVENT")||x.contains("DATE"))?secondary:textColor;}}editorView.invalidate();toast("Color theme applied — each layer is still editable");}
    private void customThemeDialog(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);EditText a=input("Primary #RRGGBB","#CC2027"),b=input("Secondary #RRGGBB","#111111"),c=input("Text #RRGGBB","#FFFFFF");box.addView(a);box.addView(b);box.addView(c);new AlertDialog.Builder(this).setTitle("Custom template colors").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{try{applyTheme(Color.parseColor(a.getText().toString().trim()),Color.parseColor(b.getText().toString().trim()),Color.parseColor(c.getText().toString().trim()));}catch(Exception ex){toast("Use colors like #CC2027");}}).show();}
    private void chooseImageTint(){OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e==null||!"image".equals(e.type)){toast("Select an image / PNG layer first");return;}LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);EditText hex=input("Tint color #RRGGBB",String.format(Locale.US,"#%06X",0xFFFFFF&e.tintColor));box.addView(hex);TextView label=text("Tint strength: "+Math.round(e.tint*100)+"%",12,muted,true);box.addView(label);SeekBar amount=new SeekBar(this);amount.setMax(100);amount.setProgress(Math.round(e.tint*100));amount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int v,boolean from){label.setText("Tint strength: "+v+"%");}});box.addView(amount);new AlertDialog.Builder(this).setTitle("Recolor image layer").setMessage("0% keeps the original PNG colors. Higher values shift the artwork toward your chosen color while keeping light/dark shading.").setView(box).setNegativeButton("Cancel",null).setNeutralButton("CLEAR TINT",(d,w)->{e.tint=0;e.tintColor=Color.WHITE;editorView.invalidate();}).setPositiveButton("Apply",(d,w)->{try{e.tintColor=Color.parseColor(hex.getText().toString().trim());e.tint=amount.getProgress()/100f;editorView.invalidate();}catch(Exception ex){toast("Use a color like #1E88E5");}}).show();}

'''
if marker not in s: raise SystemExit('0.8.2 helper insertion marker missing')
s=s.replace(marker,helpers+marker,1)

# Editor photo result handling before base/logo handlers.
old='''Uri u=data.getData();if(requestCode==REQ_BASE&&editingTemplate!=null){'''
new='''Uri u=data.getData();if(requestCode==REQ_EDITOR_PHOTO&&editorView!=null){editorView.loadPhoto(u);toast("Test photo loaded behind template");}else if(requestCode==REQ_BASE&&editingTemplate!=null){'''
if old not in s: raise SystemExit('0.8.2 editor photo result target missing')
s=s.replace(old,new,1)

# Imported base is full-frame and selected as a normal layer.
old='''e.type="image";e.assetPath=path;e.x=.5f;e.y=.5f;e.w=1f;e.h=1f;editingTemplate.elements.add(0,e);'''
new='''e.type="image";e.assetPath=path;e.x=.5f;e.y=.5f;e.w=1f;e.h=1f;e.tint=0f;editingTemplate.elements.add(0,e);'''
if old not in s: raise SystemExit('0.8.2 base image setup target missing')
s=s.replace(old,new,1)

# EditorView: real test photo, real image artwork, visibility, tint.
old='''final OverlayProcessor.Template template;int selected=-1;Runnable selectionChanged;Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);RectF canvasRect=new RectF();float lastX,lastY,startDistance,startAngle,startW,startH,startText,startRotation;boolean twoFinger=false;'''
new='''final OverlayProcessor.Template template;int selected=-1;Runnable selectionChanged;Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);RectF canvasRect=new RectF();Bitmap photoBitmap;float lastX,lastY,startDistance,startAngle,startW,startH,startText,startRotation;boolean twoFinger=false;'''
if old not in s: raise SystemExit('0.8.2 EditorView fields target missing')
s=s.replace(old,new,1)

old='''        OverlayProcessor.Element selectedElement(){return selected>=0&&selected<template.elements.size()?template.elements.get(selected):null;}'''
new='''        OverlayProcessor.Element selectedElement(){return selected>=0&&selected<template.elements.size()?template.elements.get(selected):null;}\n        void loadPhoto(Uri u){try{ImageDecoder.Source src=ImageDecoder.createSource(getContentResolver(),u);photoBitmap=ImageDecoder.decodeBitmap(src,(d,info,source)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();float sc=Math.min(1f,1600f/Math.max(w,h));if(sc<1f)d.setTargetSize(Math.max(1,(int)(w*sc)),Math.max(1,(int)(h*sc)));d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});invalidate();}catch(Exception ex){toast("Could not load test photo: "+ex.getMessage());}}'''
if old not in s: raise SystemExit('0.8.2 EditorView loadPhoto target missing')
s=s.replace(old,new,1)

old='''paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(38,48,58));c.drawRect(canvasRect,paint);paint.setShader(new LinearGradient(l,t,l+w,t+h,Color.rgb(60,75,90),Color.rgb(18,24,30),Shader.TileMode.CLAMP));c.drawRect(canvasRect,paint);paint.setShader(null);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(16));paint.setColor(Color.argb(140,255,255,255));c.drawText("PHOTO",canvasRect.centerX(),canvasRect.centerY(),paint);'''
new='''paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(38,48,58));c.drawRect(canvasRect,paint);if(photoBitmap!=null){paint.setAlpha(255);paint.setColorFilter(null);c.drawBitmap(photoBitmap,null,canvasRect,paint);}else{paint.setShader(new LinearGradient(l,t,l+w,t+h,Color.rgb(60,75,90),Color.rgb(18,24,30),Shader.TileMode.CLAMP));c.drawRect(canvasRect,paint);paint.setShader(null);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(16));paint.setColor(Color.argb(140,255,255,255));c.drawText("CHOOSE TEST PHOTO",canvasRect.centerX(),canvasRect.centerY(),paint);}'''
if old not in s: raise SystemExit('0.8.2 editor background target missing')
s=s.replace(old,new,1)

old='''for(int i=0;i<template.elements.size();i++){OverlayProcessor.Element e=template.elements.get(i);float x=l+e.x*w,y=t+e.y*h;c.save();'''
new='''for(int i=0;i<template.elements.size();i++){OverlayProcessor.Element e=template.elements.get(i);if(e==null||!e.visible)continue;float x=l+e.x*w,y=t+e.y*h;c.save();'''
if old not in s: raise SystemExit('0.8.2 editor visibility target missing')
s=s.replace(old,new,1)

old='''else if("image".equals(e.type)){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb((int)(200*e.alpha),33,150,243));c.drawRect(x-e.w*w/2f,y-e.h*h/2f,x+e.w*w/2f,y+e.h*h/2f,paint);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(11));paint.setColor(Color.WHITE);c.drawText("LOGO / PNG",x,y,paint);}'''
new='''else if("image".equals(e.type)){File f=new File(e.assetPath==null?"":e.assetPath);Bitmap img=f.exists()?BitmapFactory.decodeFile(f.getAbsolutePath()):null;if(img!=null){paint.setStyle(Paint.Style.FILL);paint.setAlpha((int)(255*e.alpha));paint.setColorFilter(e.tint>0.001f?OverlayProcessor.tintFilter(e.tintColor,e.tint):null);RectF dst=new RectF(x-e.w*w/2f,y-e.h*h/2f,x+e.w*w/2f,y+e.h*h/2f);c.drawBitmap(img,null,dst,paint);paint.setColorFilter(null);img.recycle();}else{paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb((int)(180*e.alpha),33,150,243));c.drawRect(x-e.w*w/2f,y-e.h*h/2f,x+e.w*w/2f,y+e.h*h/2f,paint);}}'''
if old not in s: raise SystemExit('0.8.2 editor real-image target missing')
s=s.replace(old,new,1)

# Hit-test ignores hidden layers.
old='''for(int i=template.elements.size()-1;i>=0;i--){OverlayProcessor.Element e=template.elements.get(i);float x='''
new='''for(int i=template.elements.size()-1;i>=0;i--){OverlayProcessor.Element e=template.elements.get(i);if(e==null||!e.visible)continue;float x='''
if old not in s: raise SystemExit('0.8.2 editor hit visibility target missing')
s=s.replace(old,new,1)

p.write_text(s)

# Version after 0.8.1 patch.
p=Path('nikon-auto-upload/app/build.gradle')
s=p.read_text().replace('versionCode 26','versionCode 27').replace("versionName '0.8.1'","versionName '0.8.2'")
p.write_text(s)

# Help/version text.
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.8.1 beta','Version 0.8.2 beta')
    s += '''\n\n0.8.2 FULL TEMPLATE EDITOR\n--------------------------\nThe Sports Card editor is now layer based. Text, player-number fields, shapes, logos and imported PNG artwork are individually selectable template parts. Open LAYER LIST to select a part, then move, nudge, resize width/height, rotate, adjust transparency, change color, show/hide, duplicate, delete, and move the layer forward/back.\n\nChoose TEST PHOTO BEHIND TEMPLATE to edit the overlay directly over a real photo. Imported PNG artwork renders in the editor instead of as a placeholder. Image/PNG layers can also receive a tint/recolor while preserving transparent areas and light/dark shading.\n\nColor presets include Red/Black, Red/Green, White/Blue and Black/Gold, plus custom Primary/Secondary/Text colors. Presets recolor editable text and shape layers; every part can still be changed individually after applying a preset.\n\nAll template editing remains separate from the camera transport modules. The original camera/phone photo is never edited; finished card output is a second processed copy.\n'''
    p.write_text(s)
