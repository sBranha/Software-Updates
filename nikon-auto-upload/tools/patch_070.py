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
            b.append("Camera: ").append(p.getString("ftp_state","Waiting for camera")).append('\\n');'''
if old not in s:
    old='''            StringBuilder b=new StringBuilder();
            b.append("Camera: ").append(p.getString("ftp_state","Waiting for Z8")).append('\\n');'''
new='''            StringBuilder b=new StringBuilder();
            b.append("Connection: ").append(ConnectionStatus.cameraConnection(p)).append('\\n');
            b.append("IP address: ").append(ConnectionStatus.ipv4(this,p)).append('\\n');
            b.append("Network: ").append(ConnectionStatus.networkSummary(this)).append('\\n');
            b.append("FTP receiver: ").append(p.getString("ftp_state","Waiting for camera")).append('\\n');'''
if old not in s: raise SystemExit('0.7.0 Home status target missing')
s=s.replace(old,new,1)

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

# ----- DirectTransferService: USB-C enters the same normal pipeline -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s=p.read_text()
old='''            try{
                if(p.getBoolean("receiver_paused",false)){file.delete();broadcast("Camera receiving is paused",findBestLocalIp());return;}'''
if old not in s:
    old='''            try{
                if(p.getBoolean("receiver_paused",false)){file.delete();broadcast("Z8 receiving is paused",findBestLocalIp());return;}'''
new='''            try{
                boolean usbImport=file!=null&&file.getAbsolutePath().replace('\\\\','/').contains("/usb_import/");
                if(p.getBoolean("receiver_paused",false)&&!usbImport){file.delete();broadcast("Camera receiving is paused",findBestLocalIp());return;}'''
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

old='''                addEvent(send?"Received from "+MultiCameraSupport.cameraName(p)+": "+originalName+" — queued for Flickr":"Received from "+MultiCameraSupport.cameraName(p)+": "+originalName+" — phone only");notifyEvent("Photo received from "+MultiCameraSupport.cameraName(p),originalName);'''
new='''                String sourceName=usbImport?"USB-C camera":MultiCameraSupport.cameraName(p);
                addEvent(send?("Received from "+sourceName+": "+originalName+" — queued for Flickr"):("Received from "+sourceName+": "+originalName+" — phone only"));notifyEvent("Photo received from "+sourceName,originalName);'''
if old in s:s=s.replace(old,new,1)

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

# ----- Android USB Host + attach detection -----
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

# ----- Help -----
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text()
    for v in ['Version 0.6.2 beta','Version 0.6.1 beta','Version 0.6.0 beta']:
        s=s.replace(v,'Version 0.7.0 beta')
    s+='''\n\nUSB-C WIRED CAMERA IMPORT (0.7.0)\n----------------------------------\nUSB-C is a separate offline module; Wi-Fi/FTP and Nikon Bluetooth remain intact. Plug a data-capable USB-C cable into the Android phone, allow Android USB access, then open Settings > Connection Methods > USB-C WIRED IMPORT. The USB screen can list compatible PTP camera photos, select one/many, import all unimported files, or automatically import newly-shot files while connected. JPEG and recognized RAW filenames such as NEF are copied at full resolution. RAW files that Flickr cannot accept are preserved phone-only. Nothing is deleted from the camera unless DELETE SELECTED FROM CAMERA is pressed and confirmed. Finished USB files enter the same phone gallery, session, queue, album/privacy, and Flickr workflow as wireless photos. USB import works fully offline.\n\nHome > Status and Settings > Connection Methods now show current connection type, phone IPv4 address, and active network(s). USB correctly reports that a camera IP is not required.\n'''
    p.write_text(s)
