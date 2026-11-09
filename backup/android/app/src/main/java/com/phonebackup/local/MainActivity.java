package com.phonebackup.local;

import android.Manifest;
import android.app.Activity;
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
        Crypto(String code, byte[] salt) throws Exception {
            this.salt = salt;
            SecretKeyFactory factory=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec=new PBEKeySpec(code.toCharArray(),salt,180000,256);
            byte[] raw=factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            key=new SecretKeySpec(raw,"AES");
            Arrays.fill(raw,(byte)0);
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
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30,24,30,20);
        TextView intro=new TextView(this);
        intro.setText("Wireless Phone Backup 0.2 — the phone and Windows PC must share Wi-Fi.");
        layout.addView(intro);
        host=new EditText(this);
        host.setHint("Windows computer IP address");
        host.setSingleLine(true);
        layout.addView(host);
        pairingCode=new EditText(this);
        pairingCode.setHint("One-time pairing code shown on Windows");
        pairingCode.setSingleLine(true);
        layout.addView(pairingCode);
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
        status.setText("Select files if desired, then click Backup Now.");
        layout.addView(status);
        setContentView(layout);
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

    private void startBackup() {
        ArrayList<String> ask=new ArrayList<>();
        if(sms.isChecked() && checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)
            ask.add(Manifest.permission.READ_SMS);
        if(contacts.isChecked() && checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)
            ask.add(Manifest.permission.READ_CONTACTS);
        if(!ask.isEmpty()) {
            requestPermissions(ask.toArray(new String[0]),PERMISSIONS);
            return;
        }
        if(host.getText().toString().trim().length()==0 || pairingCode.getText().toString().trim().length()<12) {
            show("Enter the Windows IP address and full pairing code before starting.");
            return;
        }
        final String ip=host.getText().toString().trim();
        final String code=pairingCode.getText().toString().trim();
        final boolean includeApps=apps.isChecked();
        final boolean includeMessages=sms.isChecked();
        final boolean includeContacts=contacts.isChecked();
        backupButton.setEnabled(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(()->{
            try { transfer(ip,code,includeApps,includeMessages,includeContacts); }
            catch(Exception e) { show("Backup FAILED: "+e.getClass().getSimpleName()+": "+e.getMessage()); }
            finally {
                runOnUiThread(()->{
                    backupButton.setEnabled(true);
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
            }
        }).start();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==PERMISSIONS) startBackup();
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
        show("Located "+count+" accessible app APK files. Preparing checksums...");
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
                if(ready.size()%10==0) show("Prepared "+ready.size()+" files for backup...");
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
        Crypto crypto=new Crypto(code,salt);
        byte[] encryptedManifest=crypto.encrypt(manifest.toString().getBytes(StandardCharsets.UTF_8));
        show("Connecting to Windows... "+ready.size()+" files to transfer");
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress(ip,PORT),15000);
            socket.setSoTimeout(120000);
            try(DataOutputStream output=new DataOutputStream(socket.getOutputStream())) {
                output.write(new byte[]{'W','P','B','2'});
                output.write(salt);
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
                    show("Sent "+(i+1)+"/"+ready.size()+": "+source.name);
                }
                output.flush();
            }
        }
        show("All "+ready.size()+" files sent. Check Windows for verified backup report. "+
             skipped.length()+" items/restrictions not backed up.");
    }
}
