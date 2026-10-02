from pathlib import Path

p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/NikonBluetoothActivity.java')
s=p.read_text()

s=s.replace(
'    private static final UUID ID_UUID      = UUID.fromString("00002002-3dd4-4255-8d62-6dc7b9bd5561");\n',
'''    private static final UUID ID_UUID      = UUID.fromString("00002002-3dd4-4255-8d62-6dc7b9bd5561");
    private static final UUID WIFI_CONFIG_UUID = UUID.fromString("00002004-3dd4-4255-8d62-6dc7b9bd5561");
    private static final UUID WIFI_ESTABLISH_UUID = UUID.fromString("00002005-3dd4-4255-8d62-6dc7b9bd5561");
''',1)
s=s.replace(
'    private BluetoothGattCharacteristic pairChr, not1Chr, idChr;\n',
'''    private BluetoothGattCharacteristic pairChr, not1Chr, idChr, wifiConfigChr, wifiEstablishChr;
    private NikonPtpAutoTransfer autoTransfer;
''',1)

needle='''        p=getSharedPreferences("settings",MODE_PRIVATE);
        BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);'''
replacement='''        p=getSharedPreferences("settings",MODE_PRIVATE);
        if(!p.contains("nikon_auto_handoff_enabled"))p.edit().putBoolean("nikon_auto_handoff_enabled",true).apply();
        autoTransfer=new NikonPtpAutoTransfer(this,p,new NikonPtpAutoTransfer.Listener(){
            @Override public void onLog(String message){append("[PHOTO] "+message);}
            @Override public void onState(String message,boolean error){setStatus(message,error?Color.RED:Color.rgb(55,206,108));}
            @Override public void onWifiReady(android.net.Network network,String cameraIp){
                runOnUiThread(()->{
                    append("[PHOTO] Nikon camera Wi-Fi is available.");
                    if(wifiEstablishChr!=null){
                        write(wifiEstablishChr,new byte[]{0x01});
                        append("[PHOTO] Sent Nikon Wi-Fi establishment command (2005 = 01).");
                    }else append("[PHOTO] Nikon characteristic 2005 is not available on this camera.");
                    handler.postDelayed(()->{if(autoTransfer!=null)autoTransfer.startPtp(network,cameraIp);},900);
                });
            }
        });
        BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);'''
if needle not in s: raise SystemExit('0.6.1 onCreate target missing')
s=s.replace(needle,replacement,1)

s=s.replace(
'''        closeGatt();
        try { unregisterReceiver(btReceiver); } catch(Exception ignored){}''',
'''        if(autoTransfer!=null)autoTransfer.stop();
        closeGatt();
        try { unregisterReceiver(btReceiver); } catch(Exception ignored){}''',1)

s=s.replace(
'''        info.addView(text("This pairs/reconnects Nikon smart-device Bluetooth. Full-resolution photos still use the app's Wi-Fi/FTP receiver. It does not require SnapBridge to be running.",13,muted,false));''',
'''        info.addView(text("Bluetooth stays connected for control. When Auto Photo Handoff is ON, the app reads the Nikon Wi-Fi credentials over Bluetooth, joins the camera Wi-Fi, opens PTP/IP, and pulls each NEW full-resolution JPEG to the phone. Flickr can keep using cellular.",13,muted,false));''',1)

status_marker='''        status=text(p.getString("nikon_bt_state","Not paired"),15,muted,true);status.setPadding(dp(12),dp(12),dp(12),dp(12));status.setBackgroundColor(panel);body.addView(status,new LinearLayout.LayoutParams(-1,dp(52)));

        Button scan=button("SCAN FOR NIKON CAMERA",green);'''
status_replacement='''        status=text(p.getString("nikon_bt_state","Not paired"),15,muted,true);status.setPadding(dp(12),dp(12),dp(12),dp(12));status.setBackgroundColor(panel);body.addView(status,new LinearLayout.LayoutParams(-1,dp(52)));

        Switch autoHandoff=new Switch(this);autoHandoff.setText("Automatic full-resolution photo handoff");autoHandoff.setTextColor(white);autoHandoff.setChecked(p.getBoolean("nikon_auto_handoff_enabled",true));autoHandoff.setPadding(dp(8),dp(6),dp(8),dp(6));
        autoHandoff.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean("nikon_auto_handoff_enabled",on).apply();append("[PHOTO] Auto handoff "+(on?"ON":"OFF"));if(!on&&autoTransfer!=null)autoTransfer.stop();else if(on&&p.getString("nikon_bt_state","").toLowerCase(Locale.US).contains("connected"))requestWifiConfiguration();});
        body.addView(autoHandoff,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView handoffState=text("Photo handoff: "+p.getString("nikon_handoff_state","waiting for Bluetooth"),12,muted,false);handoffState.setPadding(dp(8),dp(2),dp(8),dp(6));body.addView(handoffState);
        Button handoff=button("START / RETRY PHOTO HANDOFF",amber);handoff.setOnClickListener(v->requestWifiConfiguration());body.addView(handoff);

        Button scan=button("SCAN FOR NIKON CAMERA",green);'''
if status_marker not in s: raise SystemExit('0.6.1 status UI target missing')
s=s.replace(status_marker,status_replacement,1)

old='''            pairChr=svc.getCharacteristic(PAIR_UUID);not1Chr=svc.getCharacteristic(NOT1_UUID);idChr=svc.getCharacteristic(ID_UUID);
            if(pairChr==null||not1Chr==null||idChr==null){runOnUiThread(()->setStatus("Required Nikon Bluetooth characteristics are missing",Color.RED));return;}
            runOnUiThread(()->append("Nikon smart-device service discovered"));descriptorStage=0;enablePairIndications(g);'''
new='''            pairChr=svc.getCharacteristic(PAIR_UUID);not1Chr=svc.getCharacteristic(NOT1_UUID);idChr=svc.getCharacteristic(ID_UUID);
            wifiConfigChr=svc.getCharacteristic(WIFI_CONFIG_UUID);wifiEstablishChr=svc.getCharacteristic(WIFI_ESTABLISH_UUID);
            if(pairChr==null||not1Chr==null||idChr==null){runOnUiThread(()->setStatus("Required Nikon Bluetooth characteristics are missing",Color.RED));return;}
            runOnUiThread(()->{append("Nikon smart-device service discovered");append("[PHOTO] Wi-Fi config 2004: "+(wifiConfigChr==null?"MISSING":"found")+" • establish 2005: "+(wifiEstablishChr==null?"MISSING":"found"));});descriptorStage=0;enablePairIndications(g);'''
if old not in s: raise SystemExit('0.6.1 service-discovery target missing')
s=s.replace(old,new,1)

marker='''        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){handleChanged(c,c.getValue());}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int statusCode){'''
insert='''        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){handleChanged(c,c.getValue());}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int statusCode){handleCharacteristicRead(c,c.getValue(),statusCode);}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int statusCode){handleCharacteristicRead(c,value,statusCode);}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int statusCode){'''
if marker not in s: raise SystemExit('0.6.1 callback target missing')
s=s.replace(marker,insert,1)

old='''        if(c.getUuid().equals(NOT1_UUID)){
            if(data.length>=2&&data[0]==1&&data[1]==0){gotSuccess=true;runOnUiThread(()->append("Nikon NOT1 success received"));}
            return;
        }'''
new='''        if(c.getUuid().equals(NOT1_UUID)){
            if(data.length>=2&&data[0]==1&&data[1]==0){gotSuccess=true;runOnUiThread(()->append("Nikon NOT1 success received"));}
            else if(autoTransfer!=null&&autoTransfer.isRunning()){runOnUiThread(()->append("[PHOTO] Nikon 2008 event: "+hex(data)));autoTransfer.kickPoll();}
            return;
        }'''
if old not in s: raise SystemExit('0.6.1 NOT1 target missing')
s=s.replace(old,new,1)

old='''        if(reconnectMode&&hasClassicBondForCamera()){
            p.edit().putString("nikon_bt_state","Bluetooth connected").apply();setStatus("Nikon Bluetooth connected",Color.rgb(55,206,108));append("Saved Nikon Bluetooth connection is ready.");return;
        }'''
new='''        if(reconnectMode&&hasClassicBondForCamera()){
            p.edit().putString("nikon_bt_state","Bluetooth connected").apply();setStatus("Nikon Bluetooth connected",Color.rgb(55,206,108));append("Saved Nikon Bluetooth connection is ready.");
            if(p.getBoolean("nikon_auto_handoff_enabled",true))handler.postDelayed(this::requestWifiConfiguration,800);
            return;
        }'''
if old not in s: raise SystemExit('0.6.1 reconnect target missing')
s=s.replace(old,new,1)

marker='''    private void beginClassicBonding(){'''
helpers='''    private void requestWifiConfiguration(){
        if(!p.getBoolean("nikon_auto_handoff_enabled",true)){append("[PHOTO] Auto handoff is OFF.");return;}
        if(gatt==null){append("[PHOTO] Bluetooth must be connected first. Tap CONNECT SAVED NIKON CAMERA.");setStatus("Connect Nikon Bluetooth first",Color.rgb(255,193,7));return;}
        if(wifiConfigChr==null){append("[PHOTO] Nikon Wi-Fi configuration characteristic 2004 is missing.");setStatus("Camera does not expose Nikon Wi-Fi handoff characteristic 2004",Color.RED);return;}
        try{
            append("[PHOTO] Reading Nikon Wi-Fi configuration (2004)…");
            setStatus("Asking Nikon for its Wi-Fi network…",Color.rgb(33,150,243));
            if(!gatt.readCharacteristic(wifiConfigChr)){append("[PHOTO] Android could not start characteristic 2004 read.");setStatus("Could not request Nikon Wi-Fi credentials",Color.RED);}
        }catch(Exception e){append("[PHOTO] Wi-Fi credential read error: "+e.getMessage());setStatus("Wi-Fi credential read failed",Color.RED);}
    }

    private void handleCharacteristicRead(BluetoothGattCharacteristic c,byte[] data,int statusCode){
        if(c==null||!c.getUuid().equals(WIFI_CONFIG_UUID))return;
        if(statusCode!=BluetoothGatt.GATT_SUCCESS){runOnUiThread(()->{append("[PHOTO] Nikon 2004 read failed: status="+statusCode);setStatus("Nikon Wi-Fi credential read failed",Color.RED);});return;}
        if(data==null||data.length<102){int n=data==null?0:data.length;runOnUiThread(()->{append("[PHOTO] Nikon 2004 payload too short: "+n+" bytes");setStatus("Nikon Wi-Fi data format was unexpected",Color.RED);});return;}
        String ssid=asciiZ(data,1,32),password=asciiZ(data,33,64);
        int security=data[97]&255;
        String ip=(data[98]&255)+"."+(data[99]&255)+"."+(data[100]&255)+"."+(data[101]&255);
        p.edit().putString("nikon_handoff_ssid",ssid).putString("nikon_handoff_ip_hint",ip).putInt("nikon_handoff_security",security).apply();
        runOnUiThread(()->{
            append("[PHOTO] Nikon Wi-Fi credentials received: SSID="+ssid+" • security="+security+" • camera IP hint="+ip+" • password="+(password.isEmpty()?"none":"••••••••"));
            if(autoTransfer!=null)autoTransfer.startWifi(ssid,password,ip);
        });
    }

    private String asciiZ(byte[] data,int start,int len){
        int end=Math.min(data.length,start+len),n=start;while(n<end&&data[n]!=0)n++;
        return new String(data,start,Math.max(0,n-start),java.nio.charset.StandardCharsets.US_ASCII).trim();
    }
    private String hex(byte[] b){StringBuilder s=new StringBuilder();for(int i=0;i<Math.min(b.length,32);i++)s.append(String.format(Locale.US,"%02X",b[i]&255)).append(i+1<Math.min(b.length,32)?" ":"");return s.toString();}

    private void beginClassicBonding(){'''
if marker not in s: raise SystemExit('0.6.1 beginClassic target missing')
s=s.replace(marker,helpers,1)

s=s.replace('gatt=null;pairChr=null;not1Chr=null;idChr=null;}',
            'gatt=null;pairChr=null;not1Chr=null;idChr=null;wifiConfigChr=null;wifiEstablishChr=null;}',1)

old='''    private void requestBluetoothPermissions(){ArrayList<String> req=new ArrayList<>();if(Build.VERSION.SDK_INT>=31){if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.BLUETOOTH_SCAN);if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.BLUETOOTH_CONNECT);}else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACCESS_FINE_LOCATION);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),80);}'''
new='''    private void requestBluetoothPermissions(){ArrayList<String> req=new ArrayList<>();if(Build.VERSION.SDK_INT>=31){if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.BLUETOOTH_SCAN);if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.BLUETOOTH_CONNECT);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.NEARBY_WIFI_DEVICES);}else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACCESS_FINE_LOCATION);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),80);}'''
if old not in s: raise SystemExit('0.6.1 permission target missing')
s=s.replace(old,new,1)

p.write_text(s)

p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s=p.read_text()
needle='''        String cmd=intent==null?null:intent.getStringExtra("command");
        if("pause_receiver".equals(cmd)){pauseReceiver();return START_STICKY;}'''
replacement='''        String cmd=intent==null?null:intent.getStringExtra("command");
        if("import_file".equals(cmd)){
            String path=intent.getStringExtra("file_path"),name=intent.getStringExtra("file_name");
            try{
                File imported=path==null?null:new File(path);
                if(imported!=null&&imported.exists()&&imported.isFile()){
                    if(name==null||name.trim().isEmpty())name=imported.getName();
                    addEvent("Bluetooth/Wi-Fi handoff received: "+name);
                    onPhotoReceived(imported,name);
                }else addEvent("Bluetooth/Wi-Fi handoff file missing");
            }catch(Exception e){addEvent("Bluetooth/Wi-Fi handoff import error: "+shortMessage(e));}
            return START_STICKY;
        }
        if("pause_receiver".equals(cmd)){pauseReceiver();return START_STICKY;}'''
if needle not in s: raise SystemExit('0.6.1 DirectTransfer import target missing')
s=s.replace(needle,replacement,1)
p.write_text(s)

p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text()
s=s.replace('Bluetooth is for Nikon pairing/reconnect and future automatic handoff. Full-resolution photo files still use Wi-Fi/FTP.',
            'Bluetooth can now trigger an experimental automatic Nikon Wi-Fi/PTP handoff for NEW full-resolution JPEGs. Pair once, then use the Bluetooth screen to test the complete handoff.')
s=s.replace('Nikon Auto Upload 0.6.0','Nikon Auto Upload 0.6.1')
p.write_text(s)

p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.6.0 beta','Version 0.6.1 beta')
    old_help='Important: Bluetooth does not replace full-resolution FTP transfer in this build. Full-size JPEG transfer still uses the Wi-Fi/FTP receiver.'
    new_help='''0.6.1 PHOTO HANDOFF TEST:
After Nikon Bluetooth says connected, leave Automatic full-resolution photo handoff ON. The app reads Nikon characteristic 2004 for the camera Wi-Fi network, asks Android to join it, sends Nikon establishment command 2005, opens PTP/IP on port 15740, takes a baseline of existing card objects, and then downloads only NEW JPEG objects. Existing photos on the card are not pulled. The downloaded JPEG is fed into the normal phone/Flickr/session pipeline.

For this first beta, keep the Nikon Bluetooth screen open while testing. Wait until the status says READY - take a picture before shooting.'''
    s=s.replace(old_help,new_help)
    p.write_text(s)
