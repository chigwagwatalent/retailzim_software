#define MyAppName "RetailZW POS"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "RetailZW"
#define MyAppURL "https://retailzw.co.zw"
#define MyAppExeName "RetailZWPOS.exe"

[Setup]
AppId={{B7D98743-7C56-4D83-AC71-9CA524FCB1C8}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
DefaultDirName={autopf}\RetailZW POS
DefaultGroupName=RetailZW POS
DisableProgramGroupPage=yes
OutputDir=..\..\dist
OutputBaseFilename=RetailZW-POS-Setup-{#MyAppVersion}
SetupIconFile=..\..\windows\runner\resources\app_icon.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoCompany=RetailZW
VersionInfoDescription=RetailZW POS Installer
VersionInfoProductName=RetailZW POS
VersionInfoVersion={#MyAppVersion}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"; Flags: unchecked

[Files]
Source: "..\..\build\windows\x64\runner\Release\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\RetailZW POS"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\RetailZW POS"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch RetailZW POS"; Flags: nowait postinstall skipifsilent

[Code]
var
  ServerPage: TInputQueryWizardPage;

procedure InitializeWizard;
begin
  ServerPage := CreateInputQueryPage(
    wpSelectDir,
    'RetailZW Server',
    'Connect this till to your RetailZW backend',
    'Enter the full server URL used by this shop. For an internal network, include the server IP address and port, for example http://192.168.1.20:883.'
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
      MsgBox('Enter a complete URL beginning with http:// or https://.',
        mbError, MB_OK);
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
