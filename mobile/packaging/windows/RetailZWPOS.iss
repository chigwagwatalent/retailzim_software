#define MyAppName "RetailZW POS"
#ifndef MyAppVersion
  #define MyAppVersion "1.2.4"
#endif
#define MyAppPublisher "RetailZW"
#define MyAppURL "https://retailzw.co.zw"
#define MyAppExeName "RetailZWPOS.exe"

[Setup]
#ifdef InstallerSmokeTest
AppId=RetailZW-POS-Installer-Smoke-Test
DefaultDirName={localappdata}\RetailZW-Installer-Smoke-Test
PrivilegesRequired=lowest
#else
AppId={{B7D98743-7C56-4D83-AC71-9CA524FCB1C8}
DefaultDirName={autopf64}\RetailZW POS
PrivilegesRequired=admin
#endif
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultGroupName=RetailZW POS
DisableProgramGroupPage=yes
OutputDir=..\..\dist
OutputBaseFilename=RetailZW-POS-Setup-{#MyAppVersion}
SetupIconFile=..\..\windows\runner\resources\app_icon.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.17763
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName}
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=RetailZW POS Installer
VersionInfoProductName={#MyAppName}
VersionInfoVersion={#MyAppVersion}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"; Flags: unchecked

[Files]
Source: "..\..\build\windows\x64\runner\Release\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\..\..\REDISTRUTABLE\vc_redist.x64.exe"; Flags: dontcopy

[Icons]
#ifndef InstallerSmokeTest
Name: "{autoprograms}\RetailZW POS"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\RetailZW POS"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
#endif

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch RetailZW POS"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: files; Name: "{app}\retailzw-server.txt"
Type: dirifempty; Name: "{app}"

[Code]
var
  RuntimeRestartRequired: Boolean;

function RuntimeInstalled: Boolean;
var
  Installed, Major, Minor, Build: Cardinal;
  Key: String;
begin
  Key := 'SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64';
  Result := RegQueryDWordValue(HKLM64, Key, 'Installed', Installed) and (Installed = 1)
    and RegQueryDWordValue(HKLM64, Key, 'Major', Major)
    and RegQueryDWordValue(HKLM64, Key, 'Minor', Minor)
    and RegQueryDWordValue(HKLM64, Key, 'Bld', Build);
  if Result then
    Result := (Major > 14) or ((Major = 14) and ((Minor > 51) or ((Minor = 51) and (Build >= 36247))));
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ExitCode: Integer;
begin
  Result := '';
  if RuntimeInstalled then exit;
  ExtractTemporaryFile('vc_redist.x64.exe');
  if not Exec(ExpandConstant('{tmp}\vc_redist.x64.exe'), '/install /quiet /norestart',
      '', SW_HIDE, ewWaitUntilTerminated, ExitCode) then
  begin
    Result := 'Could not start the Microsoft Visual C++ runtime installer. Restart Windows and run setup again.';
    exit;
  end;
  if (ExitCode = 3010) or (ExitCode = 1641) then
    RuntimeRestartRequired := True
  else if (ExitCode <> 0) and not ((ExitCode = 1638) and RuntimeInstalled) then
    Result := Format('Microsoft Visual C++ runtime installation failed (code %d). Restart Windows and retry setup.', [ExitCode]);
end;

function NeedRestart: Boolean;
begin
  Result := RuntimeRestartRequired;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ConfigPath: String;
begin
  if CurStep = ssPostInstall then
  begin
    ConfigPath := ExpandConstant('{app}\retailzw-server.txt');
    { Preserve shop-specific settings on upgrade; configure new tills automatically. }
    if not FileExists(ConfigPath) then
      if not SaveStringToFile(ConfigPath, 'https://admin.retailzw.co.zw', False) then
        RaiseException('Could not save the server configuration. Please run setup again.');
  end;
end;
