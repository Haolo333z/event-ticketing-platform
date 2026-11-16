$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$qaDir = 'D:\event-ticketing-platform\docs\evidence\visual-qa'
$artifactDir = 'C:\Users\tinht\.gemini\antigravity\brain\2db4042c-291e-4015-870b-611968c9032d'

if (!(Test-Path $qaDir)) {
    New-Item -ItemType Directory -Force $qaDir | Out-Null
}

$targets = @(
    @{
        Name = 'multipage-home-1440.png'
        Url = 'http://127.0.0.1:5173/'
        Width = 1440
        Height = 900
    },
    @{
        Name = 'multipage-dat-ve-1440.png'
        Url = 'http://127.0.0.1:5173/dat-ve'
        Width = 1440
        Height = 900
    },
    @{
        Name = 'multipage-giai-phap-1440.png'
        Url = 'http://127.0.0.1:5173/giai-phap'
        Width = 1440
        Height = 900
    },
    @{
        Name = 'multipage-bang-gia-1440.png'
        Url = 'http://127.0.0.1:5173/bang-gia'
        Width = 1440
        Height = 900
    },
    @{
        Name = 'multipage-ve-cua-toi-1440.png'
        Url = 'http://127.0.0.1:5173/ve-cua-toi?mockUser=customer'
        Width = 1440
        Height = 900
    },
    @{
        Name = 'multipage-lien-he-1440.png'
        Url = 'http://127.0.0.1:5173/lien-he'
        Width = 1440
        Height = 900
    },
    @{
        Name = 'multipage-admin-1440.png'
        Url = 'http://127.0.0.1:5173/admin?mockUser=admin'
        Width = 1440
        Height = 900
    }
)

foreach ($t in $targets) {
    $dest = Join-Path $qaDir $t.Name
    $artifactDest = Join-Path $artifactDir $t.Name
    $args = @(
        '--headless=new',
        '--disable-gpu',
        '--virtual-time-budget=2500',
        "--window-size=$($t.Width),$($t.Height)",
        "--screenshot=$dest",
        $t.Url
    )
    Write-Host "Capturing $($t.Name) from $($t.Url)..."
    $p = Start-Process -FilePath $chrome -ArgumentList $args -Wait -PassThru
    if (Test-Path $dest) {
        $len = (Get-Item $dest).Length
        Write-Host "Saved $dest ($len bytes)"
        Copy-Item -LiteralPath $dest -Destination $artifactDest -Force
        Write-Host "Copied to $artifactDest"
    } else {
        Write-Error "Failed to capture $dest"
    }
}

Write-Host "All Multi-page screenshots captured successfully!"
