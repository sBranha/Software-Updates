from pathlib import Path

# 0.8.0 Photo Overlay / Sports Card module.
# This patch is intentionally applied AFTER all camera transport patches.
# It hooks only into the post-import saved-photo path and leaves USB-C, FTP,
# Wi-Fi, Bluetooth, PTP and camera-card behavior intact.

# ----- DirectTransferService: create/queue second overlay copy after import -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s=p.read_text()
old='''                boolean send=flickrEnabled()&&isFlickrUploadable(originalName);String uriText=uri.toString();
                if(send){addPending(uri);queueAlbumBase(uriText,FlickrClient.albumBaseTitle(System.currentTimeMillis(),effectiveSuffix()));}else addToSet("phone_only_uris",uriText);'''
new='''                boolean send=flickrEnabled()&&isFlickrUploadable(originalName);String uriText=uri.toString();
                OverlayProcessor.ProcessResult overlay=OverlayProcessor.onImported(this,p,uri,originalName);
                boolean queueOriginal=send&&overlay.queueOriginal;
                if(queueOriginal){addPending(uri);queueAlbumBase(uriText,FlickrClient.albumBaseTitle(System.currentTimeMillis(),effectiveSuffix()));}else addToSet("phone_only_uris",uriText);
                if(overlay.overlayUri!=null){
                    String overlayText=overlay.overlayUri.toString();
                    if(send&&overlay.queueOverlay){addPending(overlay.overlayUri);queueAlbumBase(overlayText,FlickrClient.albumBaseTitle(System.currentTimeMillis(),effectiveSuffix()));}
                    else if(!overlay.holdOverlay)addToSet("phone_only_uris",overlayText);
                    if(p.getBoolean("session_active",false)){addToSet("current_session_uris",overlayText);mapSessionUri(overlayText,p.getString("session_name",""));}
                    p.edit().putInt("overlay_completed_count",p.getInt("overlay_completed_count",0)+1).apply();
                }'''
if old not in s: raise SystemExit('0.8.0 overlay post-import target missing')
s=s.replace(old,new,1)

# Append overlay state to the existing receive event without changing the
# existing source-specific USB/FTP diagnostics.
old='''                addEvent(send?("Received from "+receiveSource+": "+originalName+" — queued for Flickr"):("Received from "+receiveSource+": "+originalName+" — phone only"));notifyEvent("Photo received from "+receiveSource,originalName);'''
new='''                String overlayNote=(overlay.message==null||overlay.message.isEmpty())?"":(" • "+overlay.message);
                addEvent((send?("Received from "+receiveSource+": "+originalName+" — Flickr workflow"):("Received from "+receiveSource+": "+originalName+" — phone only"))+overlayNote);notifyEvent("Photo received from "+receiveSource,originalName);'''
if old in s:s=s.replace(old,new,1)
p.write_text(s)

# ----- MainActivity: entry point only; no transport ownership changes -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text()
needle='''        Button usbQuick=smallButton("USB-C WIRED IMPORT",green);usbQuick.setOnClickListener(v->startActivity(new Intent(this,UsbCameraActivity.class)));controls.addView(usbQuick);
        Switch flickr=new Switch(this);'''
replacement='''        Button usbQuick=smallButton("USB-C WIRED IMPORT",green);usbQuick.setOnClickListener(v->startActivity(new Intent(this,UsbCameraActivity.class)));controls.addView(usbQuick);
        Button overlays=smallButton("PHOTO OVERLAYS / SPORTS CARDS",blue);overlays.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));controls.addView(overlays);
        Switch flickr=new Switch(this);'''
if needle not in s: raise SystemExit('0.8.0 Home overlay entry target missing')
s=s.replace(needle,replacement,1)

# Add a compact settings entry too.
needle='''        Button usbMethod=smallButton("USB-C WIRED",green);usbMethod.setOnClickListener(v->startActivity(new Intent(this,UsbCameraActivity.class)));methods.addView(usbMethod);'''
replacement='''        Button usbMethod=smallButton("USB-C WIRED",green);usbMethod.setOnClickListener(v->startActivity(new Intent(this,UsbCameraActivity.class)));methods.addView(usbMethod);
        Button overlayMethod=smallButton("PHOTO OVERLAYS / SPORTS CARDS",blue);overlayMethod.setOnClickListener(v->startActivity(new Intent(this,OverlayStudioActivity.class)));methods.addView(overlayMethod);'''
if needle in s:s=s.replace(needle,replacement,1)

s=s.replace('Nikon Auto Upload 0.7.1','Nikon Auto Upload 0.8.0').replace('Version 0.7.1 beta','Version 0.8.0 beta')
p.write_text(s)

# ----- Manifest -----
p=Path('nikon-auto-upload/app/src/main/AndroidManifest.xml')
s=p.read_text()
if '.OverlayStudioActivity' not in s:
    marker='        <activity android:name=".MainActivity" android:exported="true">\n'
    if marker not in s: raise SystemExit('0.8.0 manifest activity target missing')
    s=s.replace(marker,'        <activity android:name=".OverlayStudioActivity" android:exported="false"/>\n'+marker,1)
p.write_text(s)

# ----- Version -----
p=Path('nikon-auto-upload/app/build.gradle')
s=p.read_text().replace("versionCode 24","versionCode 25").replace("versionName '0.7.1'","versionName '0.8.0'")
p.write_text(s)

# ----- Help -----
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.7.1 beta','Version 0.8.0 beta')
    s += '''\n\n0.8.0 PHOTO OVERLAY / SPORTS CARD MODULE\n----------------------------------------\nPhoto Overlays is a separate post-import processing module. It never edits the original camera file or camera memory card and does not own USB-C, FTP, Wi-Fi, Bluetooth or PTP connections.\n\nThe Overlay Studio supports reusable Baseball, Softball, Football, Basketball, Team/Event, Watermark and custom templates. Templates may contain editable text, team/player/event/date/photographer tokens, transparent PNG/logo graphics, movable/resizable/rotatable elements, font choices, text size and opacity. Portrait and landscape templates are selected separately.\n\nShoot Setup lets you choose team/event/player details, portrait and landscape templates, automatic overlay ON/OFF, optional hold-for-preview, and Flickr output: Original only, Overlay/Card only, or Both.\n\nAutomatic flow: Camera photo -> normal camera card -> normal app import -> untouched original saved on phone -> optional second overlay JPEG created -> selected output(s) enter the existing Flickr queue. If internet is unavailable, the existing Flickr queue waits exactly as before.\n\nIf Hold for preview is enabled, the finished card is saved but is not placed in the Flickr pending queue until APPROVE FOR FLICKR is pressed. KEEP PHONE ONLY leaves the processed copy on the phone.\n'''
    p.write_text(s)
