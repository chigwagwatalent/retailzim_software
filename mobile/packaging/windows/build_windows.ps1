param(
    [string]$ApiBaseUrl = "https://admin.retailzw.co.zw",
    [string]$Version = "1.1.0",
    [string]$SigningCertificatePath = "",
    [Security.SecureString]$SigningCertificatePassword,
    [switch]$SkipInstaller,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$repoRoot = (Resolve-Path (Join-Path $projectRoot "..")).Path
$distDir = Join-Path $projectRoot "dist"
$releaseDir = Join-Path $projectRoot "build\windows\x64\runner\Release"
$installerPath = Join-Path $distDir "RetailZW-POS-Setup-$Version.exe"
$redistPath = Join-Path $repoRoot "REDISTRUTABLE\vc_redist.x64.exe"

function Resolve-Flutter {
    $candidates = @(
        "$env:USERPROFILE\.retailzw\toolchains\flutter-stable\bin\flutter.bat",
        (Get-Command flutter -ErrorAction SilentlyContinue).Source
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
    $flutter = $candidates | Select-Object -First 1
    if (-not $flutter) {
        throw "Flutter SDK was not found."
    }
    return $flutter
}

function Resolve-Iscc {
    $candidates = @(
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
        "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
        "C:\Program Files\Inno Setup 6\ISCC.exe"
    )
    return $candidates |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
}

function Assert-ValidMicrosoftRedist {
    if (-not (Test-Path -LiteralPath $redistPath)) {
        throw "VC++ x64 redistributable is missing: $redistPath"
    }
    $signature = Get-AuthenticodeSignature -LiteralPath $redistPath
    if (
        $signature.Status -ne "Valid" -or
        $signature.SignerCertificate.Subject -notmatch "Microsoft"
    ) {
        throw "The bundled VC++ redistributable does not have a valid Microsoft signature."
    }
}

function Ensure-AtlBuildDependency {
    $atlRoot = Join-Path $projectRoot "build\toolchain\atl"
    $header = Get-ChildItem (Join-Path $atlRoot "headers") `
        -Recurse -Filter "atlstr.h" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    $library = Get-ChildItem (Join-Path $atlRoot "x64") `
        -Recurse -Filter "atls.lib" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($header -and $library) {
        return
    }

    $packages = @(
        @{
            Name = "headers"
            Url = "https://download.visualstudio.microsoft.com/download/pr/a62262cf-b9f6-4770-9454-7372ad8fcec0/df87bd5da5bb3d101ca8238cc4d5f90d648336202c41f2ab80b5549b7ac495d1/Microsoft.VC.14.51.ATL.Headers.base.vsix"
            Sha256 = "df87bd5da5bb3d101ca8238cc4d5f90d648336202c41f2ab80b5549b7ac495d1"
        },
        @{
            Name = "x64"
            Url = "https://download.visualstudio.microsoft.com/download/pr/a62262cf-b9f6-4770-9454-7372ad8fcec0/6bf1c9950675fe9e5f46d861663ae19abcd18ce8e28602675194c32550ab3b04/Microsoft.VC.14.51.ATL.X64.base.vsix"
            Sha256 = "6bf1c9950675fe9e5f46d861663ae19abcd18ce8e28602675194c32550ab3b04"
        }
    )

    New-Item -ItemType Directory -Path $atlRoot -Force | Out-Null
    foreach ($package in $packages) {
        $vsixPath = Join-Path $atlRoot "$($package.Name).vsix"
        $zipPath = Join-Path $atlRoot "$($package.Name).zip"
        $extractPath = Join-Path $atlRoot $package.Name
        Invoke-WebRequest -UseBasicParsing -Uri $package.Url -OutFile $vsixPath
        $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $vsixPath).
            Hash.ToLowerInvariant()
        if ($actualHash -ne $package.Sha256) {
            throw "Microsoft ATL payload failed SHA-256 validation: $($package.Name)"
        }
        Copy-Item -LiteralPath $vsixPath -Destination $zipPath -Force
        Expand-Archive -LiteralPath $zipPath -DestinationPath $extractPath -Force
    }
}

function Invoke-CodeSigning([string[]]$Paths) {
    if ([string]::IsNullOrWhiteSpace($SigningCertificatePath)) {
        Write-Warning "No trusted Authenticode certificate supplied; binaries remain unsigned."
        return
    }
    if (-not (Test-Path -LiteralPath $SigningCertificatePath)) {
        throw "Signing certificate not found: $SigningCertificatePath"
    }
    if ($null -eq $SigningCertificatePassword) {
        throw "SigningCertificatePassword is required for the PFX certificate."
    }

    $certificate = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2(
        $SigningCertificatePath,
        $SigningCertificatePassword,
        [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet
    )
    foreach ($path in $Paths) {
        $signature = Set-AuthenticodeSignature `
            -LiteralPath $path `
            -Certificate $certificate `
            -HashAlgorithm SHA256 `
            -TimestampServer "http://timestamp.digicert.com"
        if ($signature.Status -notin @("Valid", "UnknownError")) {
            throw "Signing failed for ${path}: $($signature.StatusMessage)"
        }
    }
}

$flutter = Resolve-Flutter
Assert-ValidMicrosoftRedist
Ensure-AtlBuildDependency

Push-Location $projectRoot
try {
    powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $PSScriptRoot "generate_windows_icon.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "Windows icon generation failed."
    }

    & $flutter config --enable-windows-desktop | Out-Host
    & $flutter pub get | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Flutter dependency restore failed."
    }

    if (-not $SkipTests) {
        & $flutter analyze --no-fatal-infos | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Flutter analysis failed."
        }
        & $flutter test | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Flutter tests failed."
        }
    }

    $symbolDir = Join-Path $projectRoot "build\debug-symbols\$Version"
    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
    New-Item -ItemType Directory -Path $symbolDir -Force | Out-Null

    & $flutter build windows --release `
        --build-name=$Version `
        --build-number=3 `
        --obfuscate `
        --split-debug-info="$symbolDir" `
        --dart-define="RETAILZW_API_BASE_URL=$ApiBaseUrl" | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Flutter Windows release build failed with exit code $LASTEXITCODE."
    }
    if (-not (Test-Path -LiteralPath $releaseDir)) {
        throw "Windows release output was not created at $releaseDir."
    }

    Invoke-CodeSigning @((Join-Path $releaseDir "RetailZWPOS.exe"))

    if (-not $SkipInstaller) {
        $iscc = Resolve-Iscc
        if (-not $iscc) {
            throw "Inno Setup 6 is required to create the single-file installer."
        }
        & $iscc "/DMyAppVersion=$Version" `
            (Join-Path $PSScriptRoot "RetailZWPOS.iss") | Out-Host
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $installerPath)) {
            throw "Inno Setup failed to create $installerPath."
        }
        Invoke-CodeSigning @($installerPath)
        Write-Host "Production installer: $installerPath" -ForegroundColor Green
    }
}
finally {
    Pop-Location
}
