from pathlib import Path

# 0.8.1: Imported custom card artwork, quick movable player fields, element
# colors, and Phone Camera as another post-import source. This patch runs after
# 0.8.0 and does not change USB-C, FTP, Wi-Fi, Bluetooth or PTP transports.

# ----- Overlay Studio -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayStudioActivity.java')
s=p.read_text()
s=s.replace('private static final int REQ_LOGO=301,REQ_PREVIEW=302;',
            'private static final int REQ_LOGO=301,REQ_PREVIEW=302,REQ_TEMPLATE=303;',1)

old='''        section("Reusable templates");Button add=button("+ NEW CUSTOM TEMPLATE",green);add.setOnClickListener(v->{OverlayProcessor.Template t=new OverlayProcessor.Template();t.name="Custom Template";t.category="Custom";t.orientation="landscape";drawEditor(t);});body.addView(add);'''
new='''        section("Reusable templates");
        Button importTemplate=button("IMPORT MY TEMPLATE PNG / JPG",blue);importTemplate.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_TEMPLATE);});body.addView(importTemplate);
        TextView importHelp=text("Transparent PNG works best: leave transparent/open areas for the photo, then add movable Player #, Player Name, Team, Event or custom text on top.",12,muted,false);importHelp.setPadding(dp(6),dp(4),dp(6),dp(10));body.addView(importHelp);
        Button add=button("+ NEW CUSTOM TEMPLATE",green);add.setOnClickListener(v->{OverlayProcessor.Template t=new OverlayProcessor.Template();t.name="Custom Template";t.category="Custom";t.orientation="landscape";drawEditor(t);});body.addView(add);'''
if old not in s: raise SystemExit('0.8.1 reusable-template target missing')
s=s.replace(old,new,1)

old='''        LinearLayout add=new LinearLayout(this);Button addText=button("+ TEXT",blue);addText.setOnClickListener(v->addText());add.addView(addText,new LinearLayout.LayoutParams(0,dp(52),1));Button addLogo=button("+ LOGO / PNG",green);addLogo.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_LOGO);});add.addView(addLogo,new LinearLayout.LayoutParams(0,dp(52),1));Button addRect=button("+ GRAPHIC",Color.rgb(70,80,90));addRect.setOnClickListener(v->{OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="rect";e.x=.15f;e.y=.15f;e.w=.7f;e.h=.7f;e.fill=false;e.alpha=.8f;editingTemplate.elements.add(e);editorView.selected=editingTemplate.elements.size()-1;editorView.invalidate();syncControls();});add.addView(addRect,new LinearLayout.LayoutParams(0,dp(52),1));body.addView(add);'''
new='''        LinearLayout add=new LinearLayout(this);Button addText=button("+ TEXT",blue);addText.setOnClickListener(v->addText());add.addView(addText,new LinearLayout.LayoutParams(0,dp(52),1));Button addLogo=button("+ LOGO / PNG",green);addLogo.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_LOGO);});add.addView(addLogo,new LinearLayout.LayoutParams(0,dp(52),1));Button addRect=button("+ GRAPHIC",Color.rgb(70,80,90));addRect.setOnClickListener(v->{OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="rect";e.x=.15f;e.y=.15f;e.w=.7f;e.h=.7f;e.fill=false;e.alpha=.8f;editingTemplate.elements.add(e);editorView.selected=editingTemplate.elements.size()-1;editorView.invalidate();syncControls();});add.addView(addRect,new LinearLayout.LayoutParams(0,dp(52),1));body.addView(add);
        LinearLayout quick=new LinearLayout(this);Button addNumber=button("+ PLAYER #",amber);addNumber.setOnClickListener(v->addQuickText("{NUMBER}",.085f));quick.addView(addNumber,new LinearLayout.LayoutParams(0,dp(52),1));Button addPlayer=button("+ PLAYER NAME",blue);addPlayer.setOnClickListener(v->addQuickText("{PLAYER_NAME}",.060f));quick.addView(addPlayer,new LinearLayout.LayoutParams(0,dp(52),1));Button addTeam=button("+ TEAM",Color.rgb(70,80,90));addTeam.setOnClickListener(v->addQuickText("{TEAM}",.045f));quick.addView(addTeam,new LinearLayout.LayoutParams(0,dp(52),1));body.addView(quick);'''
if old not in s: raise SystemExit('0.8.1 editor-add row target missing')
s=s.replace(old,new,1)

old='''        LinearLayout select=card();select.addView(text("Selected element controls",14,white,true));Button editText=button("EDIT TEXT / TOKENS",blue);editText.setOnClickListener(v->editSelectedText());select.addView(editText);'''
new='''        LinearLayout select=card();select.addView(text("Selected element controls",14,white,true));Button editText=button("EDIT TEXT / TOKENS",blue);editText.setOnClickListener(v->editSelectedText());select.addView(editText);Button colorPick=button("CHOOSE ELEMENT COLOR",amber);colorPick.setOnClickListener(v->chooseElementColor());select.addView(colorPick);'''
if old not in s: raise SystemExit('0.8.1 selected-controls target missing')
s=s.replace(old,new,1)

marker='''    private void editSelectedText(){OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e==null||!"text".equals(e.type)){toast("Select a text element first");return;}EditText x=input("Text / tokens",e.text);new AlertDialog.Builder(this).setTitle("Edit text").setMessage("Tokens: {PLAYER_NAME}, {NUMBER}, {TEAM}, {EVENT}, {DATE}, {PHOTOGRAPHER}").setView(x).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{e.text=x.getText().toString();editorView.invalidate();}).show();}

'''
helpers='''    private void editSelectedText(){OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e==null||!"text".equals(e.type)){toast("Select a text element first");return;}EditText x=input("Text / tokens",e.text);new AlertDialog.Builder(this).setTitle("Edit text").setMessage("Tokens: {PLAYER_NAME}, {NUMBER}, {TEAM}, {EVENT}, {DATE}, {PHOTOGRAPHER}").setView(x).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{e.text=x.getText().toString();editorView.invalidate();}).show();}

    private void addQuickText(String token,float size){if(editingTemplate==null||editorView==null)return;OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="text";e.text=token;e.x=.50f;e.y=.50f;e.align="center";e.textSize=size;e.bold=true;e.color=Color.WHITE;editingTemplate.elements.add(e);editorView.selected=editingTemplate.elements.size()-1;editorView.invalidate();syncControls();toast("Drag it to the exact spot on your design");}

    private void chooseElementColor(){
        OverlayProcessor.Element e=editorView==null?null:editorView.selectedElement();if(e==null){toast("Select a text or graphic element first");return;}if("image".equals(e.type)){toast("PNG/logo colors come from the uploaded image");return;}
        final String[] names={"White","Black","Red","Blue","Green","Gold","Orange","Purple","Light Gray","Custom HEX…"};
        final int[] colors={Color.WHITE,Color.BLACK,Color.rgb(220,40,40),Color.rgb(30,110,230),Color.rgb(35,170,85),Color.rgb(255,193,7),Color.rgb(255,130,25),Color.rgb(145,70,210),Color.LTGRAY};
        new AlertDialog.Builder(this).setTitle("Element color").setItems(names,(d,which)->{if(which<colors.length){e.color=colors[which];editorView.invalidate();}else showCustomColor(e);}).show();
    }

    private void showCustomColor(OverlayProcessor.Element e){EditText hex=input("HEX color — example #0057B8",String.format(Locale.US,"#%06X",0xFFFFFF&(e.color)));new AlertDialog.Builder(this).setTitle("Custom color").setView(hex).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{try{String value=hex.getText().toString().trim();if(!value.startsWith("#"))value="#"+value;e.color=Color.parseColor(value);if(editorView!=null)editorView.invalidate();}catch(Exception ex){toast("Enter a color like #0057B8");}}).show();}

'''
if marker not in s: raise SystemExit('0.8.1 helper insertion target missing')
s=s.replace(marker,helpers,1)

old='''    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(requestCode==REQ_LOGO&&editingTemplate!=null){try{String path=OverlayProcessor.copyAssetIntoModule(this,u,"overlay.png");OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="image";e.assetPath=path;e.x=.5f;e.y=.5f;e.w=.28f;e.h=.28f;editingTemplate.elements.add(e);editorView.selected=editingTemplate.elements.size()-1;editorView.invalidate();toast("Graphic added");}catch(Exception e){toast("Could not add graphic: "+e.getMessage());}}else if(requestCode==REQ_PREVIEW){previewSource=u;renderPreview();}}'''
new='''    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(requestCode==REQ_TEMPLATE){try{String path=OverlayProcessor.copyAssetIntoModule(this,u,"template.png");OverlayProcessor.Template t=new OverlayProcessor.Template();t.name="My Imported Template";t.category="Imported";t.orientation=isPortrait(u)?"portrait":"landscape";OverlayProcessor.Element art=new OverlayProcessor.Element();art.type="image";art.assetPath=path;art.x=.5f;art.y=.5f;art.w=1f;art.h=1f;art.alpha=1f;t.elements.add(art);drawEditor(t);toast("Template loaded — add Player # and drag it into place");}catch(Exception e){toast("Could not import template: "+e.getMessage());}}else if(requestCode==REQ_LOGO&&editingTemplate!=null){try{String path=OverlayProcessor.copyAssetIntoModule(this,u,"overlay.png");OverlayProcessor.Element e=new OverlayProcessor.Element();e.type="image";e.assetPath=path;e.x=.5f;e.y=.5f;e.w=.28f;e.h=.28f;editingTemplate.elements.add(e);editorView.selected=editingTemplate.elements.size()-1;editorView.invalidate();toast("Graphic added");}catch(Exception e){toast("Could not add graphic: "+e.getMessage());}}else if(requestCode==REQ_PREVIEW){previewSource=u;renderPreview();}}'''
if old not in s: raise SystemExit('0.8.1 activity-result target missing')
s=s.replace(old,new,1)
p.write_text(s)

# ----- DirectTransferService: already-saved phone-camera Uri -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s=p.read_text()
needle='''        if("pause_receiver".equals(cmd)){pauseReceiver();return START_STICKY;}'''
replacement='''        if("import_uri".equals(cmd)){
            String uriText=intent.getStringExtra("uri"),name=intent.getStringExtra("file_name"),source=intent.getStringExtra("source_label");
            if(uriText!=null&&!uriText.trim().isEmpty()){final Uri importedUri=Uri.parse(uriText);final String importedName=(name==null||name.trim().isEmpty())?"PHONE_"+System.currentTimeMillis()+".jpg":name;final String sourceLabel=(source==null||source.trim().isEmpty())?"Phone Camera":source;io.submit(()->processExistingUri(importedUri,importedName,sourceLabel));}
            return START_STICKY;
        }
        if("pause_receiver".equals(cmd)){pauseReceiver();return START_STICKY;}'''
if needle not in s: raise SystemExit('0.8.1 import_uri command target missing')
s=s.replace(needle,replacement,1)

marker='''    @Override public void onPhotoReceived(File file,String originalName){'''
method='''    private void processExistingUri(Uri uri,String originalName,String receiveSource){
        try{
            boolean send=flickrEnabled()&&isFlickrUploadable(originalName);String uriText=uri.toString();
            OverlayProcessor.ProcessResult overlay=OverlayProcessor.onImported(this,p,uri,originalName);
            boolean queueOriginal=send&&overlay.queueOriginal;
            if(queueOriginal){addPending(uri);queueAlbumBase(uriText,FlickrClient.albumBaseTitle(System.currentTimeMillis(),effectiveSuffix()));}else addToSet("phone_only_uris",uriText);
            if(overlay.overlayUri!=null){String overlayText=overlay.overlayUri.toString();if(send&&overlay.queueOverlay){addPending(overlay.overlayUri);queueAlbumBase(overlayText,FlickrClient.albumBaseTitle(System.currentTimeMillis(),effectiveSuffix()));}else if(!overlay.holdOverlay)addToSet("phone_only_uris",overlayText);if(p.getBoolean("session_active",false)){addToSet("current_session_uris",overlayText);mapSessionUri(overlayText,p.getString("session_name",""));}p.edit().putInt("overlay_completed_count",p.getInt("overlay_completed_count",0)+1).apply();}
            if(p.getBoolean("session_active",false)){addToSet("current_session_uris",uriText);mapSessionUri(uriText,p.getString("session_name",""));p.edit().putInt("session_received",p.getInt("session_received",0)+1).apply();}
            long now=System.currentTimeMillis();p.edit().putInt("total_received",p.getInt("total_received",0)+1).putString("last_received_name",originalName).putLong("last_received_time",now).putString("phone_camera_state","Photo processed").apply();
            String overlayNote=(overlay.message==null||overlay.message.isEmpty())?"":(" • "+overlay.message);addEvent("Received from "+receiveSource+": "+originalName+overlayNote);notifyEvent("Photo received from "+receiveSource,originalName);
            if(send&&(queueOriginal||overlay.queueOverlay)){broadcast("Phone photo saved — selected version(s) queued for Flickr",findBestLocalIp());processPending();}else if(overlay.holdOverlay)broadcast("Phone photo saved — overlay is waiting for preview approval",findBestLocalIp());else broadcast("Phone photo saved on phone",findBestLocalIp());
        }catch(Exception e){notifyEvent("Phone camera workflow error",shortMessage(e));addEvent("Phone camera workflow error: "+shortMessage(e));broadcast("Phone camera workflow error: "+shortMessage(e),findBestLocalIp());}
    }

    @Override public void onPhotoReceived(File file,String originalName){'''
if marker not in s: raise SystemExit('0.8.1 processExistingUri target missing')
s=s.replace(marker,method,1)
p.write_text(s)

# ----- Home + Settings -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text()
old='''        Button overlays=smallButton("PHOTO OVERLAYS / SPORTS CARDS",blue);overlays.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));controls.addView(overlays);
        Switch flickr=new Switch(this);'''
new='''        Button overlays=smallButton("PHOTO OVERLAY / SPORTS CARDS",blue);overlays.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));controls.addView(overlays);
        Button phoneCamera=smallButton("PHONE CAMERA",green);phoneCamera.setOnClickListener(v->startActivity(new Intent(this,PhoneCameraActivity.class)));controls.addView(phoneCamera);
        Switch flickr=new Switch(this);'''
if old not in s: raise SystemExit('0.8.1 Home phone-camera target missing')
s=s.replace(old,new,1)
old='''        Button overlayMethod=smallButton("PHOTO OVERLAYS / SPORTS CARDS",blue);overlayMethod.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));methods.addView(overlayMethod);'''
new='''        Button overlayMethod=smallButton("PHOTO OVERLAY / SPORTS CARDS",blue);overlayMethod.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));methods.addView(overlayMethod);
        Button phoneMethod=smallButton("PHONE CAMERA",green);phoneMethod.setOnClickListener(v->startActivity(new Intent(this,PhoneCameraActivity.class)));methods.addView(phoneMethod);'''
if old in s:s=s.replace(old,new,1)
s=s.replace('Nikon Auto Upload 0.8.0','Camera Auto Upload 0.8.1').replace('Version 0.8.0 beta','Version 0.8.1 beta')
p.write_text(s)

# ----- Manifest -----
p=Path('nikon-auto-upload/app/src/main/AndroidManifest.xml')
s=p.read_text()
if '.PhoneCameraActivity' not in s:
    marker='        <activity android:name=".OverlayStudioActivity" android:exported="false"/>\n'
    if marker not in s: raise SystemExit('0.8.1 manifest overlay activity target missing')
    s=s.replace(marker,marker+'        <activity android:name=".PhoneCameraActivity" android:exported="false"/>\n',1)
s=s.replace('android:label="Nikon Auto Upload"','android:label="Camera Auto Upload"',1)
p.write_text(s)

# ----- Version -----
p=Path('nikon-auto-upload/app/build.gradle')
s=p.read_text().replace('versionCode 25','versionCode 26').replace("versionName '0.8.0'","versionName '0.8.1'")
p.write_text(s)

# ----- Help -----
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.8.0 beta','Version 0.8.1 beta')
    s += '''\n\n0.8.1 TEMPLATE IMPORT + PHONE CAMERA\n------------------------------------\nThe Overlay Studio is available from Home and Settings as PHOTO OVERLAY / SPORTS CARDS.\n\nIMPORT MY TEMPLATE PNG / JPG lets you bring in your own finished card artwork. Transparent PNG is recommended so the camera photo can show through open areas. The imported artwork becomes the base element of a reusable portrait or landscape template. Use + PLAYER #, + PLAYER NAME, + TEAM or + TEXT, then drag the field to the exact spot in your design (for example, the center of a number circle). Text and graphic elements have color choices including custom HEX colors, size, transparency, rotation and positioning. Saved templates retain those settings.\n\nPHONE CAMERA lets the phone itself become another camera source when an external camera is not available. It uses Android's normal camera app for a full-resolution JPEG, preserves the captured original, and hands it to the same optional overlay and Flickr workflow. Existing Nikon/Sony/Canon/Fujifilm, USB-C, Wi-Fi/FTP, hotspot, Bluetooth and PTP systems are unchanged.\n'''
    p.write_text(s)
