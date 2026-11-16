$ErrorActionPreference = 'Stop'
$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
if (!(Test-Path -LiteralPath $chrome)) {
    throw "Chrome not found at $chrome"
}

$repoRoot = Split-Path $PSScriptRoot -Parent
$qaDir = Join-Path $repoRoot 'docs/evidence/visual-qa'
$artifactDir = 'C:\Users\tinht\.gemini\antigravity\brain\2db4042c-291e-4015-870b-611968c9032d'

if (!(Test-Path $qaDir)) {
    New-Item -ItemType Directory -Force $qaDir | Out-Null
}

$viewports = @(
    @{ Suffix = '1440'; Width = 1440; Height = 900; Desc = 'Desktop Wide' },
    @{ Suffix = '1366'; Width = 1366; Height = 768; Desc = 'Laptop Standard' },
    @{ Suffix = '390';  Width = 390;  Height = 844; Desc = 'Mobile Viewport iPhone' }
)

$pages = @(
    @{ Slug = 'home';            Url = 'http://127.0.0.1:5173/' },
    @{ Slug = 'dat-ve';          Url = 'http://127.0.0.1:5173/dat-ve' },
    @{ Slug = 'giai-phap';       Url = 'http://127.0.0.1:5173/giai-phap' },
    @{ Slug = 'bang-gia';        Url = 'http://127.0.0.1:5173/bang-gia' },
    @{ Slug = 'lien-he';         Url = 'http://127.0.0.1:5173/lien-he' },
    @{ Slug = 'customer-wallet'; Url = 'http://127.0.0.1:5173/ve-cua-toi?mockUser=customer' },
    @{ Slug = 'staff-portal';    Url = 'http://127.0.0.1:5173/staff?mockUser=admin' },
    @{ Slug = 'organizer-portal';Url = 'http://127.0.0.1:5173/organizer?mockUser=admin' },
    @{ Slug = 'admin-portal';    Url = 'http://127.0.0.1:5173/admin?mockUser=admin' },
    @{ Slug = 'payment-return';  Url = 'http://127.0.0.1:5173/payment-return' }
)

$total = $viewports.Count * $pages.Count
$idx = 0

Write-Host "=== STARTING VISUAL QA AUTOMATED SCREENSHOT SUITE ($total shots) ==="

foreach ($vp in $viewports) {
    Write-Host "`n--- Viewport: $($vp.Desc) ($($vp.Width)x$($vp.Height)) ---"
    foreach ($pg in $pages) {
        $idx++
        $fileName = "v2-$($pg.Slug)-$($vp.Suffix).png"
        $dest = Join-Path $qaDir $fileName
        $artifactDest = Join-Path $artifactDir $fileName

        $args = @(
            '--headless=new',
            '--disable-gpu',
            '--hide-scrollbars',
            '--virtual-time-budget=3000',
            "--window-size=$($vp.Width),$($vp.Height)",
            "--screenshot=$dest",
            $pg.Url
        )

        Write-Host "[$idx/$total] Capturing $fileName from $($pg.Url)..."
        $p = Start-Process -FilePath $chrome -ArgumentList $args -Wait -PassThru

        if (Test-Path $dest) {
            $len = (Get-Item $dest).Length
            Write-Host "  -> Saved $fileName ($len bytes)"
            Copy-Item -LiteralPath $dest -Destination $artifactDest -Force
        } else {
            Write-Warning "  -> FAILED to capture $fileName"
        }
    }
}

Write-Host "`n=== VISUAL QA SCREENSHOT CAPTURE SUITE COMPLETED SUCCESSFULLY ==="
