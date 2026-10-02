from pathlib import Path

# Camera Auto Upload 0.8.3
# Dedicated full-screen direct-touch template editor page.
# Runs after 0.8.2. Camera transports and Flickr ownership are not changed.

# Keep the 0.8.2 editor compile fix used by the previous build.
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayStudioActivity.java')
s=p.read_text()
if 'import java.io.File;' not in s:
    marker='import java.util.*;\n'
    if marker not in s: raise SystemExit('0.8.3 java.io.File import marker missing')
    s=s.replace(marker,'import java.io.File;\n'+marker,1)

# All template editing now launches the separate full-screen touch editor.
old='''        section("Reusable templates");Button add=button("+ NEW CUSTOM TEMPLATE",green);add.setOnClickListener(v->{OverlayProcessor.Template t=new OverlayProcessor.Template();t.name="Custom Template";t.category="Custom";t.orientation="landscape";drawEditor(t);});body.addView(add);'''
new='''        section("Reusable templates");Button add=button("+ NEW CUSTOM TEMPLATE",green);add.setOnClickListener(v->chooseNewTemplateOrientation());body.addView(add);'''
if old not in s: raise SystemExit('0.8.3 new-template target missing')
s=s.replace(old,new,1)

old='''Button edit=button("EDIT",Color.rgb(70,80,90));edit.setOnClickListener(v->drawEditor(t));'''
new='''Button edit=button("EDIT",Color.rgb(70,80,90));edit.setOnClickListener(v->openTouchEditor(t));'''
if old not in s: raise SystemExit('0.8.3 edit-template target missing')
s=s.replace(old,new,1)

# Add player position to shoot setup for POSITION token.
old='''EditText player=input("Player name",p.getString("overlay_player",""));setup.addView(player);EditText number=input("Player number",p.getString("overlay_number",""));setup.addView(number);EditText photographer=input("Photographer / logo text",p.getString("overlay_photographer",""));setup.addView(photographer);'''
new='''EditText player=input("Player name",p.getString("overlay_player",""));setup.addView(player);EditText number=input("Player number",p.getString("overlay_number",""));setup.addView(number);EditText position=input("Player position",p.getString("overlay_position",""));setup.addView(position);EditText photographer=input("Photographer / logo text",p.getString("overlay_photographer",""));setup.addView(photographer);'''
if old not in s: raise SystemExit('0.8.3 position field target missing')
s=s.replace(old,new,1)

old='''.putString("overlay_player",player.getText().toString().trim()).putString("overlay_number",number.getText().toString().trim()).putString("overlay_photographer",photographer.getText().toString().trim())'''
new='''.putString("overlay_player",player.getText().toString().trim()).putString("overlay_number",number.getText().toString().trim()).putString("overlay_position",position.getText().toString().trim()).putString("overlay_photographer",photographer.getText().toString().trim())'''
if old not in s: raise SystemExit('0.8.3 position save target missing')
s=s.replace(old,new,1)

# Flag allows main template list to refresh after returning from the dedicated editor.
field='''    private ImageView previewImage;'''
if field not in s: raise SystemExit('0.8.3 refresh flag field target missing')
s=s.replace(field,field+'\n    private boolean returningFromTouchEditor=false;',1)

marker='''    private String cameraName(){'''
helpers='''    private void openTouchEditor(OverlayProcessor.Template t){\n        if(t==null)return;OverlayProcessor.saveTemplate(this,t);returningFromTouchEditor=true;Intent i=new Intent(this,TouchTemplateEditorActivity.class);i.putExtra("template_id",t.id);startActivity(i);\n    }\n\n    private void chooseNewTemplateOrientation(){\n        String[] choices={"Portrait","Landscape"};new AlertDialog.Builder(this).setTitle("New template orientation").setItems(choices,(d,which)->{OverlayProcessor.Template t=new OverlayProcessor.Template();t.name="Custom Template";t.category="Custom";t.orientation=which==0?"portrait":"landscape";OverlayProcessor.saveTemplate(this,t);openTouchEditor(t);}).setNegativeButton("Cancel",null).show();\n    }\n\n    @Override protected void onResume(){super.onResume();if(returningFromTouchEditor){returningFromTouchEditor=false;editingTemplate=null;drawMain();}}\n\n'''
if marker not in s: raise SystemExit('0.8.3 helper insertion target missing')
s=s.replace(marker,helpers+marker,1)

s=s.replace('Camera Auto Upload 0.8.2','Camera Auto Upload 0.8.3').replace('Version 0.8.2 beta','Version 0.8.3 beta')
p.write_text(s)

# POSITION token in rendered output.
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayProcessor.java')
s=p.read_text()
old='''.replace("{NUMBER}",p.getString("overlay_number","")).replace("{TEAM}",p.getString("overlay_team",""))'''
new='''.replace("{NUMBER}",p.getString("overlay_number","")).replace("{POSITION}",p.getString("overlay_position","")).replace("{TEAM}",p.getString("overlay_team",""))'''
if old not in s: raise SystemExit('0.8.3 POSITION token target missing')
s=s.replace(old,new,1)
p.write_text(s)

# Register the editor as its own Activity/page in the app.
p=Path('nikon-auto-upload/app/src/main/AndroidManifest.xml')
s=p.read_text()
if '.TouchTemplateEditorActivity' not in s:
    marker='''        <activity android:name=".OverlayStudioActivity" android:exported="false"/>\n'''
    if marker not in s: raise SystemExit('0.8.3 manifest overlay target missing')
    s=s.replace(marker,marker+'        <activity android:name=".TouchTemplateEditorActivity" android:exported="false"/>\n',1)
p.write_text(s)

# Version metadata after 0.8.2 patch.
p=Path('nikon-auto-upload/app/build.gradle')
s=p.read_text().replace('versionCode 27','versionCode 28').replace("versionName '0.8.2'","versionName '0.8.3'")
p.write_text(s)

# Help.
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.8.2 beta','Version 0.8.3 beta')
    s+='''\n\nFULL-SCREEN TOUCH TEMPLATE EDITOR (0.8.3)\n-----------------------------------------\nTemplate editing now opens on its own page inside Camera Auto Upload. Tap the player name, number, position, logo, PNG frame or another item directly on the card. Drag with one finger. Pinch with two fingers to resize. Twist with two fingers to rotate. Double-tap text to edit it. Tap blank canvas and pinch to zoom the whole workspace for precise placement.\n'''
    p.write_text(s)
