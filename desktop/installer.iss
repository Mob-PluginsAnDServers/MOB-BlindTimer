[Setup]
AppName=MOB Blind Timer
AppVersion=1.0
AppPublisher=MOB - Plugins & Servers
DefaultDirName={autopf}\MOB Blind Timer
DefaultGroupName=MOB Blind Timer
SetupIconFile=assets\icon.ico
UninstallDisplayIcon={app}\MOB-BlindTimer.exe
OutputDir=installer_out
OutputBaseFilename=MOB-BlindTimer-Setup
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=lowest
WizardStyle=modern

[Files]
Source: "dist\MOB-BlindTimer\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{group}\MOB Blind Timer"; Filename: "{app}\MOB-BlindTimer.exe"
Name: "{autodesktop}\MOB Blind Timer"; Filename: "{app}\MOB-BlindTimer.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Utwórz skrót na pulpicie"

[Run]
Filename: "{app}\MOB-BlindTimer.exe"; Description: "Uruchom MOB Blind Timer"; Flags: nowait postinstall skipifsilent
