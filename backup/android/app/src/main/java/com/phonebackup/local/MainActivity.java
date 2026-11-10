package com.phonebackup.local;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.InterfaceAddress;
import java.net.Inet4Address;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.io.DataInputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final int PICK = 41;
    private static final int PERMISSIONS = 42;
    private static final int PORT = 38471;
    private final ArrayList<Uri> selected = new ArrayList<>();
    private EditText pairingCode;
    private TextView phoneAddress;
    private TextView status;
    private CheckBox apps, sms, contacts;
    private Button backupButton;
    private volatile boolean cancelRequested = false;
    private volatile boolean phoneIsReady = false;
    private volatile ServerSocket backupServer;
    private ScrollView backupScreen;
    private PowerManager.WakeLock cpuWakeLock;
    private WifiManager.WifiLock wifiWakeLock;
    private boolean backupActive = false;

    private static class Source {
        String category, name, sha256;
        Uri uri;
        File file;
        byte[] bytes;
        long size;
        Source(String category, String name, Uri uri, File file, byte[] bytes) {
            this.category=category; this.name=name;
            this.uri=uri; this.file=file; this.bytes=bytes;
        }
    }

    private class Crypto {
        private final byte[] salt;
        private final Cipher cipher;
        private final SecretKeySpec key;
        private long counter = 0;
        Crypto(String code, byte[] salt, byte[] shared) throws Exception {
            this.salt=salt;
            // HKDF SHA256: ECDH gives a strong session secret even with a short pairing code.
            Mac extract=Mac.getInstance("HmacSHA256");
            extract.init(new SecretKeySpec(salt,"HmacSHA256"));
            byte[] prk=extract.doFinal(shared);
            Mac expand=Mac.getInstance("HmacSHA256");
            expand.init(new SecretKeySpec(prk,"HmacSHA256"));
            byte[] info=("WPB3-data-"+code).getBytes(StandardCharsets.US_ASCII);
            expand.update(info);
            expand.update((byte)1);
            byte[] material=expand.doFinal();
            key=new SecretKeySpec(material,"AES");
            Arrays.fill(prk,(byte)0);
            Arrays.fill(material,(byte)0);
            cipher=Cipher.getInstance("AES/GCM/NoPadding");
        }
        byte[] encrypt(byte[] plain) throws Exception {
            byte[] nonce=ByteBuffer.allocate(12).put(salt,0,4).putLong(counter++).array();
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            return cipher.doFinal(plain);
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);
        backupScreen=scroll;
        scroll.setFillViewport(true);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30,24,30,34);
        scroll.addView(layout);
        TextView headline=new TextView(this);
        headline.setText("WIRELESS PHONE BACKUP v0.6");
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP,25);
        headline.setPadding(0,6,0,12);
        layout.addView(headline);
        TextView howto=new TextView(this);
        howto.setText("1. Tap MAKE PHONE READY below.\n2. On Windows, choose your backup drive, enter this code and click FIND PHONE & BACKUP.\nYour phone stays awake through the backup.");
        howto.setTextSize(TypedValue.COMPLEX_UNIT_SP,19);
        layout.addView(howto);
        phoneAddress=new TextView(this);
        phoneAddress.setText("Phone Wi-Fi IP address: "+phoneWifiAddress());
        phoneAddress.setTextSize(TypedValue.COMPLEX_UNIT_SP,22);
        phoneAddress.setPadding(0,16,0,6);
        layout.addView(phoneAddress);
        TextView pairLabel=new TextView(this);
        pairLabel.setText("Your 6-DIGIT pairing code:");
        pairLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP,20);
        layout.addView(pairLabel);
        pairingCode=new EditText(this);
        pairingCode.setText(String.format(java.util.Locale.US,"%06d",new SecureRandom().nextInt(1000000)));
        pairingCode.setEnabled(false);
        pairingCode.setTextSize(TypedValue.COMPLEX_UNIT_SP,30);
        layout.addView(pairingCode);
        Button choose=new Button(this);
        choose.setText("Choose photos, videos and documents");
        choose.setTextSize(TypedValue.COMPLEX_UNIT_SP,20);
        layout.addView(choose);
        choose.setOnClickListener(view->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(intent,PICK);
        });
        apps=new CheckBox(this);
        apps.setText("Backup accessible installed app APKs (including splits)");
        apps.setTextSize(TypedValue.COMPLEX_UNIT_SP,19);
        apps.setChecked(true);
        layout.addView(apps);
        sms=new CheckBox(this);
        sms.setText("Backup accessible SMS/MMS messages and attachments");
        sms.setTextSize(TypedValue.COMPLEX_UNIT_SP,19);
        sms.setChecked(true);
        layout.addView(sms);
        contacts=new CheckBox(this);
        contacts.setText("Backup contacts");
        contacts.setTextSize(TypedValue.COMPLEX_UNIT_SP,19);
        contacts.setChecked(true);
        layout.addView(contacts);
        backupButton=new Button(this);
        backupButton.setText("MAKE PHONE READY");
        backupButton.setTextSize(TypedValue.COMPLEX_UNIT_SP,23);
        layout.addView(backupButton);
        status=new TextView(this);
        status.setText("READY TO START. No Windows firewall setup is required.");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP,20);
        status.setPadding(0,14,0,20);
        layout.addView(status);
        setContentView(scroll);
        backupButton.setOnClickListener(v->{
            if(backupActive && phoneIsReady) {
                cancelRequested=true;
                try { if(backupServer!=null) backupServer.close(); } catch(Exception ignored) {}
                show("Stopping phone listener...");
            } else if(!backupActive) {
                startBackup();
            }
        });
    }

    private String phoneWifiAddress() {
        try {
            WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(wifi!=null && wifi.getConnectionInfo()!=null) {
                int ip=wifi.getConnectionInfo().getIpAddress();
                if(ip!=0) return (ip&255)+"."+((ip>>8)&255)+"."+((ip>>16)&255)+"."+((ip>>24)&255);
            }
            for(Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();interfaces!=null&&interfaces.hasMoreElements();) {
                NetworkInterface iface=interfaces.nextElement();
                if(!iface.isUp() || iface.isLoopback()) continue;
                for(InterfaceAddress addr:iface.getInterfaceAddresses()) {
                    if(addr.getAddress() instanceof Inet4Address && addr.getAddress().isSiteLocalAddress())
                        return addr.getAddress().getHostAddress();
                }
            }
        } catch(Exception ignored) {}
        return "Connect your phone to Wi-Fi";
    }

    private void respondToDiscovery() {
        try(DatagramSocket udp=new DatagramSocket(38472)) {
            udp.setSoTimeout(1000);
            byte[] buffer=new byte[128];
            while(backupActive && !cancelRequested) {
                try {
                    DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                    udp.receive(packet);
                    String query=new String(packet.getData(),0,packet.getLength(),StandardCharsets.US_ASCII);
                    if(query.equals("WPB_FIND_PHONE_V6")) {
                        byte[] response="WPB_PHONE_READY_V6".getBytes(StandardCharsets.US_ASCII);
                        udp.send(new DatagramPacket(response,response.length,packet.getAddress(),packet.getPort()));
                    }
                } catch(SocketTimeoutException ignored) {}
            }
        } catch(Exception e) {
            android.util.Log.w("WirelessPhoneBackup","Phone discovery unavailable: direct phone IP still works",e);
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK || result!=RESULT_OK || data==null) return;
        if(data.getClipData()!=null) {
            for(int n=0;n<data.getClipData().getItemCount();n++)
                selected.add(data.getClipData().getItemAt(n).getUri());
        } else if(data.getData()!=null) selected.add(data.getData());
        show(selected.size()+" personal files selected.");
    }

    private void show(String text) {
        runOnUiThread(()->status.setText(text));
    }

    /** Status port is separate from the file transfer port so a connection check
     * cannot consume Windows' one-shot backup listener. */
    /** Keep the screen visible, CPU running and Wi-Fi connected for the full backup.
     * All locks are released in finishBackup(), even if transfer throws an exception.
     */
    private void keepPhoneAwakeForBackup() {
        backupActive = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (backupScreen != null) backupScreen.setKeepScreenOn(true);
        try {
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (power != null) {
                cpuWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WirelessPhoneBackup:ActiveTransfer");
                cpuWakeLock.setReferenceCounted(false);
                cpuWakeLock.acquire();
            }
        } catch (Exception e) {
            android.util.Log.w("WirelessPhoneBackup", "Could not acquire CPU wake lock", e);
        }
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                wifiWakeLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WirelessPhoneBackup:WifiTransfer");
                wifiWakeLock.setReferenceCounted(false);
                wifiWakeLock.acquire();
            }
        } catch (Exception e) {
            android.util.Log.w("WirelessPhoneBackup", "Could not acquire Wi-Fi lock", e);
        }
    }

    private void finishBackup() {
        backupActive = false;
        if (backupScreen != null) backupScreen.setKeepScreenOn(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (wifiWakeLock != null) {
            try {
                if (wifiWakeLock.isHeld()) wifiWakeLock.release();
            } catch (Exception e) {
                android.util.Log.w("WirelessPhoneBackup", "Wi-Fi lock release failed", e);
            } finally {
                wifiWakeLock = null;
            }
        }
        if (cpuWakeLock != null) {
            try {
                if (cpuWakeLock.isHeld()) cpuWakeLock.release();
            } catch (Exception e) {
                android.util.Log.w("WirelessPhoneBackup", "CPU wake lock release failed", e);
            } finally {
                cpuWakeLock = null;
            }
        }
    }

    @Override public void onResume() {
        super.onResume();
        if (backupActive) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (backupScreen != null) backupScreen.setKeepScreenOn(true);
        }
    }

    private void startBackup() {
        ArrayList<String> ask=new ArrayList<>();
        if(sms.isChecked() && checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)
            ask.add(Manifest.permission.READ_SMS);
        if(contacts.isChecked() && checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)
            ask.add(Manifest.permission.READ_CONTACTS);
        if(!ask.isEmpty()) {
            show("Grant requested permissions or turn off the categories you don't want.");
            requestPermissions(ask.toArray(new String[0]),PERMISSIONS);
            return;
        }
        final String code=pairingCode.getText().toString().trim();
        final boolean includeApps=apps.isChecked();
        final boolean includeMessages=sms.isChecked();
        final boolean includeContacts=contacts.isChecked();
        cancelRequested=false;
        keepPhoneAwakeForBackup();
        phoneIsReady=true;
        backupButton.setText("CANCEL WAITING");
        chooseOptionsEnabled(false);
        show("PHONE READY: Now on Windows click FIND PHONE & BACKUP. Your phone stays awake.");
        new Thread(()->{
            try(ServerSocket server=new ServerSocket(PORT)) {
                backupServer=server;
                server.setSoTimeout(1000);
                new Thread(this::respondToDiscovery).start();
                phoneAddress.post(()->phoneAddress.setText("Phone Wi-Fi IP address: "+phoneWifiAddress()));
                long deadline=System.currentTimeMillis()+15*60*1000;
                Socket client=null;
                while(!cancelRequested && System.currentTimeMillis()<deadline) {
                    try {
                        client=server.accept();
                        break;
                    } catch(SocketTimeoutException ignored) {}
                }
                if(cancelRequested) {
                    show("Cancelled. Tap MAKE PHONE READY to try again.");
                } else if(client==null) {
                    show("Waiting expired after 15 minutes. Tap MAKE PHONE READY again.");
                } else {
                    phoneIsReady=false;
                    final Socket incoming=client;
                    runOnUiThread(()->backupButton.setEnabled(false));
                    show("WINDOWS CONNECTED. Scanning the selected phone content...");
                    transfer(incoming,code,includeApps,includeMessages,includeContacts);
                }
            } catch(Exception e) {
                if(!cancelRequested)
                    show("Backup failed: "+e.getClass().getSimpleName()+": "+e.getMessage());
            } finally {
                phoneIsReady=false;
                backupServer=null;
                runOnUiThread(()->{
                    finishBackup();
                    backupButton.setEnabled(true);
                    backupButton.setText("MAKE PHONE READY");
                    chooseOptionsEnabled(true);
                    pairingCode.setText(String.format(java.util.Locale.US,"%06d",new SecureRandom().nextInt(1000000)));
                });
            }
        }).start();
    }

    private void chooseOptionsEnabled(boolean enabled) {
        apps.setEnabled(enabled);
        sms.setEnabled(enabled);
        contacts.setEnabled(enabled);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==PERMISSIONS) {
            boolean denied=false;
            for(int i=0;i<permissions.length;i++) {
                if(grants[i]!=PackageManager.PERMISSION_GRANTED) {
                    denied=true;
                    if(Manifest.permission.READ_SMS.equals(permissions[i])) sms.setChecked(false);
                    if(Manifest.permission.READ_CONTACTS.equals(permissions[i])) contacts.setChecked(false);
                }
            }
            if(denied) show("Some permissions were denied; those categories were turned off. Continuing with allowed data.");
            startBackup();
        }
    }

    private InputStream open(Source source) throws Exception {
        if(source.bytes!=null) return new ByteArrayInputStream(source.bytes);
        if(source.file!=null) return new FileInputStream(source.file);
        return getContentResolver().openInputStream(source.uri);
    }

    private String displayName(Uri uri) {
        try(Cursor cursor=getContentResolver().query(uri,null,null,null,null)) {
            if(cursor!=null && cursor.moveToFirst()) {
                int index=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(index>=0 && cursor.getString(index)!=null) return cursor.getString(index);
            }
        } catch(Exception ignored) { }
        return "selected-file";
    }

    private void inline(List<Source> list,String category,String name,JSONArray json) throws Exception {
        list.add(new Source(category,name,null,null,json.toString(2).getBytes(StandardCharsets.UTF_8)));
    }

    private void collectApps(List<Source> sources,JSONArray skipped) throws Exception {
        JSONArray catalog=new JSONArray();
        PackageManager manager=getPackageManager();
        List<PackageInfo> installed=manager.getInstalledPackages(0);
        int count=0;
        for(PackageInfo pkg:installed) {
            ApplicationInfo application=pkg.applicationInfo;
            if(application==null) continue;
            boolean system=(application.flags & ApplicationInfo.FLAG_SYSTEM)!=0;
            boolean updated=(application.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;
            if(system && !updated) continue;
            JSONObject entry=new JSONObject();
            entry.put("package",pkg.packageName);
            entry.put("version",pkg.versionName==null?"":pkg.versionName);
            JSONArray apkNames=new JSONArray();
            ArrayList<String> paths=new ArrayList<>();
            if(application.sourceDir!=null) paths.add(application.sourceDir);
            if(application.splitSourceDirs!=null) paths.addAll(Arrays.asList(application.splitSourceDirs));
            for(int i=0;i<paths.size();i++) {
                File candidate=new File(paths.get(i));
                String name=pkg.packageName+"__"+(i==0?"base":"split"+i)+".apk";
                if(!candidate.isFile() || !candidate.canRead()) {
                    skipped.put("APK inaccessible: "+name);
                    continue;
                }
                sources.add(new Source("apps",name,null,candidate,null));
                apkNames.put(name);
                count++;
            }
            entry.put("apks",apkNames);
            catalog.put(entry);
        }
        inline(sources,"apps","installed-app-catalog.json",catalog);
        show("STEP 2/4: Found "+count+" accessible app installation packages. Checking file sizes...");
    }

    private void collectContacts(List<Source> sources, JSONArray skipped) throws Exception {
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) {
            skipped.put("Contacts: permission not granted");
            return;
        }
        JSONArray rows=new JSONArray();
        Uri uri=ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] columns={ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER};
        try(Cursor cursor=getContentResolver().query(uri,columns,null,null,null)) {
            if(cursor!=null) while(cursor.moveToNext()) {
                JSONObject item=new JSONObject();
                item.put("contact_id",cursor.getString(0));
                item.put("name",cursor.getString(1));
                item.put("number",cursor.getString(2));
                rows.put(item);
            }
        }
        inline(sources,"contacts","phone-contacts.json",rows);
    }

    private void collectMessages(List<Source> sources,JSONArray skipped) throws Exception {
        if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED) {
            skipped.put("SMS and MMS: permission not granted");
            return;
        }
        JSONArray texts=new JSONArray();
        String[] cols={"_id","thread_id","address","date","type","body"};
        try(Cursor cursor=getContentResolver().query(Uri.parse("content://sms"),cols,null,null,null)) {
            if(cursor!=null) while(cursor.moveToNext()) {
                JSONObject msg=new JSONObject();
                for(int i=0;i<cols.length;i++) msg.put(cols[i],cursor.getString(i));
                texts.put(msg);
            }
        } catch(Exception e) { skipped.put("SMS inaccessible: "+e.getMessage()); }
        inline(sources,"messages","sms.json",texts);

        JSONArray mmsRows=new JSONArray();
        try(Cursor cursor=getContentResolver().query(Uri.parse("content://mms"),
                new String[]{"_id","thread_id","date","msg_box"},null,null,null)) {
            if(cursor!=null) while(cursor.moveToNext()) {
                String id=cursor.getString(0);
                JSONObject mms=new JSONObject();
                mms.put("id",id);
                mms.put("thread_id",cursor.getString(1));
                mms.put("date",cursor.getString(2));
                mms.put("box",cursor.getString(3));
                JSONArray parts=new JSONArray();
                Uri partsUri=Uri.parse("content://mms/"+id+"/part");
                try(Cursor part=getContentResolver().query(partsUri,
                        new String[]{"_id","ct","text","name"},null,null,null)) {
                    if(part!=null) while(part.moveToNext()) {
                        String partId=part.getString(0), type=part.getString(1);
                        JSONObject item=new JSONObject();
                        item.put("part_id",partId);
                        item.put("content_type",type);
                        item.put("text",part.getString(2));
                        item.put("original_name",part.getString(3));
                        if(type!=null && !type.startsWith("text/") && !type.equals("application/smil")) {
                            String name="mms_"+id+"_part_"+partId;
                            sources.add(new Source("mms-attachments",name,
                                    Uri.parse("content://mms/part/"+partId),null,null));
                            item.put("backup_name",name);
                        }
                        parts.put(item);
                    }
                } catch(Exception e) {
                    skipped.put("MMS "+id+" parts unavailable: "+e.getMessage());
                }
                mms.put("parts",parts);
                mmsRows.put(mms);
            }
        } catch(Exception e) { skipped.put("MMS inaccessible: "+e.getMessage()); }
        inline(sources,"messages","mms.json",mmsRows);
        skipped.put("RCS and third-party chat app messages are not included");
    }

    private static String hex(byte[] value) {
        StringBuilder str=new StringBuilder(value.length*2);
        for(byte b:value) str.append(String.format(java.util.Locale.US,"%02x",b & 255));
        return str.toString();
    }

    private void transfer(Socket socket,String code,boolean includeApps,boolean includeMessages,
                          boolean includeContacts) throws Exception {
        show("STEP 1/3: Windows is connected. Scanning phone files...");
        ArrayList<Source> candidates=new ArrayList<>();
        JSONArray skipped=new JSONArray();
        for(Uri uri:selected)
            candidates.add(new Source("files",displayName(uri),uri,null,null));
        if(includeApps) {
            try { collectApps(candidates,skipped); }
            catch(Exception e) { skipped.put("App list: "+e.getMessage()); }
        }
        if(includeContacts) {
            try { collectContacts(candidates,skipped); }
            catch(Exception e) { skipped.put("Contacts: "+e.getMessage()); }
        }
        if(includeMessages) {
            try { collectMessages(candidates,skipped); }
            catch(Exception e) { skipped.put("Messages: "+e.getMessage()); }
        }

        JSONArray files=new JSONArray();
        ArrayList<Source> ready=new ArrayList<>();
        byte[] buf=new byte[65536];
        for(Source candidate:candidates) {
            try(InputStream stream=open(candidate)) {
                if(stream==null) throw new IllegalStateException("Source not readable");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                long bytes=0;
                int n;
                while((n=stream.read(buf))!=-1) {
                    digest.update(buf,0,n);
                    bytes+=n;
                }
                candidate.size=bytes;
                candidate.sha256=hex(digest.digest());
                JSONObject item=new JSONObject();
                item.put("category",candidate.category);
                item.put("name",candidate.name);
                item.put("size",bytes);
                item.put("sha256",candidate.sha256);
                files.put(item);
                ready.add(candidate);
                show("STEP 2/4: Scanned "+ready.size()+" readable files. Current: "+candidate.name);
            } catch(Exception e) {
                skipped.put(candidate.category+"/"+candidate.name+": "+e.getMessage());
            }
        }
        if(ready.isEmpty()) throw new IllegalArgumentException("No readable files selected");
        JSONObject manifest=new JSONObject();
        manifest.put("device",Build.MANUFACTURER+"_"+Build.MODEL);
        manifest.put("files",files);
        manifest.put("unavailable",skipped);
        byte[] salt=new byte[16];
        new SecureRandom().nextBytes(salt);
        // ECDH constructs a new encrypted session after pairing.
        show("STEP 2/3: Preparing secured backup of "+ready.size()+" files...");
        try(Socket transferSocket=socket) {
            socket.setSoTimeout(1800000);
            try(DataOutputStream output=new DataOutputStream(socket.getOutputStream())) {
                DataInputStream input=new DataInputStream(socket.getInputStream());
                KeyPairGenerator gen=KeyPairGenerator.getInstance("EC");
                gen.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair ephemeral=gen.generateKeyPair();
                byte[] myPublic=ephemeral.getPublic().getEncoded();
                output.write(new byte[]{'W','P','B','3'});
                output.writeShort(myPublic.length);
                output.write(myPublic);
                output.write(salt);
                output.flush();
                int keyLength=input.readUnsignedShort();
                if(keyLength<60||keyLength>256) throw new Exception("Invalid Windows pairing reply");
                byte[] remotePublic=new byte[keyLength];
                input.readFully(remotePublic);
                KeyFactory factory=KeyFactory.getInstance("EC");
                KeyAgreement agreement=KeyAgreement.getInstance("ECDH");
                agreement.init(ephemeral.getPrivate());
                agreement.doPhase(factory.generatePublic(new X509EncodedKeySpec(remotePublic)),true);
                Crypto crypto=new Crypto(code,salt,agreement.generateSecret());
                byte[] encryptedManifest=crypto.encrypt(manifest.toString().getBytes(StandardCharsets.UTF_8));
                output.writeInt(encryptedManifest.length);
                output.write(encryptedManifest);
                for(int i=0;i<ready.size();i++) {
                    Source source=ready.get(i);
                    long transferred=0;
                    try(InputStream stream=open(source)) {
                        int n;
                        while((n=stream.read(buf))!=-1) {
                            if(n==0) continue;
                            byte[] piece=Arrays.copyOf(buf,n);
                            byte[] encrypted=crypto.encrypt(piece);
                            output.writeInt(encrypted.length);
                            output.write(encrypted);
                            transferred+=n;
                        }
                    }
                    if(transferred!=source.size) throw new IllegalStateException(
                            "Source changed during backup: "+source.name);
                    show("STEP 3/3: Sent "+(i+1)+"/"+ready.size()+" files. Current: "+source.name);
                }
                output.flush();
            }
        }
        show("SENT "+ready.size()+" files. Check Windows to confirm verification. "+
             skipped.length()+" items/restrictions not backed up.");
    }
}
