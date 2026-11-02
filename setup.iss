; Script for Inno Setup 6 - 今日收入 Windows 安装包
#define MyAppName "今日收入"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "AAJT11"
#define MyAppURL "https://github.com/AAJT11/-gold"
#define MyAppExeName "今日收入.exe"

[Setup]
AppId={{E57D8B9B-514A-4834-B27D-5A8E19B6F321}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion} (MAISON Luxury Edition)
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir=F:\工资\dist
OutputBaseFilename=今日收入-Windows-Setup-v1.0.0
SetupIconFile=F:\工资\app_icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern

CloseApplications=yes
CloseApplicationsFilter=*{#MyAppExeName}

[Languages]
Name: "chinesesimp"; MessagesFile: "F:\工资\ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "startupicon"; Description: "开机自动启动今日收入"; GroupDescription: "系统选项:"

[Files]
Source: "F:\工资\dist\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "F:\工资\app_icon.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "F:\工资\app_icon.png"; DestDir: "{app}"; Flags: ignoreversion
Source: "F:\工资\README.md"; DestDir: "{app}"; Flags: ignoreversion isreadme

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\app_icon.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\app_icon.ico"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startupicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
