param([switch]$CreatePaginationFixtures)
. "$PSScriptRoot/local-env.ps1"
Add-Type -AssemblyName System.Net.Http
$base='http://127.0.0.1:8080';$client=[Net.Http.HttpClient]::new();$client.Timeout=[TimeSpan]::FromSeconds(15)
function Get-Api([string]$path,[string]$token,[int]$expected=200){
    $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get,$base+$path)
    try {
        if($token){$request.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token)}
        $response=$client.SendAsync($request).GetAwaiter().GetResult()
        try {
            if([int]$response.StatusCode -ne $expected){throw "Unexpected HTTP status for $path : $([int]$response.StatusCode)"}
            $body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json
            if($expected -ge 400){if(!$body.code -or !$body.correlationId){throw 'Missing API error contract'}}
            return $body
        } finally {$response.Dispose()}
    } finally {$request.Dispose()}
}
try {
    if((Get-Api '/actuator/health' '').status -ne 'UP'){throw 'Backend is not UP'}
    $admin='demo-admin-token';$customer='demo-customer-token'
    $page=Get-Api '/api/admin/events?size=20' $admin
    if($CreatePaginationFixtures){
        # Add only the missing private fixtures; never reset or delete demo records.
        $needed=[Math]::Max(0,21-$page.totalElements)
        for($i=0;$i -lt $needed;$i++){
            $body=@{title='T7 pagination '+[Guid]::NewGuid().ToString();description='Private local pagination fixture';venue='Local';saleStartAt='2020-01-01T00:00:00Z';saleEndAt='2099-01-01T00:00:00Z';startsAt='2099-01-02T00:00:00Z';status='DRAFT';ticketTypes=@(@{name='GA';mode='GA';priceVnd=100000;capacity=6})}|ConvertTo-Json -Depth 6
            Invoke-RestMethod "$base/api/admin/events" -Method Post -Headers @{Authorization="Bearer $admin"} -ContentType 'application/json' -Body $body|Out-Null
        }
        $page=Get-Api '/api/admin/events?size=20' $admin
    }
    foreach($path in @('/api/admin/events','/api/admin/bookings','/api/admin/operations/outbox')){
        Get-Api $path '' 401|Out-Null;Get-Api $path 'unknown' 401|Out-Null;Get-Api $path $customer 403|Out-Null
    }
    Get-Api '/api/admin/bookings?status=unknown' $admin 400|Out-Null
    Get-Api '/api/admin/events?size=101' $admin 400|Out-Null
    if($page.totalElements -gt 20){
        $next=Get-Api '/api/admin/events?size=20&page=1' $admin
        if($next.page -ne 1 -or $next.items.Count -eq 0 -or @($next.items|Where-Object {$_.id -in $page.items.id}).Count){throw 'Pagination repeats or omits its second page'}
        Write-Output "PASS pagination API:total=$($page.totalElements),page0=$($page.items.Count),page1=$($next.items.Count),no overlapping IDs."
    }else{Write-Output 'Pagination has one page. Use -CreatePaginationFixtures to add private fixtures for Chrome testing.'}
    if($page.items.Count){
        $stock=Get-Api "/api/admin/events/$($page.items[0].id)/inventory" $admin
        foreach($type in $stock.ticketTypes){if($type.mode -eq 'GA' -and $type.capacity -ne $type.held+$type.sold+$type.available){throw 'GA snapshot is inconsistent'}}
        Get-Api "/api/admin/events/$($page.items[0].id)/inventory" $customer 403|Out-Null
    }
    $confirmed=Get-Api '/api/admin/bookings?status=CONFIRMED' $admin
    if(@($confirmed.items|Where-Object {$_.status -ne 'CONFIRMED'}).Count){throw 'Booking filter failed'}
    $bindings=@(Get-NetTCPConnection -State Listen -LocalPort 8080)
    if(!($bindings|Where-Object {$_.LocalAddress -eq '127.0.0.1'}) -or ($bindings|Where-Object {$_.LocalAddress -eq '0.0.0.0' -or $_.LocalAddress -eq '::'})){throw 'Backend is not bound only to loopback'}
    Write-Output 'PASS T7 smoke:health UP;401/403;admin catalog/inventory/booking filter/outbox policy;validation400;backend loopback.'
} finally {$client.Dispose()}
