#define MyAppName "Camera Auto Upload"
#define MyAppVersion "1.0.4"
#define MyAppPublisher "Camera Auto Upload"
#define MyAppExeName "CameraAutoUpload.Windows.exe"

[Setup]
AppId={{6D32D9F0-9AB0-4FCB-A6E6-BF7E8D8DF22B}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\Camera Auto Upload
DefaultGroupName=Camera Auto Upload
DisableProgramGroupPage=yes
OutputDir=installer-output
OutputBaseFilename=CameraAutoUpload-Windows-Setup-1.0.4
SetupIconFile=app.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
SetupLogging=yes
CloseApplications=yes
RestartApplications=no

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "..\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Camera Auto Upload"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\Camera Auto Upload"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch Camera Auto Upload"; Flags: nowait postinstall skipifsilent
