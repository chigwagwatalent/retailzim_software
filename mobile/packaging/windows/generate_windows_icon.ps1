$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$sourcePath = Join-Path $projectRoot "assets\images\retailzw_logo.png"
$masterPath = Join-Path $projectRoot "store_assets\app_icon_512.png"
$iconPath = Join-Path $projectRoot "windows\runner\resources\app_icon.ico"

if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "RetailZW logo asset was not found at $sourcePath."
}

function New-RoundedRectanglePath(
    [System.Drawing.RectangleF]$Rectangle,
    [float]$Radius
) {
    $diameter = $Radius * 2
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc($Rectangle.X, $Rectangle.Y, $diameter, $diameter, 180, 90)
    $path.AddArc(
        $Rectangle.Right - $diameter,
        $Rectangle.Y,
        $diameter,
        $diameter,
        270,
        90
    )
    $path.AddArc(
        $Rectangle.Right - $diameter,
        $Rectangle.Bottom - $diameter,
        $diameter,
        $diameter,
        0,
        90
    )
    $path.AddArc(
        $Rectangle.X,
        $Rectangle.Bottom - $diameter,
        $diameter,
        $diameter,
        90,
        90
    )
    $path.CloseFigure()
    return $path
}

function New-RetailZwMasterIcon {
    $logo = [System.Drawing.Bitmap]::FromFile($sourcePath)
    $master = New-Object System.Drawing.Bitmap 512, 512, (
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($master)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = (
        [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    )
    $graphics.PixelOffsetMode = (
        [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    )
    $graphics.Clear([System.Drawing.Color]::Transparent)

    $backgroundRect = New-Object System.Drawing.RectangleF 8, 8, 496, 496
    $backgroundPath = New-RoundedRectanglePath $backgroundRect 108
    $backgroundBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush (
        $backgroundRect,
        [System.Drawing.Color]::FromArgb(255, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(255, 229, 245, 255),
        45
    )
    $graphics.FillPath($backgroundBrush, $backgroundPath)
    $borderPen = New-Object System.Drawing.Pen (
        [System.Drawing.Color]::FromArgb(255, 18, 189, 240),
        10
    )
    $graphics.DrawPath($borderPen, $backgroundPath)

    # Use the exact supplied RetailZW wordmark without redrawing or distortion.
    $sourceRect = New-Object System.Drawing.Rectangle 0, 0, (
        $logo.Width
    ), $logo.Height
    $destinationRect = New-Object System.Drawing.Rectangle 38, 174, 436, 164
    $graphics.DrawImage(
        $logo,
        $destinationRect,
        $sourceRect,
        [System.Drawing.GraphicsUnit]::Pixel
    )

    $borderPen.Dispose()
    $backgroundBrush.Dispose()
    $backgroundPath.Dispose()
    $graphics.Dispose()
    $logo.Dispose()
    return $master
}

function Convert-BitmapToPngBytes(
    [System.Drawing.Bitmap]$Source,
    [int]$Size
) {
    $bitmap = New-Object System.Drawing.Bitmap $Size, $Size, (
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = (
        [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    )
    $graphics.PixelOffsetMode = (
        [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    )
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.DrawImage($Source, 0, 0, $Size, $Size)
    $graphics.Dispose()

    $stream = New-Object System.IO.MemoryStream
    $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
    $bytes = $stream.ToArray()
    $stream.Dispose()
    return $bytes
}

$master = New-RetailZwMasterIcon
$master.Save($masterPath, [System.Drawing.Imaging.ImageFormat]::Png)

$sizes = @(16, 20, 24, 32, 40, 48, 64, 128, 256)
$frames = foreach ($size in $sizes) {
    [PSCustomObject]@{
        Size = $size
        Data = Convert-BitmapToPngBytes $master $size
    }
}
$master.Dispose()

$iconStream = [System.IO.File]::Open(
    $iconPath,
    [System.IO.FileMode]::Create,
    [System.IO.FileAccess]::Write
)
$writer = New-Object System.IO.BinaryWriter $iconStream
try {
    $writer.Write([UInt16]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]$frames.Count)

    [UInt32]$offset = 6 + (16 * $frames.Count)
    foreach ($frame in $frames) {
        $dimension = if ($frame.Size -eq 256) { 0 } else { $frame.Size }
        $writer.Write([Byte]$dimension)
        $writer.Write([Byte]$dimension)
        $writer.Write([Byte]0)
        $writer.Write([Byte]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]$frame.Data.Length)
        $writer.Write([UInt32]$offset)
        $offset += [UInt32]$frame.Data.Length
    }
    foreach ($frame in $frames) {
        $writer.Write([Byte[]]$frame.Data)
    }
}
finally {
    $writer.Dispose()
    $iconStream.Dispose()
}

Write-Host "RetailZW icon generated: $iconPath" -ForegroundColor Green
Write-Host "Brand preview generated: $masterPath" -ForegroundColor Green
