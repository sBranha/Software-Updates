package com.nikonautoupload;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Experimental Nikon smart-device Bluetooth pairing/reconnect screen.
 *
 * This is an independent Android implementation of publicly documented Nikon
 * smart-device protocol facts. Protocol research was cross-checked against the
 * MIT-licensed Furble Nikon implementation and public Nikon smart-device protocol
 * documentation. Full-resolution photo transfer remains on Wi-Fi/FTP.
 */
public class NikonBluetoothActivity extends Activity {
    private static final UUID SERVICE_UUID = UUID.fromString("0000de00-3dd4-4255-8d62-6dc7b9bd5561");
    private static final UUID PAIR_UUID    = UUID.fromString("00002000-3dd4-4255-8d62-6dc7b9bd5561");
    private static final UUID NOT1_UUID    = UUID.fromString("00002008-3dd4-4255-8d62-6dc7b9bd5561");
    private static final UUID ID_UUID      = UUID.fromString("00002002-3dd4-4255-8d62-6dc7b9bd5561");
    private static final UUID WIFI_CONFIG_UUID = UUID.fromString("00002004-3dd4-4255-8d62-6dc7b9bd5561");
    private static final UUID WIFI_ESTABLISH_UUID = UUID.fromString("00002005-3dd4-4255-8d62-6dc7b9bd5561");
    private static final UUID CCCD_UUID    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final byte[] BLOWFISH_KEY = new byte[]{(byte)0xff,(byte)0xff,(byte)0xaa,0x55,0x11,0x22,0x33,0x00};
    private static final int[][] SALTS = new int[][]{
            {0x704066e4,0x0433d552},{0xed4b8fac,0x15f7e47b},{0x24471f11,0x8b5ea1fc},{0x05960c31,0x2b8c7f41},
            {0xfda588c1,0xeba8b1f3},{0x99166056,0x1bd3d550},{0xcd32687f,0xa9e28a30},{0x2a8fe834,0xdec7ebf4}
    };

    private SharedPreferences p;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic pairChr, not1Chr, idChr, wifiConfigChr, wifiEstablishChr;
    private NikonPtpAutoTransfer autoTransfer;
    private TextView status, log;
    private LinearLayout foundList;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private final Set<String> seen = new HashSet<>();
    private boolean reconnectMode = false;
    private boolean gotSuccess = false;
    private int descriptorStage = 0;
    private long controllerDevice, controllerNonce, ourTimestamp;
    private String selectedCameraName = "Nikon camera";
    private String selectedBleAddress = "";
    private ScanCallback scanCallback;

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice d;
            if (Build.VERSION.SDK_INT >= 33) d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            else d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (d == null) return;
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                String n = safeName(d);
                append("Classic device found: " + (n.isEmpty()?d.getAddress():n));
                if (cameraNameMatches(n)) {
                    try {
                        adapter.cancelDiscovery();
                        setStatus("Classic Nikon found — opening Android pairing…", Color.rgb(255,193,7));
                        append("Calling Android createBond() for " + n);
                        d.createBond();
                    } catch (Exception e) { append("Classic bond start failed: " + e.getMessage()); }
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (state == BluetoothDevice.BOND_BONDED && cameraNameMatches(safeName(d))) {
                    p.edit().putString("nikon_bt_state","Paired — reconnecting").putString("nikon_bt_classic_address",d.getAddress()).apply();
                    setStatus("Bluetooth pairing complete — reconnecting to camera…", Color.rgb(55,206,108));
                    append("Classic bond complete. Starting saved-camera BLE reconnect.");
                    handler.postDelayed(() -> startScan(true), 1500);
                } else if (state == BluetoothDevice.BOND_NONE && cameraNameMatches(safeName(d))) {
                    append("Bluetooth bond not completed. Confirm the passkey on both camera and phone.");
                }
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        p=getSharedPreferences("settings",MODE_PRIVATE);
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
        BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=bm==null?null:bm.getAdapter();
        scanner=adapter==null?null:adapter.getBluetoothLeScanner();
        registerBtReceiver();
        draw();
        requestBluetoothPermissions();
    }

    @Override protected void onDestroy() {
        stopScan();
        try { if(adapter!=null)adapter.cancelDiscovery(); } catch(Exception ignored){}
        if(autoTransfer!=null)autoTransfer.stop();
        closeGatt();
        try { unregisterReceiver(btReceiver); } catch(Exception ignored){}
        super.onDestroy();
    }

    private void registerBtReceiver(){
        IntentFilter f=new IntentFilter();f.addAction(BluetoothDevice.ACTION_FOUND);f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);f.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(btReceiver,f,Context.RECEIVER_EXPORTED); else registerReceiver(btReceiver,f);
    }

    private void draw(){
        int bg=Color.rgb(7,11,15),panel=Color.rgb(16,23,31),white=Color.rgb(245,247,250),muted=Color.rgb(155,166,178),blue=Color.rgb(33,150,243),green=Color.rgb(55,206,108),amber=Color.rgb(255,193,7);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(16),dp(12),dp(16),dp(16));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this);back.setText("‹");back.setTextSize(28);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(54),dp(52)));
        TextView title=text("Nikon Bluetooth",22,white,true);top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(top);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout info=card(panel);info.addView(text("EXPERIMENTAL SNAPBRIDGE-STYLE CONNECTION",13,blue,true));
        info.addView(text("Bluetooth stays connected for control. When Auto Photo Handoff is ON, the app reads Nikon Wi-Fi information over Bluetooth, joins the camera Wi-Fi, opens PTP/IP, and pulls each NEW full-resolution JPEG to the phone. If Nikon protects the Wi-Fi password, enter the camera password once below; the app reuses it automatically after that.",13,muted,false));
        info.addView(text("On the camera: Network menu → Connect to smart device → Pairing (Bluetooth) → Start pairing. Then tap SCAN FOR NIKON CAMERA below.",13,white,false));body.addView(info);

        status=text(p.getString("nikon_bt_state","Not paired"),15,muted,true);status.setPadding(dp(12),dp(12),dp(12),dp(12));status.setBackgroundColor(panel);body.addView(status,new LinearLayout.LayoutParams(-1,dp(52)));

        Switch autoHandoff=new Switch(this);autoHandoff.setText("Automatic full-resolution photo handoff");autoHandoff.setTextColor(white);autoHandoff.setChecked(p.getBoolean("nikon_auto_handoff_enabled",true));autoHandoff.setPadding(dp(8),dp(6),dp(8),dp(6));
        autoHandoff.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean("nikon_auto_handoff_enabled",on).apply();append("[PHOTO] Auto handoff "+(on?"ON":"OFF"));if(!on&&autoTransfer!=null)autoTransfer.stop();else if(on&&p.getString("nikon_bt_state","").toLowerCase(Locale.US).contains("connected"))requestWifiConfiguration();});
        body.addView(autoHandoff,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView handoffState=text("Photo handoff: "+p.getString("nikon_handoff_state","waiting for Bluetooth"),12,muted,false);handoffState.setPadding(dp(8),dp(2),dp(8),dp(6));body.addView(handoffState);
        Button handoff=button("START / RETRY PHOTO HANDOFF",amber);handoff.setOnClickListener(v->requestWifiConfiguration());body.addView(handoff);

        LinearLayout wifiPasswordCard=card(panel);
        wifiPasswordCard.addView(text("CAMERA WI-FI PASSWORD — ONE-TIME FALLBACK",13,blue,true));
        wifiPasswordCard.addView(text("Some Nikon firmware protects the Wi-Fi password inside Bluetooth characteristic 2004. If the app says the password is protected, enter the Wi-Fi password shown by the camera once. It is saved on this phone and reused automatically.",12,muted,false));
        EditText wifiPasswordInput=new EditText(this);
        wifiPasswordInput.setHint("Camera Wi-Fi password");
        wifiPasswordInput.setHintTextColor(muted);wifiPasswordInput.setTextColor(white);wifiPasswordInput.setSingleLine(true);
        wifiPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        wifiPasswordInput.setText(p.getString("nikon_wifi_password_override",""));
        wifiPasswordCard.addView(wifiPasswordInput,new LinearLayout.LayoutParams(-1,dp(52)));
        Button saveWifiPassword=button("SAVE PASSWORD & RETRY HANDOFF",blue);
        saveWifiPassword.setOnClickListener(v->{
            String entered=wifiPasswordInput.getText().toString();
            if(!isValidWifiPassphrase(entered)){
                setStatus("Enter the camera Wi-Fi password (8–63 ASCII characters)",Color.rgb(255,193,7));
                append("[PHOTO] Camera Wi-Fi password was not saved: use 8–63 normal ASCII characters.");
                return;
            }
            p.edit().putString("nikon_wifi_password_override",entered).apply();
            append("[PHOTO] One-time Nikon camera Wi-Fi password saved. Retrying handoff.");
            requestWifiConfiguration();
        });
        wifiPasswordCard.addView(saveWifiPassword);
        Button clearWifiPassword=button("CLEAR SAVED CAMERA WI-FI PASSWORD",Color.rgb(70,80,90));
        clearWifiPassword.setOnClickListener(v->{p.edit().remove("nikon_wifi_password_override").apply();wifiPasswordInput.setText("");append("[PHOTO] Saved Nikon camera Wi-Fi password cleared.");});
        wifiPasswordCard.addView(clearWifiPassword);
        body.addView(wifiPasswordCard);

        Button scan=button("SCAN FOR NIKON CAMERA",green);scan.setOnClickListener(v->startScan(false));body.addView(scan);
        Button saved=button("CONNECT SAVED NIKON CAMERA",blue);saved.setOnClickListener(v->startScan(true));body.addView(saved);
        Button openBt=button("OPEN ANDROID BLUETOOTH SETTINGS",Color.rgb(70,80,90));openBt.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));body.addView(openBt);

        TextView fh=text("FOUND CAMERAS",13,blue,true);fh.setPadding(0,dp(12),0,dp(6));body.addView(fh);
        foundList=new LinearLayout(this);foundList.setOrientation(LinearLayout.VERTICAL);body.addView(foundList);
        TextView lh=text("PAIRING LOG",13,blue,true);lh.setPadding(0,dp(12),0,dp(6));body.addView(lh);
        log=text("Ready.\n",12,muted,false);log.setTextIsSelectable(true);log.setPadding(dp(12),dp(10),dp(12),dp(18));log.setBackgroundColor(panel);body.addView(log);
        setContentView(root);
    }

    private void startScan(boolean reconnect){
        if(!hasBluetoothPermissions()){requestBluetoothPermissions();return;}
        if(adapter==null){setStatus("This phone has no Bluetooth adapter",Color.RED);return;}
        if(!adapter.isEnabled()){startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));setStatus("Turn Bluetooth ON, then try again",Color.rgb(255,193,7));return;}
        scanner=adapter.getBluetoothLeScanner();if(scanner==null){setStatus("Bluetooth LE scanner unavailable",Color.RED);return;}
        reconnectMode=reconnect;seen.clear();foundList.removeAllViews();stopScan();
        setStatus(reconnect?"Scanning for saved Nikon camera…":"Scanning for Nikon cameras…",Color.rgb(33,150,243));
        append(reconnect?"Saved-camera scan started":"New pairing scan started");
        ScanFilter filter=new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build();
        ScanSettings settings=new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanCallback=new ScanCallback(){
            @Override public void onScanResult(int callbackType, ScanResult result){handleResult(result);}
            @Override public void onBatchScanResults(List<ScanResult> results){for(ScanResult r:results)handleResult(r);}
            @Override public void onScanFailed(int errorCode){runOnUiThread(()->{append("BLE scan failed: "+errorCode);setStatus("Bluetooth scan failed: "+errorCode,Color.RED);});}
        };
        try{scanner.startScan(Collections.singletonList(filter),settings,scanCallback);}catch(Exception e){append("Scan start failed: "+e.getMessage());}
        handler.postDelayed(()->{stopScan();if(seen.isEmpty())setStatus("No Nikon camera found — make sure Start pairing is showing on the camera",Color.rgb(255,193,7));},15000);
    }

    private void handleResult(ScanResult result){
        BluetoothDevice d=result.getDevice();String address=d.getAddress();if(!seen.add(address))return;
        String name=safeName(d);if(name.isEmpty())name="Nikon camera";
        final String display=name;append("Found BLE Nikon: "+display+"  "+address);
        if(reconnectMode && savedResultMatches(result,display)) { stopScan();connectGatt(d,display,true);return; }
        Button b=button("PAIR  "+display+"\n"+address,Color.rgb(33,150,243));b.setAllCaps(false);b.setOnClickListener(v->{stopScan();connectGatt(d,display,false);});foundList.addView(b);
    }

    private boolean savedResultMatches(ScanResult r,String name){
        long saved=p.getLong("nikon_bt_controller_device",-1L);String savedName=p.getString("nikon_bt_camera_name","");
        if(saved<0)return false;
        try{
            byte[] m=r.getScanRecord()==null?null:r.getScanRecord().getManufacturerSpecificData(0x0399);
            if(m!=null&&m.length>=4){long dev=((long)m[0]&255)|(((long)m[1]&255)<<8)|(((long)m[2]&255)<<16)|(((long)m[3]&255)<<24);if((dev&0xffffffffL)==(saved&0xffffffffL))return true;}
        }catch(Exception ignored){}
        return !savedName.isEmpty()&&savedName.equals(name);
    }

    private void connectGatt(BluetoothDevice d,String name,boolean saved){
        if(!hasBluetoothPermissions())return;closeGatt();selectedCameraName=name;selectedBleAddress=d.getAddress();reconnectMode=saved;
        if(saved){controllerDevice=p.getLong("nikon_bt_controller_device",0);controllerNonce=p.getLong("nikon_bt_controller_nonce",0);}
        else {controllerDevice=(random.nextInt()&0xffffff00L)|0x01L;controllerNonce=random.nextInt()&0xffffffffL;}
        setStatus("Connecting to "+name+"…",Color.rgb(33,150,243));append("GATT connect: "+d.getAddress());
        try{gatt=d.connectGatt(this,false,gattCallback,BluetoothDevice.TRANSPORT_LE);}catch(Exception e){append("GATT connect failed: "+e.getMessage());}
    }

    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int statusCode,int newState){
            runOnUiThread(()->append("GATT state="+newState+" status="+statusCode));
            if(newState==BluetoothProfile.STATE_CONNECTED){runOnUiThread(()->setStatus("BLE connected — discovering Nikon service…",Color.rgb(33,150,243)));g.discoverServices();}
            else if(newState==BluetoothProfile.STATE_DISCONNECTED){runOnUiThread(()->{if(!p.getString("nikon_bt_state","").contains("paired"))setStatus("Bluetooth disconnected",Color.rgb(155,166,178));});}
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int statusCode){
            BluetoothGattService svc=g.getService(SERVICE_UUID);if(svc==null){runOnUiThread(()->setStatus("Nikon smart-device service not found",Color.RED));return;}
            pairChr=svc.getCharacteristic(PAIR_UUID);not1Chr=svc.getCharacteristic(NOT1_UUID);idChr=svc.getCharacteristic(ID_UUID);
            wifiConfigChr=svc.getCharacteristic(WIFI_CONFIG_UUID);wifiEstablishChr=svc.getCharacteristic(WIFI_ESTABLISH_UUID);
            if(pairChr==null||not1Chr==null||idChr==null){runOnUiThread(()->setStatus("Required Nikon Bluetooth characteristics are missing",Color.RED));return;}
            runOnUiThread(()->{append("Nikon smart-device service discovered");append("[PHOTO] Wi-Fi config 2004: "+(wifiConfigChr==null?"MISSING":"found")+" • establish 2005: "+(wifiEstablishChr==null?"MISSING":"found"));});descriptorStage=0;enablePairIndications(g);
        }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int statusCode){
            if(statusCode!=BluetoothGatt.GATT_SUCCESS){runOnUiThread(()->append("Descriptor write failed: "+statusCode));return;}
            if(descriptorStage==0){descriptorStage=1;enableNot1Notifications(g);}else if(descriptorStage==1){descriptorStage=2;runOnUiThread(()->append("PAIR + NOT1 subscriptions enabled"));sendStage1();}
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){handleChanged(c,c.getValue());}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int statusCode){handleCharacteristicRead(c,c.getValue(),statusCode);}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int statusCode){handleCharacteristicRead(c,value,statusCode);}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int statusCode){
            runOnUiThread(()->append("Write "+shortUuid(c.getUuid())+" status="+statusCode));
            if(c.getUuid().equals(ID_UUID)&&statusCode==BluetoothGatt.GATT_SUCCESS){handler.postDelayed(()->finishBlePairingPhase(),600);}
        }
    };

    private void enablePairIndications(BluetoothGatt g){
        g.setCharacteristicNotification(pairChr,true);BluetoothGattDescriptor d=pairChr.getDescriptor(CCCD_UUID);if(d==null){runOnUiThread(()->setStatus("PAIR notification descriptor missing",Color.RED));return;}d.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);g.writeDescriptor(d);
    }
    private void enableNot1Notifications(BluetoothGatt g){
        g.setCharacteristicNotification(not1Chr,true);BluetoothGattDescriptor d=not1Chr.getDescriptor(CCCD_UUID);if(d==null){runOnUiThread(()->setStatus("NOT1 notification descriptor missing",Color.RED));return;}d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);g.writeDescriptor(d);
    }

    private void sendStage1(){
        ourTimestamp=random.nextLong();byte[] msg=pairMessage(1,ourTimestamp,(int)controllerDevice,(int)controllerNonce);gotSuccess=false;
        runOnUiThread(()->{setStatus("Running Nikon Bluetooth handshake…",Color.rgb(33,150,243));append("Sending pairing stage 1");});write(pairChr,msg);
    }

    private void handleChanged(BluetoothGattCharacteristic c,byte[] data){
        if(data==null)return;
        if(c.getUuid().equals(NOT1_UUID)){
            if(data.length>=2&&data[0]==1&&data[1]==0){gotSuccess=true;runOnUiThread(()->append("Nikon NOT1 success received"));}
            else if(autoTransfer!=null&&autoTransfer.isRunning()){runOnUiThread(()->append("[PHOTO] Nikon 2008 event: "+hex(data)));autoTransfer.kickPoll();}
            return;
        }
        if(!c.getUuid().equals(PAIR_UUID)||data.length<17)return;
        int stage=data[0]&255;runOnUiThread(()->append("Received pairing stage "+stage));
        if(stage==2){
            try{byte[] response=buildStage3(data);if(response==null){runOnUiThread(()->setStatus("Nikon challenge could not be verified",Color.RED));return;}write(pairChr,response);runOnUiThread(()->append("Sending pairing stage 3"));}
            catch(Exception e){runOnUiThread(()->setStatus("Pairing calculation failed: "+e.getMessage(),Color.RED));}
        } else if(stage==4){
            String serial=new String(Arrays.copyOfRange(data,9,17)).replace("\u0000","").trim();p.edit().putString("nikon_bt_camera_serial",serial).apply();runOnUiThread(()->append("Camera serial: "+serial));
            String controller=p.getString("nikon_bt_controller_name","");if(controller.isEmpty()){controller=controllerName();p.edit().putString("nikon_bt_controller_name",controller).apply();}
            final String controllerFinal=controller;
            write(idChr,controllerFinal.getBytes(java.nio.charset.StandardCharsets.US_ASCII));runOnUiThread(()->append("Writing controller name: "+controllerFinal));
        }
    }

    private byte[] buildStage3(byte[] stage2) throws Exception{
        ByteBuffer b=ByteBuffer.wrap(stage2).order(ByteOrder.LITTLE_ENDIAN);b.get();long camTs=b.getLong();int camDev=b.getInt(),camNonce=b.getInt();
        int ourLo=Integer.reverseBytes((int)(ourTimestamp&0xffffffffL)),ourHi=Integer.reverseBytes((int)((ourTimestamp>>>32)&0xffffffffL));
        int camLo=Integer.reverseBytes((int)(camTs&0xffffffffL)),camHi=Integer.reverseBytes((int)((camTs>>>32)&0xffffffffL));
        int matched=-1;
        for(int i=0;i<SALTS.length;i++){
            int[] h=hash(new int[]{SALTS[i][0],SALTS[i][1],camLo,camHi,ourLo,ourHi});
            if(h[0]==Integer.reverseBytes(camDev)&&h[1]==Integer.reverseBytes(camNonce)){matched=i;break;}
        }
        if(matched<0)return null;
        final int mi=matched;runOnUiThread(()->append("Nikon challenge verified (salt "+mi+")"));
        int[] r=hash(new int[]{SALTS[matched][0],SALTS[matched][1],ourLo,ourHi,camLo,camHi});
        int dev=Integer.reverseBytes(r[0]),nonce=Integer.reverseBytes(r[1]);
        return pairMessage(3,ourTimestamp,dev,nonce);
    }

    private int[] hash(int[] src) throws Exception{
        int left=0x01020304,right=0x05060708,inL=0,inR=0;
        Cipher cipher=Cipher.getInstance("Blowfish/ECB/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(BLOWFISH_KEY,"Blowfish"));
        for(int i=0;i<src.length;i+=2){inL=src[i]^left;inR=src[i+1]^right;ByteBuffer block=ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(inL).putInt(inR);byte[] enc=cipher.doFinal(block.array());ByteBuffer out=ByteBuffer.wrap(enc).order(ByteOrder.BIG_ENDIAN);inL=out.getInt();inR=out.getInt();left=inL;right=inR;}
        return new int[]{inL,inR};
    }

    private byte[] pairMessage(int stage,long timestamp,int device,int nonce){return ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN).put((byte)stage).putLong(timestamp).putInt(device).putInt(nonce).array();}

    private void finishBlePairingPhase(){
        p.edit().putLong("nikon_bt_controller_device",controllerDevice&0xffffffffL).putLong("nikon_bt_controller_nonce",controllerNonce&0xffffffffL).putString("nikon_bt_camera_name",selectedCameraName).putString("nikon_bt_last_ble_address",selectedBleAddress).apply();
        if(reconnectMode&&hasClassicBondForCamera()){
            p.edit().putString("nikon_bt_state","Bluetooth connected").apply();setStatus("Nikon Bluetooth connected",Color.rgb(55,206,108));append("Saved Nikon Bluetooth connection is ready.");
            if(p.getBoolean("nikon_auto_handoff_enabled",true))handler.postDelayed(this::requestWifiConfiguration,800);
            return;
        }
        setStatus("BLE handshake complete — starting Android Bluetooth pairing…",Color.rgb(255,193,7));append(gotSuccess?"Nikon handshake accepted":"Proceeding to Classic bond; NOT1 success may arrive later on this camera");beginClassicBonding();
    }

    private void requestWifiConfiguration(){
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
        String parsedSsid=asciiZ(data,1,32),parsedPassword=asciiZ(data,33,64);
        int security=data[97]&255;
        String ip=(data[98]&255)+"."+(data[99]&255)+"."+(data[100]&255)+"."+(data[101]&255);
        String savedSsid=p.getString("nikon_handoff_ssid","");
        final String ssid=(parsedSsid==null||parsedSsid.isEmpty())?savedSsid:parsedSsid;
        final boolean protectedPassword=(parsedPassword==null||!isValidWifiPassphrase(parsedPassword));
        String savedPassword=p.getString("nikon_wifi_password_override","");
        final String password=protectedPassword?savedPassword:parsedPassword;
        if(ssid==null||ssid.isEmpty()){
            runOnUiThread(()->{append("[PHOTO] Nikon 2004 Wi-Fi name is protected/unreadable.");setStatus("Could not read the Nikon camera Wi-Fi name",Color.RED);});
            return;
        }
        p.edit().putString("nikon_handoff_ssid",ssid).putString("nikon_handoff_ip_hint",ip).putInt("nikon_handoff_security",security).apply();
        if(protectedPassword&&!isValidWifiPassphrase(password)){
            runOnUiThread(()->{
                append("[PHOTO] Nikon 2004 password field is protected/non-ASCII. It will NOT be sent to Android as a WPA password.");
                append("[PHOTO] Enter the camera Wi-Fi password once in CAMERA WI-FI PASSWORD, then tap SAVE PASSWORD & RETRY HANDOFF.");
                p.edit().putString("nikon_handoff_state","Camera Wi-Fi password needed once").apply();
                setStatus("Nikon protects its Wi-Fi password — enter it once below",Color.rgb(255,193,7));
            });
            return;
        }
        runOnUiThread(()->{
            append("[PHOTO] Nikon Wi-Fi configuration ready: SSID="+ssid+" • security="+security+" • camera IP hint="+ip+" • password source="+(protectedPassword?"saved one-time password":"camera Bluetooth"));
            if(autoTransfer!=null)autoTransfer.startWifi(ssid,password,ip);
        });
    }

    private String asciiZ(byte[] data,int start,int len){
        int end=Math.min(data.length,start+len),n=start;
        while(n<end&&data[n]!=0){int v=data[n]&255;if(v<0x20||v>0x7e)return null;n++;}
        if(n<=start)return "";
        return new String(data,start,n-start,java.nio.charset.StandardCharsets.US_ASCII);
    }
    private boolean isValidWifiPassphrase(String value){
        if(value==null||value.length()<8||value.length()>63)return false;
        return java.nio.charset.StandardCharsets.US_ASCII.newEncoder().canEncode(value);
    }
    private String hex(byte[] b){StringBuilder s=new StringBuilder();for(int i=0;i<Math.min(b.length,32);i++)s.append(String.format(Locale.US,"%02X",b[i]&255)).append(i+1<Math.min(b.length,32)?" ":"");return s.toString();}

    private void beginClassicBonding(){
        closeGatt();
        if(!hasBluetoothPermissions())return;
        try{adapter.cancelDiscovery();boolean ok=adapter.startDiscovery();append("Classic Bluetooth discovery started: "+ok);if(!ok)setStatus("Could not start Android Bluetooth discovery",Color.RED);else setStatus("Confirm the Bluetooth passkey on camera and phone",Color.rgb(255,193,7));}
        catch(Exception e){append("Classic discovery error: "+e.getMessage());}
    }

    private boolean hasClassicBondForCamera(){
        if(adapter==null||!hasBluetoothPermissions())return false;
        try{for(BluetoothDevice d:adapter.getBondedDevices())if(cameraNameMatches(safeName(d)))return true;}catch(Exception ignored){}return false;
    }

    private boolean cameraNameMatches(String n){if(n==null||n.isEmpty())return false;String a=n.toLowerCase(Locale.US),b=selectedCameraName.toLowerCase(Locale.US);return a.equals(b)||a.contains(b)||b.contains(a)||a.startsWith("nikon")||a.startsWith("z8")||a.startsWith("z9")||a.startsWith("z6")||a.startsWith("z7")||a.startsWith("z5")||a.startsWith("zf");}

    private void write(BluetoothGattCharacteristic c,byte[] value){if(gatt==null||c==null)return;try{c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);c.setValue(value);if(!gatt.writeCharacteristic(c))runOnUiThread(()->append("Characteristic write could not start"));}catch(Exception e){runOnUiThread(()->append("Write error: "+e.getMessage()));}}

    private String controllerName(){String model=Build.MODEL==null?"Android":Build.MODEL.replaceAll("[^A-Za-z0-9]","_");return "Android_"+model+"_"+String.format(Locale.US,"%04d",random.nextInt(10000));}
    private String safeName(BluetoothDevice d){try{String n=d.getName();return n==null?"":n;}catch(Exception e){return "";}}
    private String shortUuid(UUID u){String s=u.toString();return s.length()>8?s.substring(4,8):s;}

    private void stopScan(){if(scanner!=null&&scanCallback!=null&&hasBluetoothPermissions())try{scanner.stopScan(scanCallback);}catch(Exception ignored){}scanCallback=null;}
    private void closeGatt(){try{if(gatt!=null){gatt.disconnect();gatt.close();}}catch(Exception ignored){}gatt=null;pairChr=null;not1Chr=null;idChr=null;wifiConfigChr=null;wifiEstablishChr=null;}

    private boolean hasBluetoothPermissions(){if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void requestBluetoothPermissions(){ArrayList<String> req=new ArrayList<>();if(Build.VERSION.SDK_INT>=31){if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.BLUETOOTH_SCAN);if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.BLUETOOTH_CONNECT);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.NEARBY_WIFI_DEVICES);}else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACCESS_FINE_LOCATION);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),80);}

    private void append(String s){runOnUiThread(()->{if(log!=null){String old=log.getText().toString();if(old.length()>8000)old=old.substring(old.length()-6000);log.setText(old+s+"\n");}});}
    private void setStatus(String s,int color){runOnUiThread(()->{p.edit().putString("nikon_bt_state",s).apply();if(status!=null){status.setText(s);status.setTextColor(color);}});}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);t.setLineSpacing(0,1.08f);return t;}
    private LinearLayout card(int color){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(12),dp(14),dp(12));l.setBackgroundColor(color);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));l.setLayoutParams(lp);return l;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(5),0,dp(5));b.setLayoutParams(lp);return b;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
