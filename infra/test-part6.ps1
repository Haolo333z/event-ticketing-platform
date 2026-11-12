param([string]$Tests='*Test')
& "$PSScriptRoot/test-part3.ps1" -Tests $Tests
if($LASTEXITCODE -ne 0){throw 'Part 6 backend checks failed'}
