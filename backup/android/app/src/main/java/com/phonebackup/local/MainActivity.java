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
    private EditText host, pairingCode;
    private TextView status;
    private CheckBox apps, sms, contacts;
    private Button backupButton;
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
        ScrollView screen=new ScrollView(this);
        backupScreen=screen;
        screen.setFillViewport(true);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32,30,32,36);
        screen.addView(layout);
        TextView intro=new TextView(this);
        intro.setText("WIRELESS PHONE BACKUP 0.5\n1. On Windows, click BACKUP NOW.\n2. On the phone, tap FIND COMPUTER, enter the six-digit code, then BACKUP NOW.");
        layout.addView(intro);
        host=new EditText(this);
        host.setHint("Windows computer IP (or use Find Computer)");
        host.setSingleLine(true);
        layout.addView(host);
        pairingCode=new EditText(this);
        pairingCode.setHint("Six-digit code from Windows");
        pairingCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        pairingCode.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        pairingCode.setSingleLine(true);
        layout.addView(pairingCode);
        Button find=new Button(this);
        find.setText("FIND WINDOWS COMPUTER");
        layout.addView(find);
        find.setOnClickListener(v->discover());
        Button choose=new Button(this);
        choose.setText("Choose photos, videos and documents");
        layout.addView(choose);
        choose.setOnClickListener(view->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(intent,PICK);
        });
        apps=new CheckBox(this);
        apps.setText("Backup actual installed app APKs (including split APKs)");
        apps.setChecked(true);
        layout.addView(apps);
        sms=new CheckBox(this);
        sms.setText("Backup SMS/MMS messages and available attachments");
        sms.setChecked(true);
        layout.addView(sms);
        contacts=new CheckBox(this);
        contacts.setText("Backup contacts");
        contacts.setChecked(true);
        layout.addView(contacts);
        backupButton=new Button(this);
        backupButton.setText("Backup Now");
        layout.addView(backupButton);
        status=new TextView(this);
        status.setText("READY. First press BACKUP NOW on Windows. Then FIND WINDOWS COMPUTER. Screen will stay on during backup.");
        layout.addView(status);
        for(int i=0;i<layout.getChildCount();i++) {
            android.view.View view=layout.getChildAt(i);
            if(view instanceof TextView) {
                TextView label=(TextView)view;
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, view==intro?21:19);
                label.setPadding(8,12,8,12);
                label.setMinHeight(62);
            }
            if(view instanceof Button) ((Button)view).setTextSize(TypedValue.COMPLEX_UNIT_SP,21);
        }
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP,20);
        setContentView(screen);
        backupButton.setOnClickListener(v->startBackup());
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
    private boolean checkReady(String ip) {
        if(ip==null || ip.trim().length()==0) return false;
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress(ip.trim(),38473),500);
            socket.setSoTimeout(800);
            byte[] query="WPB_STATUS_V4".getBytes(StandardCharsets.US_ASCII);
            socket.getOutputStream().write(query);
            socket.getOutputStream().flush();
            byte[] reply=new byte[12];
            int length=0;
            while(length<reply.length) {
                int n=socket.getInputStream().read(reply,length,reply.length-length);
                if(n<0) return false;
                length+=n;
            }
            return "WPB_READY_V4".equals(new String(reply,StandardCharsets.US_ASCII));
        } catch(Exception e) {
            return false;
        }
    }

    private String broadcastDiscovery() {
        try(DatagramSocket udp=new DatagramSocket()) {
            udp.setBroadcast(true);
            udp.setSoTimeout(1000);
            byte[] query="WPB_DISCOVER_V4".getBytes(StandardCharsets.US_ASCII);
            HashSet<InetAddress> addresses=new HashSet<>();
            addresses.add(InetAddress.getByName("255.255.255.255"));
            for(Enumeration<NetworkInterface> it=NetworkInterface.getNetworkInterfaces();it!=null&&it.hasMoreElements();) {
                NetworkInterface network=it.nextElement();
                if(!network.isUp()||network.isLoopback()) continue;
                for(InterfaceAddress networkAddress:network.getInterfaceAddresses()) {
                    if(networkAddress.getBroadcast()!=null)
                        addresses.add(networkAddress.getBroadcast());
                }
            }
            for(InetAddress address:addresses) {
                try { udp.send(new DatagramPacket(query,query.length,address,38472)); }
                catch(Exception ignored) {}
            }
            long end=System.currentTimeMillis()+1500;
            while(System.currentTimeMillis()<end) {
                try {
                    byte[] buf=new byte[64];
                    DatagramPacket packet=new DatagramPacket(buf,buf.length);
                    udp.receive(packet);
                    if("WPB_READY_V4".equals(new String(buf,0,packet.getLength(),StandardCharsets.US_ASCII)))
                        return packet.getAddress().getHostAddress();
                } catch(SocketTimeoutException timeout) { break; }
            }
        } catch(Exception ignored) {}
        return null;
    }

    private String scanLocalSubnet() {
        ArrayList<String> candidates=new ArrayList<>();
        try {
            HashSet<String> prefixes=new HashSet<>();
            for(Enumeration<NetworkInterface> it=NetworkInterface.getNetworkInterfaces();it!=null&&it.hasMoreElements();) {
                NetworkInterface network=it.nextElement();
                if(!network.isUp() || network.isLoopback()) continue;
                for(InterfaceAddress interfaceAddress:network.getInterfaceAddresses()) {
                    InetAddress addr=interfaceAddress.getAddress();
                    if(!(addr instanceof Inet4Address)) continue;
                    String[] parts=addr.getHostAddress().split("\\.");
                    if(parts.length==4) {
                        String prefix=parts[0]+"."+parts[1]+"."+parts[2]+".";
                        // Scan only private LAN addresses on an explicit Find Computer tap.
                        int a=Integer.parseInt(parts[0]);
                        int b=Integer.parseInt(parts[1]);
                        if(a==10 || (a==172&&b>=16&&b<=31) || (a==192&&b==168))
                            prefixes.add(prefix);
                    }
                }
            }
            for(String prefix:prefixes) for(int n=1;n<=254;n++)
                candidates.add(prefix+n);
        } catch(Exception ignored) {}
        if(candidates.isEmpty()) return null;
        show("Computer search: checking the local Wi-Fi network. Please wait...");
        ExecutorService pool=Executors.newFixedThreadPool(24);
        ExecutorCompletionService<String> completed=new ExecutorCompletionService<>(pool);
        try {
            for(String candidate:candidates) completed.submit(
                ()->checkReady(candidate)?candidate:null);
            long deadline=System.currentTimeMillis()+11000;
            for(int n=0;n<candidates.size() && System.currentTimeMillis()<deadline;n++) {
                long remaining=deadline-System.currentTimeMillis();
                Future<String> result=completed.poll(remaining,TimeUnit.MILLISECONDS);
                if(result==null) break;
                String ip=result.get();
                if(ip!=null) return ip;
            }
        } catch(Exception ignored) {
        } finally {
            pool.shutdownNow();
        }
        return null;
    }

    private void discover() {
        final String typedIp=host.getText().toString().trim();
        show("FINDING Windows computer... On Windows click BACKUP NOW first.");
        new Thread(()->{
            String ip=null;
            try {
                if(checkReady(typedIp)) ip=typedIp;
                if(ip==null) ip=broadcastDiscovery();
                if(ip==null) ip=scanLocalSubnet();
                if(ip!=null) {
                    final String found=ip;
                    runOnUiThread(()->host.setText(found));
                    show("CONNECTED: Found the Windows receiver at "+found+". Enter its 6-digit code and tap BACKUP NOW.");
                } else {
                    show("NO WINDOWS RECEIVER FOUND. On Windows: connect your drive, choose its folder, press BACKUP NOW, and allow Windows Firewall access on PRIVATE networks. Then try Find again.");
                }
            } catch(Exception e) {
                show("Computer search failed: "+e.getMessage()+". You can enter the Windows IPv4 address manually.");
            }
        }).start();
    }

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
            show("Grant the requested message/contact permissions or turn their checkboxes off.");
            requestPermissions(ask.toArray(new String[0]),PERMISSIONS);
            return;
        }
        if(host.getText().toString().trim().length()==0 || !pairingCode.getText().toString().trim().matches("[0-9]{6}")) {
            show("Enter the Windows computer IP address and the 6-digit pairing code.");
            return;
        }
        final String ip=host.getText().toString().trim();
        final String code=pairingCode.getText().toString().trim();
        final boolean includeApps=apps.isChecked();
        final boolean includeMessages=sms.isChecked();
        final boolean includeContacts=contacts.isChecked();
        backupButton.setEnabled(false);
        keepPhoneAwakeForBackup();
        show("BACKUP RUNNING: Phone screen, CPU and Wi-Fi are being kept awake until backup finishes. STEP 1/4: Starting...");
        new Thread(()->{
            try { transfer(ip,code,includeApps,includeMessages,includeContacts); }
            catch(Exception e) { show("Backup FAILED: "+e.getClass().getSimpleName()+": "+e.getMessage()); }
            finally {
                runOnUiThread(()->{
                    finishBackup();
                    backupButton.setEnabled(true);
                });
            }
        }).start();
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

    private void transfer(String ip,String code,boolean includeApps,boolean includeMessages,
                          boolean includeContacts) throws Exception {
        show("STEP 1/4: Checking whether Windows backup is ready...");
        if(!checkReady(ip))
            throw new java.io.IOException("Windows app is not listening at "+ip+". On Windows: connect your external drive, select its folder, and click BACKUP NOW. If it still fails, allow the backup app on PRIVATE networks in Windows Firewall.");
        show("STEP 2/4: Windows is ready. Scanning your phone files...");
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
        show("STEP 3/4: Connecting to Windows for "+ready.size()+" files...");
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress(ip,PORT),15000);
            socket.setSoTimeout(120000);
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
                    show("STEP 4/4: Sent "+(i+1)+"/"+ready.size()+" files. Current: "+source.name);
                }
                output.flush();
            }
        }
        show("SENT "+ready.size()+" files. Check Windows to confirm verification. "+
             skipped.length()+" items/restrictions not backed up.");
    }
}
