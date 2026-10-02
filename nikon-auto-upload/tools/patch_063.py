from pathlib import Path

# 0.6.2: Nikon Z8 can return the 2004 Wi-Fi password field protected/non-ASCII.
# Never pass that ciphertext to Android's WPA2 builder. Accept a one-time camera
# Wi-Fi password override, keep the Bluetooth/Wi-Fi handoff automatic thereafter,
# and add defensive ASCII validation in the network layer.

p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/NikonBluetoothActivity.java')
s=p.read_text()

ui_marker='''        Button handoff=button("START / RETRY PHOTO HANDOFF",amber);handoff.setOnClickListener(v->requestWifiConfiguration());body.addView(handoff);\n\n        Button scan=button("SCAN FOR NIKON CAMERA",green);'''
ui_replacement='''        Button handoff=button("START / RETRY PHOTO HANDOFF",amber);handoff.setOnClickListener(v->requestWifiConfiguration());body.addView(handoff);

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

        Button scan=button("SCAN FOR NIKON CAMERA",green);'''
if ui_marker not in s: raise SystemExit('0.6.2 UI insertion target missing')
s=s.replace(ui_marker,ui_replacement,1)

old_parser='''        String ssid=asciiZ(data,1,32),password=asciiZ(data,33,64);
        int security=data[97]&255;
        String ip=(data[98]&255)+"."+(data[99]&255)+"."+(data[100]&255)+"."+(data[101]&255);
        p.edit().putString("nikon_handoff_ssid",ssid).putString("nikon_handoff_ip_hint",ip).putInt("nikon_handoff_security",security).apply();
        runOnUiThread(()->{
            append("[PHOTO] Nikon Wi-Fi credentials received: SSID="+ssid+" • security="+security+" • camera IP hint="+ip+" • password="+(password.isEmpty()?"none":"••••••••"));
            if(autoTransfer!=null)autoTransfer.startWifi(ssid,password,ip);
        });'''
new_parser='''        String parsedSsid=asciiZ(data,1,32),parsedPassword=asciiZ(data,33,64);
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
        });'''
if old_parser not in s: raise SystemExit('0.6.2 parser target missing')
s=s.replace(old_parser,new_parser,1)

old_ascii='''    private String asciiZ(byte[] data,int start,int len){
        int end=Math.min(data.length,start+len),n=start;while(n<end&&data[n]!=0)n++;
        return new String(data,start,Math.max(0,n-start),java.nio.charset.StandardCharsets.US_ASCII).trim();
    }'''
new_ascii='''    private String asciiZ(byte[] data,int start,int len){
        int end=Math.min(data.length,start+len),n=start;
        while(n<end&&data[n]!=0){int v=data[n]&255;if(v<0x20||v>0x7e)return null;n++;}
        if(n<=start)return "";
        return new String(data,start,n-start,java.nio.charset.StandardCharsets.US_ASCII);
    }
    private boolean isValidWifiPassphrase(String value){
        if(value==null||value.length()<8||value.length()>63)return false;
        return java.nio.charset.StandardCharsets.US_ASCII.newEncoder().canEncode(value);
    }'''
if old_ascii not in s: raise SystemExit('0.6.2 ascii helper target missing')
s=s.replace(old_ascii,new_ascii,1)

s=s.replace('Bluetooth stays connected for control. When Auto Photo Handoff is ON, the app reads the Nikon Wi-Fi credentials over Bluetooth, joins the camera Wi-Fi, opens PTP/IP, and pulls each NEW full-resolution JPEG to the phone. Flickr can keep using cellular.',
'''Bluetooth stays connected for control. When Auto Photo Handoff is ON, the app reads Nikon Wi-Fi information over Bluetooth, joins the camera Wi-Fi, opens PTP/IP, and pulls each NEW full-resolution JPEG to the phone. If Nikon protects the Wi-Fi password, enter the camera password once below; the app reuses it automatically after that.''',1)

p.write_text(s)

p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/NikonPtpAutoTransfer.java')
s=p.read_text()
needle='''        running.set(true);
        ptpStarted = false;'''
replacement='''        if (password != null && !password.isEmpty()) {
            boolean ascii = StandardCharsets.US_ASCII.newEncoder().canEncode(password);
            if (!ascii || password.length() < 8 || password.length() > 63) {
                log("Refusing invalid/non-ASCII Nikon Wi-Fi password before Android network request.");
                state("Camera Wi-Fi password needs to be entered once", true);
                prefs.edit().putString("nikon_handoff_state", "Camera Wi-Fi password needed once").apply();
                return;
            }
        }
        running.set(true);
        ptpStarted = false;'''
if needle not in s: raise SystemExit('0.6.2 network validation target missing')
s=s.replace(needle,replacement,1)
p.write_text(s)

p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text().replace('Nikon Auto Upload 0.6.1','Nikon Auto Upload 0.6.2')
p.write_text(s)

p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.6.1 beta','Version 0.6.2 beta')
    s += '''\n\n0.6.2 NIKON PROTECTED WI-FI PASSWORD FIX\n----------------------------------------\nSome Nikon cameras return a protected/binary password field in Bluetooth characteristic 2004. Android rejects that binary data as a WPA passphrase. The app now detects this safely instead of attempting to use it.\n\nWhen prompted, enter the camera's displayed Wi-Fi password one time in Nikon Bluetooth > CAMERA WI-FI PASSWORD — ONE-TIME FALLBACK, then tap SAVE PASSWORD & RETRY HANDOFF. The password is retained on the phone and reused for later automatic Bluetooth-to-Wi-Fi handoffs.\n'''
    p.write_text(s)
