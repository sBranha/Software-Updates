#define MyAppName "Wireless Phone Backup"
#define MyAppVersion "0.1.0"
[Setup]
AppId=WirelessPhoneBackupLocal
AppName={#MyAppName}
AppVersion={#MyAppVersion}
DefaultDirName={localappdata}\Programs\WirelessPhoneBackup
DefaultGroupName={#MyAppName}
OutputDir=..\..\dist
OutputBaseFilename=WirelessPhoneBackup-Setup
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
UninstallDisplayIcon={app}\WirelessPhoneBackup.exe
[Files]
Source: "..\..\dist\WirelessPhoneBackup.exe"; DestDir: "{app}"; Flags: ignoreversion
[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\WirelessPhoneBackup.exe"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\WirelessPhoneBackup.exe"; Tasks: desktopicon
[Tasks]
Name: "desktopicon"; Description: "Create desktop shortcut"; Flags: unchecked
[Run]
Filename: "{app}\WirelessPhoneBackup.exe"; Description: "Launch Wireless Phone Backup"; Flags: nowait postinstall skipifsilent
