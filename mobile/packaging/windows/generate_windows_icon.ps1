$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$sourcePath = Join-Path $projectRoot "store_assets\retailzw_logo_512.png"
$iconPath = Join-Path $projectRoot "windows\runner\resources\app_icon.ico"
$previewPath = Join-Path $projectRoot "store_assets\retailzw_logo_512.png"

function New-RetailZwIcon([int]$size) {
    $source = [System.Drawing.Image]::FromFile($sourcePath)
    $bitmap = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.DrawImage($source, 0, 0, $size, $size)
    $graphics.Dispose()
    $source.Dispose()
    return $bitmap
}

$preview = New-RetailZwIcon 512
$preview.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)
$preview.Dispose()

$iconBitmap = New-RetailZwIcon 256
$icon = [System.Drawing.Icon]::FromHandle($iconBitmap.GetHicon())
$stream = [System.IO.File]::Open($iconPath, [System.IO.FileMode]::Create)
$icon.Save($stream)
$stream.Dispose()
$icon.Dispose()
$iconBitmap.Dispose()

Write-Host "RetailZW icon generated: $iconPath" -ForegroundColor Green
