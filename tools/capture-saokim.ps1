$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$qaDir = 'D:\event-ticketing-platform\docs\evidence\visual-qa'
$artifactDir = 'C:\Users\tinht\.gemini\antigravity\brain\2db4042c-291e-4015-870b-611968c9032d'

if (!(Test-Path $qaDir)) {
    New-Item -ItemType Directory -Force $qaDir | Out-Null
}

$targets = @(
    @{
        Name = 'saokim-hero-1440.png'
        Url = 'http://127.0.0.1:5173/'
        Width = 1440
        Height = 900
    },
    @{
        Name = 'saokim-mega-menu-solutions-1440.png'
        Url = 'http://127.0.0.1:5173/?openDropdown=solutions'
        Width = 1440
        Height = 900
    },
    @{
        Name = 'saokim-mega-menu-industries-1440.png'
        Url = 'http://127.0.0.1:5173/?openDropdown=industries'
        Width = 1440
        Height = 900
    },
    @{
        Name = 'saokim-floating-dock-1440.png'
        Url = 'http://127.0.0.1:5173/'
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
        '--virtual-time-budget=2000',
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

Write-Host "All Sao Kim makeover screenshots captured successfully!"
