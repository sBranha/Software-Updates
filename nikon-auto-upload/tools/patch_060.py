from pathlib import Path

# ----- MainActivity: Nikon Bluetooth test entry point -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s = p.read_text()
marker = '        body.addView(camChoice);\n\n'
insert = '''        body.addView(camChoice);

        if("Nikon".equals(cp.brand)){
            sectionHeader("Nikon Bluetooth — Experimental");
            LinearLayout btCard=cardBox();
            String btState=p.getString("nikon_bt_state","Not paired");
            int btColor=btState.toLowerCase(Locale.US).contains("connected")||btState.toLowerCase(Locale.US).contains("complete")?green:(btState.toLowerCase(Locale.US).contains("pair")?amber:muted);
            btCard.addView(statusRow("Smart-device Bluetooth",btState,btColor));
            String btName=p.getString("nikon_bt_camera_name","");
            if(!btName.isEmpty())btCard.addView(statusRow("Saved camera",btName,white));
            String serial=p.getString("nikon_bt_camera_serial","");
            if(!serial.isEmpty())btCard.addView(statusRow("Camera serial",serial,muted));
            btCard.addView(txt("Bluetooth is for Nikon pairing/reconnect and future automatic handoff. Full-resolution photo files still use Wi-Fi/FTP.",12,muted,false));
            Button btSetup=big("PAIR / CONNECT NIKON BLUETOOTH",blue);
            btSetup.setOnClickListener(v->startActivity(new Intent(this,NikonBluetoothActivity.class)));
            btCard.addView(btSetup);
            body.addView(btCard);
        }

'''
if marker not in s:
    raise SystemExit('0.6.0 could not locate camera card insertion point')
s=s.replace(marker,insert,1)
s=s.replace('Nikon Auto Upload 0.5.3','Nikon Auto Upload 0.6.0')
s=s.replace('Nikon Auto Upload 0.5.2','Nikon Auto Upload 0.6.0')
s=s.replace('Nikon Auto Upload 0.5.1','Nikon Auto Upload 0.6.0')
p.write_text(s)

# ----- Manifest: Android Bluetooth permissions + pairing activity -----
p = Path('nikon-auto-upload/app/src/main/AndroidManifest.xml')
s = p.read_text()
perm_marker='    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>\n'
perms='''    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"/>
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
'''
if perm_marker not in s:
    raise SystemExit('0.6.0 could not locate notification permission')
s=s.replace(perm_marker,perms,1)
activity_marker='        <activity android:name=".MainActivity" android:exported="true">\n'
activity='''        <activity android:name=".NikonBluetoothActivity" android:exported="false"/>
        <activity android:name=".MainActivity" android:exported="true">
'''
if activity_marker not in s:
    raise SystemExit('0.6.0 could not locate MainActivity manifest entry')
s=s.replace(activity_marker,activity,1)
p.write_text(s)

# ----- Help: explain Bluetooth scope -----
p = Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text()
    s=s.replace('Version 0.5.3 beta','Version 0.6.0 beta')
    s += '''\n\nNIKON BLUETOOTH (EXPERIMENTAL)\n------------------------------\nFor Nikon cameras that support SnapBridge smart-device Bluetooth, Settings now includes PAIR / CONNECT NIKON BLUETOOTH.\n\nCamera: Network menu > Connect to smart device > Pairing (Bluetooth) > Start pairing.\nApp: Settings > Nikon Bluetooth > PAIR / CONNECT NIKON BLUETOOTH > SCAN FOR NIKON CAMERA.\n\nThe app performs the Nikon smart-device BLE handshake and then asks Android to complete the Bluetooth Classic bond/passkey step. After pairing, use CONNECT SAVED NIKON CAMERA for reconnect tests.\n\nImportant: Bluetooth does not replace full-resolution FTP transfer in this build. Full-size JPEG transfer still uses the Wi-Fi/FTP receiver.\n'''
    p.write_text(s)
