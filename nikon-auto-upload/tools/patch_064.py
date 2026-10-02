from pathlib import Path

# 0.7.0: Add the modular USB-C/PTP import entry points and richer live
# connection/network/IP status without changing the existing Wi-Fi/FTP or Nikon
# Bluetooth owners.

# ----- MainActivity -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text()

# Home status should recognize USB-C and the Bluetooth->Wi-Fi handoff as real
# camera connections, while preserving the existing FTP detection.
s=s.replace(
'        boolean cameraConnected=isCameraConnected(),cellReady=cell.toLowerCase(Locale.US).contains("ready");',
'''        String connectionMode=ConnectionStatus.cameraConnection(p);
        boolean cameraConnected=isCameraConnected()||connectionMode.startsWith("USB-C")||connectionMode.startsWith("Bluetooth →"),cellReady=cell.toLowerCase(Locale.US).contains("ready");''',1)
s=s.replace('chip(MultiCameraSupport.networkLabel(p),cameraConnected?green:muted)',
            'chip(connectionMode,cameraConnected?green:muted)',1)

# Quick wired-import entry point on Home.
needle='''        sectionHeader("Quick controls");LinearLayout controls=cardBox();
        Switch flickr=new Switch(this);'''
replacement='''        sectionHeader("Quick controls");LinearLayout controls=cardBox();
        Button usbQuick=smallButton("USB-C WIRED IMPORT",green);usbQuick.setOnClickListener(v->startActivity(new Intent(this,UsbCameraActivity.class)));controls.addView(usbQuick);
        Switch flickr=new Switch(this);'''
if needle not in s: raise SystemExit('0.7.0 quick-control target missing')
s=s.replace(needle,replacement,1)

# Status card: connection type + local IP + active network(s).
needle='''            StringBuilder b=new StringBuilder();
            b.append("Camera: ").append(p.getString("ftp_state","Waiting for Z8")).append('\\n');'''
replacement='''            StringBuilder b=new StringBuilder();
            b.append("Connection: ").append(ConnectionStatus.cameraConnection(p)).append('\\n');
            b.append("IP address: ").append(ConnectionStatus.ipv4(this,p)).append('\\n');
            b.append("Network: ").append(ConnectionStatus.networkSummary(this)).append('\\n');
            String cameraIp=p.getString("nikon_handoff_camera_ip","");if(!cameraIp.isEmpty())b.append("Camera IP: ").append(cameraIp).append('\\n');
            b.append("Camera: ").append(p.getString("ftp_state","Waiting for camera")).append('\\n');'''
if needle not in s: raise SystemExit('0.7.0 live-status target missing')
s=s.replace(needle,replacement,1)

# USB gets its own READY health state and does not depend on Wi-Fi/cellular.
needle='''    Health health(){
        boolean running=p.getBoolean("receiver_running",false),paused=p.getBoolean("receiver_paused",false),flickr=p.getBoolean("flickr_upload_enabled",true);long free=freeBytes();int battery=phoneBattery();'''
replacement='''    Health health(){
        boolean running=p.getBoolean("receiver_running",false),paused=p.getBoolean("receiver_paused",false),flickr=p.getBoolean("flickr_upload_enabled",true);long free=freeBytes();int battery=phoneBattery();
        String usbState=p.getString("usb_state","").toLowerCase(Locale.US);
        if(usbState.contains("connected")||usbState.contains("ready")||usbState.contains("import"))return new Health("USB-C CAMERA CONNECTED","Offline wired import is available. No Wi-Fi or IP address is required for the camera link.",green);'''
if needle not in s: raise SystemExit('0.7.0 health target missing')
s=s.replace(needle,replacement,1)

# Connection-method selector at the top of Settings. Existing setup cards remain.
needle='''        sectionHeader("Camera Connection");LinearLayout camera=cardBox();'''
replacement='''        sectionHeader("Connection Methods");
        LinearLayout methods=cardBox();
        String currentConnection=ConnectionStatus.cameraConnection(p);
        methods.addView(statusRow("Current connection",currentConnection,currentConnection.startsWith("Waiting")?muted:green));
        methods.addView(statusRow("IP address",ConnectionStatus.ipv4(this,p),muted));
        methods.addView(statusRow("Network",ConnectionStatus.networkSummary(this),muted));
        Button usbMethod=smallButton("USB-C WIRED",green);usbMethod.setOnClickListener(v->startActivity(new Intent(this,UsbCameraActivity.class)));methods.addView(usbMethod);
        Button wifiMethod=smallButton("WI-FI / HOTSPOT",blue);wifiMethod.setOnClickListener(v->MultiCameraSupport.showSetup(this,p));methods.addView(wifiMethod);
        Button wirelessMethod=smallButton("BLUETOOTH / WIRELESS SETUP",Color.rgb(70,80,90));wirelessMethod.setOnClickListener(v->{if("Nikon".equals(cp.brand))startActivity(new Intent(this,NikonBluetoothActivity.class));else toast("Bluetooth wireless setup is currently implemented for Nikon. USB-C and FTP remain available for this camera.");});methods.addView(wirelessMethod);
        body.addView(methods);

        sectionHeader("Camera Connection");LinearLayout camera=cardBox();'''
if needle not in s: raise SystemExit('0.7.0 settings connection target missing')
s=s.replace(needle,replacement,1)

s=s.replace('Nikon Auto Upload 0.6.2','Nikon Auto Upload 0.7.0')
p.write_text(s)

# ----- DirectTransferService -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s=p.read_text()

# Make imported-file diagnostics source aware (USB-C vs Bluetooth/Wi-Fi).
old='''            String path=intent.getStringExtra("file_path"),name=intent.getStringExtra("file_name");
            try{
                File imported=path==null?null:new File(path);
                if(imported!=null&&imported.exists()&&imported.isFile()){
                    if(name==null||name.trim().isEmpty())name=imported.getName();
                    addEvent("Bluetooth/Wi-Fi handoff received: "+name);
                    onPhotoReceived(imported,name);
                }else addEvent("Bluetooth/Wi-Fi handoff file missing");
            }catch(Exception e){addEvent("Bluetooth/Wi-Fi handoff import error: "+shortMessage(e));}'''
new='''            String path=intent.getStringExtra("file_path"),name=intent.getStringExtra("file_name"),source=intent.getStringExtra("source_label");
            if(source==null||source.trim().isEmpty())source="Bluetooth/Wi-Fi";
            final String sourceLabel=source;
            try{
                File imported=path==null?null:new File(path);
                if(imported!=null&&imported.exists()&&imported.isFile()){
                    if(name==null||name.trim().isEmpty())name=imported.getName();
                    addEvent(sourceLabel+" handoff received: "+name);
                    onPhotoReceived(imported,name);
                }else addEvent(sourceLabel+" handoff file missing");
            }catch(Exception e){addEvent(sourceLabel+" handoff import error: "+shortMessage(e));}'''
if old not in s: raise SystemExit('0.7.0 import source-label target missing')
s=s.replace(old,new,1)

# RAW files are imported at full resolution but are not endlessly retried to
# Flickr, whose workflow in this app is JPEG-based.
s=s.replace('boolean send=flickrEnabled();String uriText=uri.toString();',
            'boolean send=flickrEnabled()&&isFlickrUploadable(originalName);String uriText=uri.toString();',1)

old='''    private Uri saveToGallery(File file,String name) throws Exception {
        String low=name.toLowerCase(Locale.US),mime=(low.endsWith(".jpeg")||low.endsWith(".jpg"))?"image/jpeg":"application/octet-stream";'''
new='''    private boolean isFlickrUploadable(String name){String x=name==null?"":name.toLowerCase(Locale.US);return x.endsWith(".jpg")||x.endsWith(".jpeg");}
    private String mimeForName(String name){
        String x=name==null?"":name.toLowerCase(Locale.US);
        if(x.endsWith(".jpg")||x.endsWith(".jpeg"))return "image/jpeg";
        if(x.endsWith(".nef")||x.endsWith(".nrw"))return "image/x-nikon-nef";
        if(x.endsWith(".arw"))return "image/x-sony-arw";
        if(x.endsWith(".cr2"))return "image/x-canon-cr2";
        if(x.endsWith(".cr3"))return "image/x-canon-cr3";
        if(x.endsWith(".raf"))return "image/x-fuji-raf";
        if(x.endsWith(".dng"))return "image/x-adobe-dng";
        if(x.endsWith(".rw2"))return "image/x-panasonic-rw2";
        if(x.endsWith(".orf"))return "image/x-olympus-orf";
        if(x.endsWith(".tif")||x.endsWith(".tiff"))return "image/tiff";
        if(x.endsWith(".png"))return "image/png";
        return "application/octet-stream";
    }

    private Uri saveToGallery(File file,String name) throws Exception {
        String low=name.toLowerCase(Locale.US),mime=mimeForName(name);'''
if old not in s: raise SystemExit('0.7.0 MIME target missing')
s=s.replace(old,new,1)
p.write_text(s)

# ----- USB activity small hardening -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/UsbCameraActivity.java')
s=p.read_text()
s=s.replace('int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;',
'''int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE; else flags|=PendingIntent.FLAG_IMMUTABLE;''',1)
s=s.replace('String name=cameraName(d);p.edit().putString("usb_state","Connected")',
            'String name=cameraName(d);p.edit().putString("usb_state","USB-C connected")',1)
s=s.replace('p.edit().putString("usb_state",message==null?"Disconnected":message).apply();',
            'p.edit().putString("usb_state",message==null?"Disconnected":message).apply();',1)
p.write_text(s)

# ----- Manifest -----
p=Path('nikon-auto-upload/app/src/main/AndroidManifest.xml')
s=p.read_text()
if 'android.hardware.usb.host' not in s:
    s=s.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-feature android:name="android.hardware.usb.host" android:required="false"/>',1)
usb_activity='''        <activity android:name=".UsbCameraActivity" android:exported="true" android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
            <meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" android:resource="@xml/camera_usb_filter"/>
        </activity>
'''
if '.UsbCameraActivity' not in s:
    marker='        <activity android:name=".MainActivity" android:exported="true">\n'
    if marker not in s: raise SystemExit('0.7.0 manifest MainActivity target missing')
    s=s.replace(marker,usb_activity+marker,1)
p.write_text(s)

# ----- Help -----
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.6.2 beta','Version 0.7.0 beta')
    s += '''\n\n0.7.0 USB-C WIRED CAMERA IMPORT\n--------------------------------\nUSB-C Wired is a separate camera-import module. It uses Android USB Host/OTG and standard USB Still Image/PTP transport. It does not replace or modify the Wi-Fi/FTP receiver or Nikon Bluetooth handoff.\n\n1. Use a data-capable USB-C cable.\n2. Put the camera in MTP/PTP / USB data mode if the camera offers a USB mode choice.\n3. Open Settings > Connection Methods > USB-C WIRED, or plug in a compatible PTP camera and choose Nikon Auto Upload when Android asks.\n4. Grant Android USB permission.\n5. Select one or more photos, IMPORT NEW, or enable automatic import of newly-shot photos.\n6. Full-resolution JPEG and supported RAW files are copied without deleting the camera originals. RAW files are retained on the phone; JPEGs continue through the existing Flickr queue when Flickr upload is enabled.\n7. Delete from camera requires the separate DELETE SELECTED FROM CAMERA button and an explicit confirmation.\n\nThe Home/Settings status now shows connection type, phone IP address where applicable, active network(s), and Nikon camera IP when the Bluetooth/Wi-Fi PTP handoff has one. USB-C does not need an IP address.\n'''
    p.write_text(s)
