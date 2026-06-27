param(
    [string]$ApiBaseUrl = "https://admin.retailzw.co.zw",
    [switch]$SkipInstaller
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$distDir = Join-Path $projectRoot "dist"
$releaseDir = Join-Path $projectRoot "build\windows\x64\runner\Release"
$portableDir = Join-Path $distDir "RetailZW-POS"
$zipPath = Join-Path $distDir "RetailZW-POS-Portable-1.0.0.zip"

Push-Location $projectRoot
try {
    & (Join-Path $PSScriptRoot "generate_windows_icon.ps1")
    flutter config --enable-windows-desktop | Out-Host
    flutter pub get | Out-Host
    flutter build windows --release `
        --dart-define="RETAILZW_API_BASE_URL=$ApiBaseUrl" | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Flutter Windows release build failed with exit code $LASTEXITCODE."
    }

    if (-not (Test-Path $releaseDir)) {
        throw "Windows release output was not created at $releaseDir."
    }

    if (Test-Path $portableDir) {
        Remove-Item $portableDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $portableDir -Force | Out-Null
    Copy-Item (Join-Path $releaseDir "*") $portableDir -Recurse -Force

    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $portableDir "*") -DestinationPath $zipPath -CompressionLevel Optimal
    Write-Host "Portable Windows application: $zipPath" -ForegroundColor Green

    if (-not $SkipInstaller) {
        $innoCandidates = @(
            "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
            "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
            "C:\Program Files\Inno Setup 6\ISCC.exe"
        )
        $iscc = $innoCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
        if ($iscc) {
            & $iscc (Join-Path $PSScriptRoot "RetailZWPOS.iss") | Out-Host
            Write-Host "Installer created in $distDir" -ForegroundColor Green
        } else {
            Write-Warning "Inno Setup 6 is not installed. Portable ZIP was created; install Inno Setup and rerun for a Setup EXE."
        }
    }
}
finally {
    Pop-Location
}
