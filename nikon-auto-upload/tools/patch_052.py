from pathlib import Path
import re

# ----- MainActivity: editable FTP credentials -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s = p.read_text()

marker = '        body.addView(camChoice);\n\n        sectionHeader("Camera Connection");'
insert = '''        body.addView(camChoice);

        sectionHeader("FTP Login");
        LinearLayout ftpLogin=cardBox();
        ftpLogin.addView(txt("Use these exact credentials in the camera FTP profile.",12,muted,false));
        EditText ftpUserEdit=input("FTP username",p.getString("ftp_user",DirectTransferService.DEFAULT_FTP_USER));
        ftpUserEdit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        ftpLogin.addView(ftpUserEdit,new LinearLayout.LayoutParams(-1,dp(52)));
        EditText ftpPassEdit=input("FTP password",p.getString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD));
        ftpPassEdit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        ftpLogin.addView(ftpPassEdit,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView ftpPort=txt("Port: 2121  •  Use 02121 when the camera requires five digits",12,muted,false);ftpLogin.addView(ftpPort);
        Button saveFtp=smallButton("SAVE FTP LOGIN & RESTART RECEIVER",green);
        saveFtp.setOnClickListener(v->{
            String u=clean(ftpUserEdit.getText().toString()),pw=ftpPassEdit.getText().toString().trim();
            if(u.isEmpty()){toast("FTP username cannot be blank");return;}
            if(pw.isEmpty()){toast("FTP password cannot be blank");return;}
            p.edit().putString("ftp_user",u).putString("ftp_password",pw).apply();
            sendServiceCommand("restart_receiver");
            toast("FTP login saved — receiver restarted");
            drawSettings();
        });
        ftpLogin.addView(saveFtp);
        Button resetFtp=smallButton("RESET FTP LOGIN TO DEFAULT",muted);
        resetFtp.setOnClickListener(v->{
            p.edit().putString("ftp_user",DirectTransferService.DEFAULT_FTP_USER).putString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD).apply();
            sendServiceCommand("restart_receiver");
            toast("Default FTP login restored");
            drawSettings();
        });
        ftpLogin.addView(resetFtp);
        body.addView(ftpLogin);

        sectionHeader("Camera Connection");'''
if marker not in s:
    raise SystemExit('0.5.2 could not locate camera settings insertion point')
s = s.replace(marker, insert, 1)

# Remove the old password-only Z8 FTP block so there is one camera-neutral FTP login editor.
s, n = re.subn(r'\s*sectionHeader\("Advanced Z8 FTP"\);LinearLayout ftp=cardBox\(\);.*?body\.addView\(ftp\);', '', s, count=1, flags=re.S)
if n != 1:
    raise SystemExit('0.5.2 could not remove legacy Advanced Z8 FTP block')

s = s.replace('Nikon Auto Upload 0.5.1', 'Nikon Auto Upload 0.5.2')
p.write_text(s)

# ----- DirectTransferService: use saved username + hot-restart server -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s = p.read_text()
s = s.replace('public static final String FTP_USER="nikon";', 'public static final String DEFAULT_FTP_USER="nikon";')
s = s.replace('if(!p.contains("flickr_upload_enabled"))e.putBoolean("flickr_upload_enabled",true);',
              'if(!p.contains("ftp_user"))e.putString("ftp_user",DEFAULT_FTP_USER);\n        if(!p.contains("flickr_upload_enabled"))e.putBoolean("flickr_upload_enabled",true);',1)
s = s.replace('new SimpleFtpServer(FTP_PORT,FTP_USER,p.getString("ftp_password",DEFAULT_FTP_PASSWORD),temp,this);',
              'new SimpleFtpServer(FTP_PORT,p.getString("ftp_user",DEFAULT_FTP_USER),p.getString("ftp_password",DEFAULT_FTP_PASSWORD),temp,this);')
s = s.replace('i.putExtra("ftp_user",FTP_USER);', 'i.putExtra("ftp_user",p.getString("ftp_user",DEFAULT_FTP_USER));')
needle = 'if("resume_receiver".equals(cmd)){resumeReceiver();return START_STICKY;}'
if needle not in s:
    raise SystemExit('0.5.2 could not locate service command block')
s = s.replace(needle, needle+'\n        if("restart_receiver".equals(cmd)){restartReceiver();return START_STICKY;}',1)
needle2 = '''    private synchronized void resumeReceiver(){
        p.edit().putBoolean("receiver_paused",false).putString("ftp_state","Waiting for camera").apply();startServerOnly();broadcast("Camera receiving resumed",findBestLocalIp());
    }'''
replacement2 = needle2 + '''
    private synchronized void restartReceiver(){
        p.edit().putBoolean("receiver_paused",false).putString("ftp_state","Restarting FTP receiver").apply();
        if(ftp!=null){ftp.stop();ftp=null;}
        startServerOnly();
        broadcast("FTP receiver restarted with updated login",findBestLocalIp());
    }'''
if needle2 not in s:
    raise SystemExit('0.5.2 could not locate resumeReceiver method')
s = s.replace(needle2,replacement2,1)
p.write_text(s)

# ----- MultiCameraSupport: display the current login in setup and stop hardcoding nikon -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MultiCameraSupport.java')
s = p.read_text()
repls = {
    'user nikon, password = Advanced FTP password':'user/password = the values shown in Settings > FTP Login',
    'use user nikon and the password shown in Advanced FTP':'use the FTP username and password shown in Settings > FTP Login',
    'port 2121, user nikon, Advanced FTP password':'port 2121, the username/password shown in Settings > FTP Login',
    'login user nikon, and the Advanced FTP password':'login with the username/password shown in Settings > FTP Login',
    'Passive mode Enable, user nikon, and the Advanced FTP password':'Passive mode Enable, and the username/password shown in Settings > FTP Login',
    'Secure Protocol Off, user nikon, and the Advanced FTP password':'Secure Protocol Off, and the username/password shown in Settings > FTP Login',
    'User Name & Password = nikon plus the Advanced FTP password':'User Name & Password = the values shown in Settings > FTP Login',
    'PASV Enable, user nikon, and the Advanced FTP password':'PASV Enable, and the username/password shown in Settings > FTP Login'
}
for a,b in repls.items(): s=s.replace(a,b)
old = 'String msg=x.setupSteps+accessory+"\\n\\nCurrent phone FTP address: "+ip+":2121";'
new = '''String user=p.getString("ftp_user",DirectTransferService.DEFAULT_FTP_USER),pass=p.getString("ftp_password",DirectTransferService.DEFAULT_FTP_PASSWORD);
        String msg=x.setupSteps+accessory+"\\n\\nCurrent phone FTP address: "+ip+":2121\\nFTP username: "+user+"\\nFTP password: "+pass;'''
if old not in s:
    raise SystemExit('0.5.2 could not locate setup credential text')
s = s.replace(old,new,1)
p.write_text(s)

# ----- In-app help: make username/password configurable wording -----
p = Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text()
    s=s.replace('Version 0.5.1 beta','Version 0.5.2 beta')
    s=s.replace('User: nikon','User: use the username shown in Settings > FTP Login')
    s=s.replace('Password: use the value shown in Settings > Advanced FTP','Password: use the password shown in Settings > FTP Login')
    s=s.replace('User must be nikon.','User must exactly match the username saved in Settings > FTP Login.')
    s=s.replace('user nikon','the configured FTP username')
    s=s.replace('user = nikon','user = the configured FTP username')
    s=s.replace('user/password = nikon + app Advanced FTP password','user/password = the values shown in Settings > FTP Login')
    p.write_text(s)
