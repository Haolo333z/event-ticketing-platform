param([switch]$Live)
$ErrorActionPreference='Stop'
& "$PSScriptRoot/test-part8.ps1"
if($Live){& "$PSScriptRoot/test-part8-ui.ps1"}
Write-Output 'PASS T8.3 recovery backend gate; optional live UI uses owned UUID resources.'
