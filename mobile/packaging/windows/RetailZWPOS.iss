#define MyAppName "RetailZW POS"
#ifndef MyAppVersion
  #define MyAppVersion "1.1.0"
#endif
#define MyAppPublisher "RetailZW"
#define MyAppURL "https://retailzw.co.zw"
#define MyAppExeName "RetailZWPOS.exe"

[Setup]
AppId={{B7D98743-7C56-4D83-AC71-9CA524FCB1C8}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf64}\RetailZW POS
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
PrivilegesRequired=admin
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
Source: "..\..\..\REDISTRUTABLE\vc_redist.x64.exe"; DestDir: "{tmp}"; DestName: "vc_redist.x64.exe"; Flags: deleteafterinstall

[Icons]
Name: "{autoprograms}\RetailZW POS"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\RetailZW POS"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{tmp}\vc_redist.x64.exe"; Parameters: "/install /quiet /norestart"; StatusMsg: "Installing Microsoft Visual C++ Runtime..."; Flags: runhidden waituntilterminated
Filename: "{app}\{#MyAppExeName}"; Description: "Launch RetailZW POS"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: files; Name: "{app}\retailzw-server.txt"
Type: dirifempty; Name: "{app}"

[Code]
var
  ServerPage: TInputQueryWizardPage;

procedure InitializeWizard;
begin
  ServerPage := CreateInputQueryPage(
    wpSelectDir,
    'RetailZW Server',
    'Connect this till to your RetailZW backend',
    'Enter the full server URL used by this shop. The production URL is already selected.'
  );
  ServerPage.Add('Server base URL:', False);
  ServerPage.Values[0] := 'https://admin.retailzw.co.zw';
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Value: String;
  LowerValue: String;
begin
  Result := True;
  if CurPageID = ServerPage.ID then
  begin
    Value := Trim(ServerPage.Values[0]);
    LowerValue := Lowercase(Value);
    if (Value = '') or
       ((Pos('http://', LowerValue) <> 1) and
        (Pos('https://', LowerValue) <> 1)) then
    begin
      MsgBox(
        'Enter a complete URL beginning with http:// or https://.',
        mbError,
        MB_OK
      );
      Result := False;
    end;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
    SaveStringToFile(
      ExpandConstant('{app}\retailzw-server.txt'),
      Trim(ServerPage.Values[0]),
      False
    );
end;
