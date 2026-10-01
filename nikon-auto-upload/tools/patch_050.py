from pathlib import Path

# ----- MainActivity -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s = p.read_text()

s = s.replace('        base("Nikon Auto Upload","Home");\n',
'''        base("Nikon Auto Upload","Home");
        MultiCameraSupport.ensureDefaults(p);
        MultiCameraSupport.Profile cp=MultiCameraSupport.selected(p);
''',1)

s = s.replace(
'ImageView camera=new ImageView(this);camera.setImageResource(R.drawable.z8_camera);camera.setScaleType(ImageView.ScaleType.FIT_CENTER);camera.setAdjustViewBounds(true);hero.addView(camera,new LinearLayout.LayoutParams(-1,dp(205)));',
'View camera=MultiCameraSupport.artwork(this,p);hero.addView(camera,new LinearLayout.LayoutParams(-1,dp(205)));')
s = s.replace('TextView z8=txt("Nikon Z8",24,white,true);','TextView z8=txt(cp.displayName(),24,white,true);')
s = s.replace('(cameraConnected?"●  Connected by Wi-Fi FTP • Full resolution":"●  Waiting for camera")',
              '(cameraConnected?"●  "+cp.displayName()+" connected":"●  Waiting for "+cp.model)')
s = s.replace('chip("Wi-Fi FTP",cameraConnected?green:muted)',
              'chip(MultiCameraSupport.networkLabel(p),cameraConnected?green:muted)')
s = s.replace('return new Health("READY — WAITING FOR Z8","Turn on your Z8 WLAN2 FTP profile. No outside Wi-Fi is required.",green);',
              'return new Health("READY — WAITING FOR CAMERA",MultiCameraSupport.waitingText(p),green);')

# Add camera selection/setup at the top of Settings.
needle='''    void drawSettings(){
        base("Settings","Settings");
        sectionHeader("Camera Connection");'''
replacement='''    void drawSettings(){
        base("Settings","Settings");
        MultiCameraSupport.ensureDefaults(p);
        MultiCameraSupport.Profile cp=MultiCameraSupport.selected(p);
        sectionHeader("Camera");
        LinearLayout camChoice=cardBox();
        camChoice.addView(statusRow("Selected camera",cp.displayName(),green));
        camChoice.addView(txt(cp.mode.equals(MultiCameraSupport.MODE_CAMERA_AP)?"Camera creates the Wi-Fi network":"Phone hotspot supplies the camera network",12,muted,false));
        Button chooseCamera=smallButton("CHOOSE CAMERA MODEL",blue);chooseCamera.setOnClickListener(v->MultiCameraSupport.showSelector(this,p,this::drawSettings));camChoice.addView(chooseCamera);
        Button setupCamera=smallButton("SHOW SETUP FOR "+cp.model.toUpperCase(Locale.US),green);setupCamera.setOnClickListener(v->MultiCameraSupport.showSetup(this,p));camChoice.addView(setupCamera);
        body.addView(camChoice);

        sectionHeader("Camera Connection");'''
if needle not in s:
    raise SystemExit('0.5.0 could not locate drawSettings header')
s=s.replace(needle,replacement,1)

s=s.replace('statusRow("Wi-Fi FTP",isCameraConnected()?"Nikon Z8 Connected":"Waiting for Nikon Z8",isCameraConnected()?green:muted)',
            'statusRow(MultiCameraSupport.networkLabel(p),isCameraConnected()?cp.displayName()+" Connected":"Waiting for "+cp.displayName(),isCameraConnected()?green:muted)')
s=s.replace('"START / RESTART Z8 RECEIVER"','"START / RESTART CAMERA RECEIVER"')
s=s.replace('Nikon Auto Upload 0.4.1  •  Original mock-up match  •  Wi-Fi FTP only — no Bluetooth',
            'Nikon Auto Upload 0.5.0  •  Nikon + Canon + Sony FTP beta')
s=s.replace('Nikon Auto Upload 0.4.1\\nGenerated:', 'Nikon Auto Upload 0.5.0\\nGenerated:')

# Make default tags camera-neutral for new installs; existing user tag preference remains untouched.
s=s.replace('p.getString("tags","nikon z8")','p.getString("tags","camera photography")')

p.write_text(s)

# ----- DirectTransferService -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s = p.read_text()
s=s.replace('super.onCreate();p=getSharedPreferences("settings",MODE_PRIVATE);migrateStableSettings();createChannel();',
            'super.onCreate();p=getSharedPreferences("settings",MODE_PRIVATE);migrateStableSettings();MultiCameraSupport.ensureDefaults(p);createChannel();')
s=s.replace('"Waiting for Z8"','"Waiting for camera"')
s=s.replace('"Z8 direct receiver is starting"','"Camera FTP receiver is starting"')
s=s.replace('"FTP receiver ready — waiting for Nikon Z8"','"FTP receiver ready — waiting for "+MultiCameraSupport.cameraName(p)')
s=s.replace('"Z8 receiving paused — Flickr retries can continue"','"Camera receiving paused — Flickr retries can continue"')
s=s.replace('"Z8 receiving resumed"','"Camera receiving resumed"')
s=s.replace('"Cellular Flickr path ready — Z8 Wi-Fi can stay connected"','"Cellular Flickr path ready — camera network can stay connected"')
s=s.replace('"Z8 receiving is paused"','"Camera receiving is paused"')
s=s.replace('"Duplicate Z8 photo ignored: "+originalName','"Duplicate camera photo ignored: "+originalName')
s=s.replace('"Received from Z8: "+originalName','"Received from "+MultiCameraSupport.cameraName(p)+": "+originalName')
s=s.replace('notifyEvent("Photo received from Nikon Z8",originalName);','notifyEvent("Photo received from "+MultiCameraSupport.cameraName(p),originalName);')
s=s.replace('putString("ftp_state","Z8 connected")','putString("ftp_state",MultiCameraSupport.cameraName(p)+" connected")')
s=s.replace('addEvent("Z8 connected to FTP receiver")','addEvent(MultiCameraSupport.cameraName(p)+" connected to FTP receiver")')
s=s.replace('putString("ftp_state","Z8 logged in")','putString("ftp_state",MultiCameraSupport.cameraName(p)+" logged in")')
s=s.replace('addEvent("Z8 FTP login accepted")','addEvent(MultiCameraSupport.cameraName(p)+" FTP login accepted")')
# Hotspot interfaces are essential for Canon/Sony mode.
old='''    private String findBestLocalIp(){try{Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();while(en.hasMoreElements()){NetworkInterface ni=en.nextElement();String nn=ni.getName().toLowerCase(Locale.US);if(!(nn.contains("wlan")||nn.contains("wifi")))continue;Enumeration<InetAddress> aa=ni.getInetAddresses();while(aa.hasMoreElements()){InetAddress a=aa.nextElement();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())return a.getHostAddress();}}}catch(Exception ignored){}return "waiting for Z8 Wi-Fi";}'''
if old in s:
    s=s.replace(old,'    private String findBestLocalIp(){return MultiCameraSupport.findBestLocalIp();}')
else:
    # Keep build working if formatting changes later.
    import re
    s=re.sub(r'    private String findBestLocalIp\(\)\{.*?\n    private synchronized void addEvent',
             '    private String findBestLocalIp(){return MultiCameraSupport.findBestLocalIp();}\n    private synchronized void addEvent',s,flags=re.S)
p.write_text(s)

# ----- SimpleFtpServer -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/SimpleFtpServer.java')
s = p.read_text()
s=s.replace('/** FTP receiver tuned for Nikon Z8 direct Wi-Fi transfers. */','/** Standards-based FTP receiver for supported Nikon, Canon, and Sony cameras. */')
s=s.replace('NikonFTP-Accept','CameraFTP-Accept')
s=s.replace('Nikon Auto Upload FTP ready','Camera Auto Upload FTP ready')
s=s.replace('Z8 TCP','Camera TCP').replace('Z8 FTP','Camera FTP').replace('Z8 passive','Camera passive').replace('Z8 active','Camera active').replace('Receiving from Z8','Receiving from camera').replace('Z8 transfer complete','Camera transfer complete')
s=s.replace('No Nikon passive port available','No passive FTP port available')
p.write_text(s)
