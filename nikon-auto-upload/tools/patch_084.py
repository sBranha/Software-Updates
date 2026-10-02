from pathlib import Path

# Camera Auto Upload 0.8.4
# Photo-first Photoshop-style touch editor + locked automatic photo placement.
# Runs after 0.8.3. Camera transports remain untouched.

# -----------------------------------------------------------------------------
# OverlayProcessor: save photo placement with each template and use it when
# rendering every automatic incoming photo.
# -----------------------------------------------------------------------------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayProcessor.java')
s=p.read_text()
old='''        public String orientation="landscape"; // portrait / landscape\n        public final ArrayList<Element> elements=new ArrayList<>();'''
new='''        public String orientation="landscape"; // portrait / landscape\n        public float photoX=.5f,photoY=.5f,photoScale=1f,photoRotation=0f;\n        public final ArrayList<Element> elements=new ArrayList<>();'''
if old not in s: raise SystemExit('0.8.4 Template photo transform fields target missing')
s=s.replace(old,new,1)
old='''        JSONObject toJson() throws JSONException {JSONObject o=new JSONObject();o.put("id",id).put("name",name).put("category",category).put("orientation",orientation);JSONArray a=new JSONArray();for(Element e:elements)a.put(e.toJson());o.put("elements",a);return o;}'''
new='''        JSONObject toJson() throws JSONException {JSONObject o=new JSONObject();o.put("id",id).put("name",name).put("category",category).put("orientation",orientation).put("photoX",photoX).put("photoY",photoY).put("photoScale",photoScale).put("photoRotation",photoRotation);JSONArray a=new JSONArray();for(Element e:elements)a.put(e.toJson());o.put("elements",a);return o;}'''
if old not in s: raise SystemExit('0.8.4 Template JSON write target missing')
s=s.replace(old,new,1)
old='''        static Template fromJson(JSONObject o){Template t=new Template();t.id=o.optString("id",t.id);t.name=o.optString("name","Template");t.category=o.optString("category","Custom");t.orientation=o.optString("orientation","landscape");JSONArray a=o.optJSONArray("elements");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)t.elements.add(Element.fromJson(x));}return t;}'''
new='''        static Template fromJson(JSONObject o){Template t=new Template();t.id=o.optString("id",t.id);t.name=o.optString("name","Template");t.category=o.optString("category","Custom");t.orientation=o.optString("orientation","landscape");t.photoX=(float)o.optDouble("photoX",.5);t.photoY=(float)o.optDouble("photoY",.5);t.photoScale=(float)o.optDouble("photoScale",1);t.photoRotation=(float)o.optDouble("photoRotation",0);JSONArray a=o.optJSONArray("elements");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)t.elements.add(Element.fromJson(x));}return t;}'''
if old not in s: raise SystemExit('0.8.4 Template JSON read target missing')
s=s.replace(old,new,1)

old='''        Bitmap src=decode(c,original,maxPixels);Bitmap out=Bitmap.createBitmap(src.getWidth(),src.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);canvas.drawBitmap(src,0,0,null);float w=out.getWidth(),h=out.getHeight(),base=Math.min(w,h);'''
new='''        Bitmap src=decode(c,original,maxPixels);Bitmap out=Bitmap.createBitmap(src.getWidth(),src.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);float w=out.getWidth(),h=out.getHeight(),base=Math.min(w,h);\n        canvas.drawColor(Color.BLACK);float cover=Math.max(w/src.getWidth(),h/src.getHeight());float pw=src.getWidth()*cover*Math.max(.15f,t.photoScale),ph=src.getHeight()*cover*Math.max(.15f,t.photoScale);float pcx=t.photoX*w,pcy=t.photoY*h;Paint photoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);canvas.save();canvas.rotate(t.photoRotation,pcx,pcy);canvas.drawBitmap(src,null,new RectF(pcx-pw/2f,pcy-ph/2f,pcx+pw/2f,pcy+ph/2f),photoPaint);canvas.restore();'''
if old not in s: raise SystemExit('0.8.4 photo transform render target missing')
s=s.replace(old,new,1)
p.write_text(s)

# -----------------------------------------------------------------------------
# MainActivity: make Editor a real bottom navigation destination and route the
# old overlay shortcut into the new photo-first editor rather than the list UI.
# -----------------------------------------------------------------------------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text()
old='''        addNav("⌂","Home",active);addNav("▣","Photos",active);addNav("⇧","Uploads",active);addNav("⚙","Settings",active);'''
new='''        addNav("⌂","Home",active);addNav("▣","Photos",active);addNav("⇧","Uploads",active);addNav("✎","Editor",active);addNav("⚙","Settings",active);'''
if old not in s: raise SystemExit('0.8.4 bottom nav target missing')
s=s.replace(old,new,1)
old='''        TextView t=txt(icon+"\\n"+label,11,label.equals(active)?blue:muted,label.equals(active));t.setGravity(Gravity.CENTER);t.setLineSpacing(0,.95f);t.setOnClickListener(v->{if(label.equals("Home"))drawHome();else if(label.equals("Photos"))drawPhotos();else if(label.equals("Uploads"))drawUploads();else drawSettings();});nav.addView(t,new LinearLayout.LayoutParams(0,-1,1));'''
new='''        TextView t=txt(icon+"\\n"+label,11,label.equals(active)?blue:muted,label.equals(active));t.setGravity(Gravity.CENTER);t.setLineSpacing(0,.95f);t.setOnClickListener(v->{if(label.equals("Home"))drawHome();else if(label.equals("Photos"))drawPhotos();else if(label.equals("Uploads"))drawUploads();else if(label.equals("Editor")){Intent i=new Intent(this,TouchTemplateEditorActivity.class);i.putExtra("photo_editor",true);startActivity(i);}else drawSettings();});nav.addView(t,new LinearLayout.LayoutParams(0,-1,1));'''
if old not in s: raise SystemExit('0.8.4 bottom nav action target missing')
s=s.replace(old,new,1)

old='''Button overlays=smallButton("PHOTO OVERLAYS / SPORTS CARDS",blue);overlays.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));'''
new='''Button overlays=smallButton("PHOTO EDITOR",blue);overlays.setOnClickListener(v->{Intent i=new Intent(this,TouchTemplateEditorActivity.class);i.putExtra("photo_editor",true);startActivity(i);});'''
if old in s:s=s.replace(old,new,1)
old='''Button overlayMethod=smallButton("PHOTO OVERLAYS / SPORTS CARDS",blue);overlayMethod.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));'''
new='''Button overlayMethod=smallButton("PHOTO EDITOR",blue);overlayMethod.setOnClickListener(v->{Intent i=new Intent(this,TouchTemplateEditorActivity.class);i.putExtra("photo_editor",true);startActivity(i);});'''
if old in s:s=s.replace(old,new,1)

# Show locked automatic layout state on Home without mixing it into camera status.
needle='''        sectionHeader("Status");LinearLayout statusCard=cardBox();'''
insert='''        sectionHeader("Editor automation");LinearLayout editorCard=cardBox();boolean editorLocked=p.getBoolean("editor_layout_locked",false);editorCard.addView(txt(editorLocked?"●  LOCKED AUTO LAYOUT":"○  Manual editor",14,editorLocked?green:muted,true));editorCard.addView(txt(editorLocked?"Incoming JPEGs keep the original, create the edited copy automatically, then follow your Flickr output choice.":"Open Editor from the bottom bar, build the layout, then tap LOCK when you want incoming photos processed automatically.",12,muted,false));Button openEditor=smallButton("OPEN PHOTO EDITOR",blue);openEditor.setOnClickListener(v->{Intent i=new Intent(this,TouchTemplateEditorActivity.class);i.putExtra("photo_editor",true);startActivity(i);});editorCard.addView(openEditor);body.addView(editorCard);\n\n        sectionHeader("Status");LinearLayout statusCard=cardBox();'''
if needle not in s: raise SystemExit('0.8.4 editor automation card target missing')
s=s.replace(needle,insert,1)
s=s.replace('Camera Auto Upload 0.8.3','Camera Auto Upload 0.8.4').replace('Nikon Auto Upload 0.8.3','Camera Auto Upload 0.8.4').replace('Version 0.8.3 beta','Version 0.8.4 beta')
p.write_text(s)

# Version after 0.8.3.
p=Path('nikon-auto-upload/app/build.gradle')
s=p.read_text().replace('versionCode 28','versionCode 29').replace("versionName '0.8.3'","versionName '0.8.4'")
p.write_text(s)

# Help.
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.8.3 beta','Version 0.8.4 beta')
    s+='''\n\nPHOTO-FIRST EDITOR + LOCKED AUTO LAYOUT (0.8.4)\n------------------------------------------------\nEditor is now a bottom navigation destination. Choose any phone/gallery photo, then edit directly on the canvas. Tap the PHOTO itself to move it under the overlay. Drag with one finger, pinch to zoom, and twist to rotate. Text, number, position, PNG frames, logos, shapes and other graphics are separate direct-touch layers. The workspace also supports undo/redo, layer order, duplication, deletion, grid and fit controls.\n\nLOCK saves the current portrait/landscape layout and its photo placement and enables automatic overlay processing for incoming JPEGs. The original import remains untouched. The edited copy follows the chosen Original / Edited / Both Flickr queue setting, and the existing Flickr queue still waits safely when internet is unavailable.\n'''
    p.write_text(s)
