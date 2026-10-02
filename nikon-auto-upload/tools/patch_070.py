from pathlib import Path

# ----- MainActivity: connection chooser + IP/network status -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text()

old='''    void handleCallback(Intent i){
        Uri d=i.getData();'''
new='''    void handleCallback(Intent i){
        if(i!=null&&i.getBooleanExtra("open_settings",false)){drawSettings();}
        Uri d=i==null?null:i.getData();'''
if old not in s: raise SystemExit('0.7.0 handleCallback target missing')
s=s.replace(old,new,1)

old='''            StringBuilder b=new StringBuilder();
            b.append("Camera: ").append(p.getString("ftp_state","Waiting for Z8")).append('\\n');'''
new='''            StringBuilder b=new StringBuilder();
            b.append("Connection: ").append(ConnectionStatus.cameraConnection(p)).append('\\n');
            b.append("IP address: ").append(ConnectionStatus.ipv4(this,p)).append('\\n');
            b.append("Network: ").append(ConnectionStatus.networkSummary(this)).append('\\n');
            b.append("FTP receiver: ").append(p.getString("ftp_state","Waiting for Z8")).append('\\n');'''
if old not in s: raise SystemExit('0.7.0 Home status target missing')
s=s.replace(old,new,1)

# Earlier camera-profile patches expanded the original Camera Connection card,
# so inject the new modular chooser immediately after Settings opens instead of
# replacing that existing working card.
old='''    void drawSettings(){
        base("Settings","Settings");'''
new='''    void drawSettings(){
        base("Settings","Settings");
        sectionHeader("Connection Methods");LinearLayout methods=cardBox();
        methods.addView(statusRow("Current connection",ConnectionStatus.cameraConnection(p),green));
        methods.addView(statusRow("IP address",ConnectionStatus.ipv4(this,p),muted));
        methods.addView(statusRow("Network",ConnectionStatus.networkSummary(this),muted));
        Button choose=smallButton("OPEN CAMERA CONNECTIONS",blue);choose.setOnClickListener(v->startActivity(new Intent(this,ConnectionActivity.class)));methods.addView(choose);
        Button usb=smallButton("USB-C WIRED IMPORT",green);usb.setOnClickListener(v->startActivity(new Intent(this,UsbCameraActivity.class)));methods.addView(usb);body.addView(methods);'''
if old not in s: raise SystemExit('0.7.0 Settings insertion target missing')
s=s.replace(old,new,1)

for oldver in ['Nikon Auto Upload 0.6.2','Nikon Auto Upload 0.6.1','Nikon Auto Upload 0.6.0']:
    s=s.replace(oldver,'Nikon Auto Upload 0.7.0')
p.write_text(s)

# ----- DirectTransferService: USB imports share workflow without depending on FTP pause state -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s=p.read_text()
old='''            try{
                if(p.getBoolean("receiver_paused",false)){file.delete();broadcast("Z8 receiving is paused",findBestLocalIp());return;}'''
new='''            try{
                boolean usbImport=file!=null&&file.getAbsolutePath().replace('\\\\','/').contains("/usb_import/");
                if(p.getBoolean("receiver_paused",false)&&!usbImport){file.delete();broadcast("Z8 receiving is paused",findBestLocalIp());return;}'''
if old not in s: raise SystemExit('0.7.0 USB pause-bypass target missing')
s=s.replace(old,new,1)

old='''                boolean send=flickrEnabled();String uriText=uri.toString();'''
new='''                boolean send=flickrEnabled()&&isFlickrUploadFormat(originalName);String uriText=uri.toString();'''
if old not in s: raise SystemExit('0.7.0 Flickr-format target missing')
s=s.replace(old,new,1)

old='''.putString("last_received_name",originalName).putLong("last_received_time",now).putString("ftp_state","Photo received").apply();'''
new='''.putString("last_received_name",originalName).putLong("last_received_time",now).putString(usbImport?"usb_state":"ftp_state",usbImport?"USB import complete":"Photo received").apply();'''
if old not in s: raise SystemExit('0.7.0 source-state target missing')
s=s.replace(old,new,1)

old='''                addEvent(send?"Received from Z8: "+originalName+" — queued for Flickr":"Received from Z8: "+originalName+" — phone only");notifyEvent("Photo received from Nikon Z8",originalName);'''
new='''                String sourceName=usbImport?"USB-C camera":"Z8";
                addEvent(send?("Received from "+sourceName+": "+originalName+" — queued for Flickr"):("Received from "+sourceName+": "+originalName+" — phone only"));notifyEvent("Photo received from "+sourceName,originalName);'''
if old not in s: raise SystemExit('0.7.0 source-label target missing')
s=s.replace(old,new,1)

old='''    private Uri saveToGallery(File file,String name) throws Exception {
        String low=name.toLowerCase(Locale.US),mime=(low.endsWith(".jpeg")||low.endsWith(".jpg"))?"image/jpeg":"application/octet-stream";'''
new='''    private boolean isFlickrUploadFormat(String name){
        String x=name==null?"":name.toLowerCase(Locale.US);
        return x.endsWith(".jpg")||x.endsWith(".jpeg")||x.endsWith(".png")||x.endsWith(".gif");
    }

    private Uri saveToGallery(File file,String name) throws Exception {
        String low=name.toLowerCase(Locale.US),mime;
        if(low.endsWith(".jpeg")||low.endsWith(".jpg"))mime="image/jpeg";
        else if(low.endsWith(".nef")||low.endsWith(".nrw"))mime="image/x-nikon-nef";
        else if(low.endsWith(".arw"))mime="image/x-sony-arw";
        else if(low.endsWith(".cr2"))mime="image/x-canon-cr2";
        else if(low.endsWith(".cr3"))mime="image/x-canon-cr3";
        else if(low.endsWith(".raf"))mime="image/x-fujifilm-raf";
        else if(low.endsWith(".dng"))mime="image/dng";
        else if(low.endsWith(".tif")||low.endsWith(".tiff"))mime="image/tiff";
        else if(low.endsWith(".png"))mime="image/png";
        else mime="application/octet-stream";'''
if old not in s: raise SystemExit('0.7.0 MIME target missing')
s=s.replace(old,new,1)
p.write_text(s)

# ----- Manifest: Android USB Host + auto-detect activity -----
p=Path('nikon-auto-upload/app/src/main/AndroidManifest.xml')
s=p.read_text()
if 'android.hardware.usb.host' not in s:
    s=s.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">','''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.hardware.usb.host" android:required="false"/>''',1)
marker='''        <activity android:name=".MainActivity" android:exported="true">'''
usb_activities='''        <activity android:name=".ConnectionActivity" android:exported="false"/>
        <activity android:name=".UsbCameraActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"/>
            </intent-filter>
            <meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" android:resource="@xml/usb_device_filter"/>
        </activity>
        <activity android:name=".MainActivity" android:exported="true">'''
if marker not in s: raise SystemExit('0.7.0 MainActivity manifest target missing')
s=s.replace(marker,usb_activities,1)
p.write_text(s)

# ----- Help file -----
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text()
    for v in ['Version 0.6.2 beta','Version 0.6.1 beta','Version 0.6.0 beta']:
        s=s.replace(v,'Version 0.7.0 beta')
    s+='''\n\nUSB-C WIRED CAMERA IMPORT (0.7.0)\n----------------------------------\nUSB-C is a separate offline import module and does not replace or alter Wi-Fi/FTP or Nikon Bluetooth.\n\n1. Use a data-capable USB-C cable / OTG connection from camera to Android phone.\n2. On first connection, Android may ask which app should handle the USB camera and may ask permission. Choose Nikon Auto Upload and allow access.\n3. Open Settings > Connection Methods > USB-C WIRED IMPORT if it does not open automatically.\n4. The USB screen shows camera identity, photos on the card, file size/type, transfer progress, and success/failure status.\n5. Select one or more files and tap IMPORT SELECTED, or tap IMPORT NEW for files the app has not imported before.\n6. Optional automatic import watches for newly-shot files while the USB screen is connected. Existing card files are used as the initial baseline so the first auto session does not pull the whole card.\n7. JPEG and supported RAW filenames (including Nikon NEF) are copied at full resolution. RAW files are preserved on the phone; formats Flickr does not accept are kept phone-only.\n8. Imported files enter the same normal phone/Flickr/session pipeline as wireless transfers. Internet is not required for USB importing; Flickr waits until internet/cellular is available.\n9. Nothing is deleted from the camera automatically. DELETE SELECTED FROM CAMERA always requires explicit confirmation.\n\nThe USB transport uses standard Android USB Host plus USB Still Image/PTP. The transport is brand-neutral so compatible Nikon, Canon, Sony, Fujifilm, and other cameras can be added without changing the existing wireless modules.\n\nCONNECTION STATUS\n-----------------\nHome > Status and Settings > Connection Methods now show the current camera connection, phone IPv4 address, and active network(s). USB-C correctly reports that a camera IP address is not required.\n'''
    p.write_text(s)
