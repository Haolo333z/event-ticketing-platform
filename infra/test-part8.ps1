param([string]$Tests='*Test')
$ErrorActionPreference='Stop'
# Reuse the proven isolated PostgreSQL/Kafka harness, including all T6 regression tests.
& "$PSScriptRoot/test-part7.ps1" -Tests $Tests
Write-Output 'PASS stage 8 backend gate; isolated databases/broker cleaned by the existing harness.'
