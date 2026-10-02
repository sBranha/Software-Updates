from pathlib import Path

# ----- MainActivity: app-managed LocalOnlyHotspot controls -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s = p.read_text()

marker = '        sectionHeader("FTP Login");\n'
insert = '''        if(MultiCameraSupport.usesPhoneHotspot(p)){
            CameraHotspotManager.updateAddress(p);
            sectionHeader("Camera Hotspot");
            LinearLayout hotspot=cardBox();
            hotspot.addView(txt("Use this app-created local camera network instead of Samsung Mobile Hotspot. It is designed so the camera can reach the FTP receiver on this phone while Flickr keeps using cellular data.",12,muted,false));
            boolean hs=CameraHotspotManager.isRunning()||p.getBoolean("camera_hotspot_running",false);
            String ssid=p.getString("camera_hotspot_ssid","");
            String hp=p.getString("camera_hotspot_password","");
            String hip=p.getString("camera_hotspot_ip","waiting for hotspot address");
            hotspot.addView(statusRow("Camera hotspot",hs?"RUNNING":"OFF",hs?green:muted));
            if(hs){
                hotspot.addView(statusRow("Wi-Fi name (SSID)",ssid.isEmpty()?"Starting...":ssid,white));
                hotspot.addView(statusRow("Wi-Fi password",hp.isEmpty()?"Starting...":hp,white));
                hotspot.addView(statusRow("PHONE FTP ADDRESS",hip+":2121",blue));
                String allIps=p.getString("camera_hotspot_all_ips","");if(!allIps.isEmpty())hotspot.addView(txt("Active local IPv4: "+allIps,11,muted,false));
                Button refreshIp=smallButton("REFRESH HOTSPOT FTP ADDRESS",blue);refreshIp.setOnClickListener(v->{CameraHotspotManager.updateAddress(p);drawSettings();});hotspot.addView(refreshIp);
                Button stopHs=smallButton("STOP CAMERA HOTSPOT",red);stopHs.setOnClickListener(v->CameraHotspotManager.stop(p,msg->{toast(msg);drawSettings();}));hotspot.addView(stopHs);
            }else{
                Button startHs=big("START CAMERA HOTSPOT",green);startHs.setOnClickListener(v->CameraHotspotManager.start(this,p,msg->{toast(msg);if(CameraHotspotManager.isRunning())startDirect(false);drawSettings();}));hotspot.addView(startHs);
                hotspot.addView(txt("Important: turn OFF Samsung/Android Mobile Hotspot before starting this. Android will create a separate local-only Wi-Fi network and show its name/password here.",11,amber,false));
            }
            body.addView(hotspot);
        }

        sectionHeader("FTP Login");
'''
if marker not in s:
    raise SystemExit('0.5.3 could not locate FTP Login section')
s=s.replace(marker,insert,1)

s=s.replace('Nikon Auto Upload 0.5.2','Nikon Auto Upload 0.5.3')
p.write_text(s)

# ----- MultiCameraSupport: direct users to the app-managed hotspot -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MultiCameraSupport.java')
s = p.read_text()
s=s.replace('Turn on Android Mobile Hotspot and connect the camera to that hotspot.',
            'In Nikon Auto Upload Settings, tap START CAMERA HOTSPOT. Connect the camera to the Wi-Fi name and password shown by the app.')
s=s.replace('Turn on Android Mobile Hotspot and connect the transmitter/camera to it.',
            'In Nikon Auto Upload Settings, tap START CAMERA HOTSPOT. Connect the transmitter/camera to the Wi-Fi name and password shown by the app.')
s=s.replace('Turn on Android Mobile Hotspot and connect the camera to that hotspot using Network → Wi-Fi / Access Point Set.',
            'In Nikon Auto Upload Settings, tap START CAMERA HOTSPOT. Connect the camera to that Wi-Fi using Network → Wi-Fi / Access Point Set.')
s=s.replace('Turn on Android Mobile Hotspot and connect the camera to it.',
            'In Nikon Auto Upload Settings, tap START CAMERA HOTSPOT and connect the camera to the Wi-Fi name/password shown by the app.')
s=s.replace('Turn on Android Mobile Hotspot and connect the file transmitter to it.',
            'In Nikon Auto Upload Settings, tap START CAMERA HOTSPOT and connect the file transmitter to the Wi-Fi name/password shown by the app.')
s=s.replace('Turn on Android Mobile Hotspot and connect the '+ '"+x.model+"' +' to it.',
            'Start the Camera Hotspot inside this app, then connect the '+ '"+x.model+"' +' to the Wi-Fi name/password shown here.')
p.write_text(s)

# ----- In-app help wording -----
p = Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.5.2 beta','Version 0.5.3 beta')
    s=s.replace('Android Settings > Mobile Hotspot > ON.', 'Nikon Auto Upload > Settings > START CAMERA HOTSPOT.')
    s=s.replace('Turn on Android Mobile Hotspot.', 'Open Nikon Auto Upload > Settings and tap START CAMERA HOTSPOT.')
    s=s.replace('Turn on Android Mobile Hotspot and connect', 'Start CAMERA HOTSPOT inside Nikon Auto Upload and connect')
    s=s.replace('Android Mobile Hotspot = ON.', 'Nikon Auto Upload Camera Hotspot = ON.')
    p.write_text(s)
