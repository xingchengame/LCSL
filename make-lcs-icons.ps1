Add-Type -AssemblyName System.Drawing

$root = "D:\lucunserverl\HMCL-main"
$srcFile = Join-Path $root "logo.jpg"
$imgDir = Join-Path $root "LCSL\src\main\resources\assets\img"
$backup = Join-Path $root "backup-icons-20261023"

New-Item -ItemType Directory -Force -Path $backup | Out-Null

$src = [System.Drawing.Image]::FromFile($srcFile)
Write-Host "source: $($src.Width) x $($src.Height)"
Write-Host ("corner(0,0)   = " + $src.GetPixel(0,0))
Write-Host ("corner(last)  = " + $src.GetPixel($src.Width-1,$src.Height-1))
Write-Host ("center        = " + $src.GetPixel([int]($src.Width/2),[int]($src.Height/2)))
Write-Host ("edge-top      = " + $src.GetPixel([int]($src.Width/2),1))

function New-CircleIcon {
    param([int]$Size)
    $bmp = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)
    $pad = [Math]::Round($Size * 0.015)
    $d = $Size - 2 * $pad
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse($pad, $pad, $d, $d)
    $g.SetClip($path)
    $g.DrawImage($src, 0, 0, $Size, $Size)
    $g.ResetClip()
    $g.Dispose()
    $path.Dispose()
    return $bmp
}

$targets = @(
    @{ name = "icon.png";           size = 32 },
    @{ name = "icon@2x.png";        size = 64 },
    @{ name = "icon@4x.png";        size = 128 },
    @{ name = "icon-title.png";     size = 24 },
    @{ name = "icon-title@2x.png";  size = 48 },
    @{ name = "icon-mac.png";       size = 512 }
)

foreach ($t in $targets) {
    $old = Join-Path $imgDir $t.name
    if ((Test-Path $old) -and -not (Test-Path (Join-Path $backup $t.name))) {
        Copy-Item $old (Join-Path $backup $t.name)
    }
    $icon = New-CircleIcon -Size $t.size
    $icon.Save($old, [System.Drawing.Imaging.ImageFormat]::Png)
    $icon.Dispose()
    Write-Host ("wrote " + $t.name + " (" + $t.size + "px)")
}

# icon@8x.png keeps the logo as it is, at full resolution
$bigIcon = Join-Path $imgDir "icon@8x.png"
if ((Test-Path $bigIcon) -and -not (Test-Path (Join-Path $backup "icon@8x.png"))) {
    Copy-Item $bigIcon (Join-Path $backup "icon@8x.png")
}
$full = New-Object System.Drawing.Bitmap($src)
$full.Save($bigIcon, [System.Drawing.Imaging.ImageFormat]::Png)
$full.Dispose()
Write-Host ("wrote icon@8x.png (" + $src.Width + "px)")

$src.Dispose()
Write-Host "backup at $backup"
