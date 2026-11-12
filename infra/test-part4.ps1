param([string]$Tests='*Test')
# Same isolation and cleanup as Part 3; all tests include the new booking gates.
& "$PSScriptRoot/test-part3.ps1" -Tests $Tests
