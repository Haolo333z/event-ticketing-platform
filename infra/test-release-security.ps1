$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$output=$env:TICKETING_AUDIT_OUTPUT
if(!$output -or $env:TICKETING_AUDIT_PROJECT -notmatch '^ticketing-audit-[a-f0-9]{12}$'){throw 'Run through test-invariants.ps1 -WorkingTree'}
$tools=Join-Path (Split-Path $output -Parent) 'tools'
New-Item -ItemType Directory -Force $tools|Out-Null
$result=[ordered]@{status='RUNNING';tools=@();checks=@();limitations=@('actionlint on Windows: shellcheck and pyflakes disabled; this is not a GitHub-hosted Linux run')}
function Invoke-Check($name,$exe,[string[]]$arguments){
    $p=Start-Process -FilePath $exe -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $output "$name.stdout.log") -RedirectStandardError (Join-Path $output "$name.stderr.log")
    $null=$p.Handle;$p.WaitForExit();$p.Refresh()
    $script:result.checks+=@{name=$name;exitCode=$p.ExitCode}
    if($p.ExitCode -ne 0){throw "$name failed (exit $($p.ExitCode)); inspect redacted audit logs"}
}
try{
    $packages=@(
        @{repo='gitleaks/gitleaks';tag='v8.30.1';file='gitleaks_8.30.1_windows_x64.zip';sums='gitleaks_8.30.1_checksums.txt'},
        @{repo='google/osv-scanner';tag='v2.7.1';file='osv-scanner_windows_amd64.exe';sums='osv-scanner_SHA256SUMS'},
        @{repo='rhysd/actionlint';tag='v1.7.12';file='actionlint_1.7.12_windows_amd64.zip';sums='actionlint_1.7.12_checksums.txt'}
    )
    foreach($package in $packages){
        $base='https://github.com/'+$package.repo+'/releases/download/'+$package.tag+'/'
        foreach($name in @($package.file,$package.sums)){
            $path=Join-Path $tools $name
            if(!(Test-Path -LiteralPath $path)){Invoke-WebRequest -UseBasicParsing ($base+$name) -OutFile $path}
        }
        $expected=((Get-Content -LiteralPath (Join-Path $tools $package.sums)|Where-Object {$_ -match ([regex]::Escape($package.file)+'$')}) -split '\s+')[0]
        $hash=(Get-FileHash -LiteralPath (Join-Path $tools $package.file)).Hash
        if(!$expected -or $hash -ne $expected){throw 'Scanner archive checksum mismatch'}
        if($package.file.EndsWith('.zip')){Expand-Archive -LiteralPath (Join-Path $tools $package.file) -DestinationPath $tools -Force}
        $result.tools+=@{release=$package.repo+'/'+$package.tag;archive=$package.file;sha256=$hash}
    }
    Invoke-Check 'secrets-history' (Join-Path $tools 'gitleaks.exe') @('git','.', '--redact','--no-banner','--report-format=json','--report-path',('"'+(Join-Path $output 'secrets-history.json')+'"'))
    Invoke-Check 'secrets-candidate' (Join-Path $tools 'gitleaks.exe') @('dir','.', '--redact','--no-banner','--report-format=json','--report-path',('"'+(Join-Path $output 'secrets-candidate.json')+'"'))
    Invoke-Check 'workflow-lint' (Join-Path $tools 'actionlint.exe') @('-shellcheck=','-pyflakes=','.github/workflows/ci.yml')
    $errors=@()
    foreach($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1'){
        $parseErrors=$null;$tokens=$null
        [void][Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$parseErrors)
        $errors+=@($parseErrors)
    }
    $result.powershellFiles=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1').Count
    if($errors.Count){throw 'PowerShell parse errors in infra scripts'}
    & git -C $root diff --check
    if($LASTEXITCODE){throw 'git diff --check failed'}
    $result.status='PASS'
}catch{$result.status='FAIL';$result.error=$_.Exception.Message;throw}
finally{$result|ConvertTo-Json -Depth 8|Set-Content -Encoding UTF8 (Join-Path $output 'security-static.json')}
Write-Output 'PASS full-history/candidate secret scans, workflow lint, PowerShell parsing and diff check.'
