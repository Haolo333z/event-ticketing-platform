param([string]$Tests='*Test')
# Reuse the isolated PostgreSQL database runner; preserve the existing demo volume.
& "$PSScriptRoot/test-part3.ps1" -Tests $Tests
if($LASTEXITCODE -ne 0){throw 'Part 5 backend checks failed'}
