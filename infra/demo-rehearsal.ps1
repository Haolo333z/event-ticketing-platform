param(
 [ValidateSet('Prepare','Start','NewRun','Show','Ga','Seats','Late','Outage','Recover','Replay','Verify','Rehearse','Backup','Stop')][string]$Action='Show',
 [string]$Session='reports/t10-2/session.json', [switch]$Paced
)
$ErrorActionPreference='Stop'
$workspaceRoot=Split-Path $PSScriptRoot -Parent
$reportRoot=Join-Path $workspaceRoot 'reports/t10-2'
$sessionPath=[IO.Path]::GetFullPath((Join-Path $workspaceRoot $Session))
if(!$sessionPath.StartsWith($reportRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Session must be inside reports/t10-2'}
New-Item -ItemType Directory -Force $reportRoot | Out-Null
Add-Type -AssemblyName System.Net.Http
$http=[Net.Http.HttpClient]::new();$http.Timeout=[TimeSpan]::FromSeconds(20)
function Tree($x){
 if($null -eq $x){return $null}
 if($x -is [Management.Automation.PSCustomObject]){$h=@{};foreach($p in $x.PSObject.Properties){$h[$p.Name]=Tree $p.Value};return $h}
 if($x -is [Array]){return ,@($x|ForEach-Object {Tree $_})}
 return $x
}
function Load($path){Tree (Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json)}
function Save($path,$value){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 35),[Text.UTF8Encoding]::new($false))}
function Check($ok,$message){if(!$ok){throw "CHECK FAILED: $message"}}
function Wait-Until([scriptblock]$test,[int]$seconds=60){$end=[DateTime]::UtcNow.AddSeconds($seconds);do{if(& $test){return};Start-Sleep -Milliseconds 400}while([DateTime]::UtcNow -lt $end);throw "Timed out after ${seconds}s"}
function Persist {Save $sessionPath $script:s;if($script:r){Save $script:s.runFile $script:r}}
function Assert-Owned {
 Check ($s.project -match '^ticketing-t102-[a-f0-9]{12}$') 'project is not a rehearsal project'
 $expected=Join-Path (Split-Path $workspaceRoot -Qualifier) ("ticketing-t102-"+$s.project.Substring(15))
 Check ([IO.Path]::GetFullPath($s.checkout).Equals($expected,[StringComparison]::OrdinalIgnoreCase)) 'checkout/project identity mismatch'
 Check ($s.api -eq 'http://127.0.0.1:18081' -and $s.dbPort -eq 15433 -and $s.kafkaPort -eq 19093) 'unexpected ports'
 Check ((Get-FileHash -LiteralPath (Join-Path $s.checkout '.env') -Algorithm SHA256).Hash -eq $s.envSha256) 'environment changed; inspect it before continuing'
 $cfg=@{};foreach($line in [IO.File]::ReadAllLines((Join-Path $s.checkout '.env'))){if($line -match '^([A-Z_]+)=(.*)$'){$cfg[$Matches[1]]=$Matches[2]}}
 Check ($cfg.COMPOSE_PROJECT_NAME -eq $s.project -and $cfg.POSTGRES_DB -eq 'ticketing_rehearsal' -and $cfg.DB_PORT -eq '15433' -and $cfg.KAFKA_PORT -eq '19093' -and $cfg.SERVER_PORT -eq '18081') 'configuration is not isolated'
 $script:dc=@('compose','--project-name',$s.project,'--env-file',(Join-Path $s.checkout '.env'),'-f',(Join-Path $s.checkout 'infra/docker-compose.yml'))
}
function Compose([string[]]$args2){& docker @script:dc @args2;if($LASTEXITCODE -ne 0){throw 'Owned compose command failed'}}
function Request($method,$path,$body=$null,$role='customer',$key=$null){
 $req=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($method),($s.api+$path))
 if($role -ne 'anonymous'){$fixture=if($role -eq 'admin'){'demo-admin-token'}else{'demo-customer-token'};$req.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$fixture)}
 if($key){$req.Headers.Add('Idempotency-Key',[string]$key)}
 if($null -ne $body){$req.Content=[Net.Http.StringContent]::new(($body|ConvertTo-Json -Depth 15 -Compress),[Text.Encoding]::UTF8,'application/json')}
 return $req
}
function Response($res){try{$raw=$res.Content.ReadAsStringAsync().GetAwaiter().GetResult();$obj=if($raw){$raw|ConvertFrom-Json}else{$null};return @{status=[int]$res.StatusCode;body=$obj}}finally{$res.Dispose()}}
function Api($method,$path,$body=$null,$role='customer',$key=$null){$req=Request $method $path $body $role $key;try{Response ($http.SendAsync($req).GetAwaiter().GetResult())}finally{$req.Dispose()}}
function Expect($res,$code){Check ($res.status -eq $code) "HTTP expected $code got $($res.status), code=$($res.body.code)";return $res.body}
function Audit($label){
 Check ($r.ga.id -match '^[a-f0-9-]{36}$' -and $r.seated.id -match '^[a-f0-9-]{36}$') 'fixture IDs missing'
 $raw=Get-Content (Join-Path $workspaceRoot 'infra/demo-audit.sql') -Raw | & docker @script:dc exec -T postgres psql -U ticketing -d ticketing_rehearsal -qAt -v ON_ERROR_STOP=1 -v "ga_event=$($r.ga.id)" -v "seat_event=$($r.seated.id)"
 Check ($LASTEXITCODE -eq 0) 'read-only audit SQL failed'
 $a=Tree (($raw -join "`n")|ConvertFrom-Json);Save (Join-Path $r.directory ($label+'.json')) $a;return $a
}
function Poll-Booking($id,$status){Wait-Until { (Expect (Api GET "/api/bookings/$id") 200).status -eq $status } 65}
function Input-Booking($name,$event,$seat=$null){
 if(!$r.inputs.ContainsKey($name)){
  $body=@{eventId=$event.id;ticketTypeId=$event.typeId};if($seat){$body.seatIds=@($seat)}else{$body.quantity=1}
  $r.inputs[$name]=@{key=('t102-'+[Guid]::NewGuid().ToString('N'));body=$body;paymentId=[Guid]::NewGuid().ToString()};Persist
 };return $r.inputs[$name]
}
function Hold($name,$event,$seat=$null){$input=Input-Booking $name $event $seat;$rec=Expect (Api POST '/api/bookings' $input.body customer $input.key) 202;$input.bookingId=$rec.bookingId;Persist;return $rec}
function Pay($name,$scenario='SUCCESS'){$input=$r.inputs[$name];$null=Expect (Api POST "/api/bookings/$($input.bookingId)/simulate-payment" @{scenario=$scenario;requestId=$input.paymentId}) 202}
function Fixture($name,$mode){
 $title="T10.2 $($r.id) $name";$found=@();$page=0
 do{$list=Expect (Api GET "/api/admin/events?page=$page&size=100" $null admin) 200;$found+=@($list.items|Where-Object title -eq $title);$page++}while($page*100 -lt $list.totalElements)
 Check ($found.Count -le 1) 'ambiguous fixture title'
 if($found.Count -eq 1){$ev=$found[0]}else{
  $now=[DateTime]::UtcNow;$tt=@{name=$name;mode=$mode;priceVnd=100000}
  if($mode -eq 'GA'){$tt.capacity=10}else{$tt.priceVnd=200000;$tt.seats=@(@{label='A1'},@{label='A2'})}
  $body=@{title=$title;description='Dedicated rehearsal fixture; simulated payments only';venue='Local rehearsal';status='PUBLISHED';saleStartAt=$now.AddDays(-1).ToString('o');saleEndAt=$now.AddHours(8).ToString('o');startsAt=$now.AddDays(1).ToString('o');ticketTypes=@($tt)}
  $ev=Expect (Api POST '/api/admin/events' $body admin) 201
 }
 return @{id=$ev.id;typeId=$ev.ticketTypes[0].id;title=$title}
}
function Start-Product {
 Compose @('up','-d','--wait','postgres','kafka')
 foreach($pair in @(@{port=18081;marker=(Join-Path $s.checkout 'infra/.local-runtime/backend.jar')},@{port=5173;marker=(Join-Path $s.checkout 'frontend/node_modules/vite/bin/vite.js')})){
  $listeners=@(Get-NetTCPConnection -State Listen -LocalPort $pair.port -ErrorAction SilentlyContinue|Where-Object { $_.LocalAddress -ne '::1' -or $pair.port -ne 5173 }|Select-Object -ExpandProperty OwningProcess -Unique)
  foreach($listenerId in $listeners){$p=Get-CimInstance Win32_Process -Filter "ProcessId=$listenerId";Check ($p.CommandLine.Replace('/','\').Contains($pair.marker.Replace('/','\'))) 'port belongs to another process'}
  if(!$listeners.Count){
   if($pair.port -eq 18081){$file=Join-Path $s.checkout 'infra/start-local.ps1';$extra='-Port 18081 -HoldSeconds 20 -ExpiryIntervalSeconds 1';$log='backend'}else{$file=Join-Path $s.checkout 'infra/start-frontend.ps1';$extra='-BindAddress 127.0.0.1';$log='frontend'}
   $p=Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$file`" $extra" -WorkingDirectory $s.checkout -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $reportRoot "$log.out.log") -RedirectStandardError (Join-Path $reportRoot "$log.err.log")
   $s[$log+'LauncherPid']=$p.Id;Persist
  }
 }
 Wait-Until {try{(Api GET '/actuator/health' $null anonymous).status -eq 200}catch{$false}} 240
 Wait-Until {try{$res=$http.GetAsync('http://127.0.0.1:5173').GetAwaiter().GetResult();$ok=[int]$res.StatusCode -eq 200;$res.Dispose();$ok}catch{$false}} 30
}
function Stop-Product {
 foreach($port in @(18081,5173)){
  $marker=if($port -eq 18081){Join-Path $s.checkout 'infra/.local-runtime/backend.jar'}else{Join-Path $s.checkout 'frontend/node_modules/vite/bin/vite.js'}
  foreach($listenerId in @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue|Where-Object { $_.LocalAddress -ne '::1' -or $port -ne 5173 }|Select-Object -ExpandProperty OwningProcess -Unique)){
   $p=Get-CimInstance Win32_Process -Filter "ProcessId=$listenerId";Check ($p.CommandLine.Replace('/','\').Contains($marker.Replace('/','\'))) 'refusing to stop unowned listener';Stop-Process -Id $listenerId
  }
 };Compose @('stop')
}
function Main-Step($name){
 switch($name){
  Show {
   Check ((Api GET '/actuator/health' $null anonymous).status -eq 200) 'health not UP'
   foreach($e in @($r.ga,$r.seated)){$detail=Expect (Api GET "/api/events/$($e.id)") 200;$null=Expect (Api GET "/api/events/$($e.id)/availability") 200;Write-Host "$($e.title) => $($e.id)"}
   Write-Host "Chrome http://127.0.0.1:5173 ; API $($s.api) ; customer/admin local personas"
   return @{snapshot=(Audit 'show');health=200;frontend='http://127.0.0.1:5173'}
  }
  Ga {
   $rec=Hold ga $r.ga;$again=Hold ga $r.ga;Check ($rec.bookingId -eq $again.bookingId) 'idempotency changed booking'
   $held=Audit 'ga-held';Check ($held.counts.bookings -eq 1 -and $held.ga[0].held -eq 1 -and $held.counts.keys -eq 1) 'duplicate hold'
   Pay ga;Poll-Booking $rec.bookingId CONFIRMED
   $cached=Hold ga $r.ga;Check ($cached.status -eq 'PENDING_PAYMENT') 'cached create response changed'
   $a=Audit 'ga-confirmed';Check ($a.counts.tickets -eq 1 -and $a.ga[0].sold -eq 1) 'GA not confirmed once'
   return @{bookingId=$rec.bookingId;http=@(202,202,202);cachedStatus=$cached.status;currentStatus='CONFIRMED';snapshot=$a}
  }
  Seats {
   $avail=Expect (Api GET "/api/events/$($r.seated.id)/availability") 200;$seat=@($avail.ticketTypes[0].seats|Where-Object label -eq 'A1')[0].id;Check ($null -ne $seat) 'A1 missing'
   $one=Input-Booking seat1 $r.seated $seat;$two=Input-Booking seat2 $r.seated $seat
   $req1=Request POST '/api/bookings' $one.body customer $one.key;$req2=Request POST '/api/bookings' $two.body customer $two.key
   try{$t1=$http.SendAsync($req1);$t2=$http.SendAsync($req2);$a1=Response ($t1.GetAwaiter().GetResult());$a2=Response ($t2.GetAwaiter().GetResult())}finally{$req1.Dispose();$req2.Dispose()}
   Check ((@($a1.status,$a2.status)|Sort-Object) -join ',' -eq '202,409') 'seat contest must have exactly one winner'
   $winner=if($a1.status -eq 202){'seat1'}else{'seat2'};$lose=if($a1.status -eq 409){$a1}else{$a2};Check ($lose.body.code -eq 'INVENTORY_UNAVAILABLE') 'unexpected seat conflict'
   $r.winner=$winner;$r.inputs[$winner].bookingId=if($a1.status -eq 202){$a1.body.bookingId}else{$a2.body.bookingId};Persist
   Pay $winner;Poll-Booking $r.inputs[$winner].bookingId CONFIRMED
   $a=Audit 'seat-confirmed';Check ($a.counts.bookings -eq 2 -and $a.counts.keys -eq 2 -and $a.counts.tickets -eq 2 -and @($a.tickets|Where-Object seatLabel -eq 'A1').Count -eq 1) 'seat duplicate or partial booking'
   return @{bookingId=$r.inputs[$winner].bookingId;http=@($a1.status,$a2.status);conflict=$lose.body.code;principalCount=1;snapshot=$a}
  }
  Late {
   $rec=Hold late $r.ga;Poll-Booking $rec.bookingId EXPIRED;$expired=Audit 'late-expired';Check ($expired.ga[0].held -eq 0) 'expired hold not released'
   Pay late;Poll-Booking $rec.bookingId REFUND_REQUIRED
   Wait-Until {$a=Audit 'late-refund';@($a.compensations|Where-Object status -eq 'COMPLETED').Count -eq 1} 30
   $a=Audit 'late-completed';Check ($a.counts.tickets -eq 2 -and $a.lateRecordedAfterDeadline -and $a.counts.compensations -eq 1 -and $a.counts.compensationAttempts -eq 1) 'late success reissued ticket/refund'
   return @{bookingId=$rec.bookingId;states=@('PENDING_PAYMENT','EXPIRED','REFUND_REQUIRED');expired=$expired;snapshot=$a}
  }
  Outage {
   Compose @('stop','kafka');$root=Api GET '/actuator/health' $null anonymous;$ready=Api GET '/actuator/health/readiness' $null anonymous;$live=Api GET '/actuator/health/liveness' $null anonymous
   Check ($root.status -eq 503 -and $ready.status -eq 503 -and $live.status -eq 200) 'dependency health incorrect'
   $rec=Hold outage $r.ga;$null=Expect (Api POST "/api/bookings/$($rec.bookingId)/cancel") 200
   $pending=Audit 'kafka-down-committed';Check ($pending.ga[0].held -eq 0 -and $pending.counts.bookings -eq 4) 'commit lost with broker down'
   Wait-Until {$a=Audit 'kafka-down-retry';@($a.outbox|Where-Object {$_.bookingId -eq $rec.bookingId -and $_.version -eq 1 -and $_.status -eq 'FAILED' -and $_.attempts -eq 3}).Count -eq 1} 65
   return @{bookingId=$rec.bookingId;health=@{root=$root.status;readiness=$ready.status;liveness=$live.status};committed=$pending;snapshot=(Audit 'kafka-down-failed')}
  }
  Recover {
   Compose @('up','-d','--wait','kafka');Wait-Until {(Api GET '/actuator/health' $null anonymous).status -eq 200} 45
   $a=Audit 'recovery-before';$event=@($a.outbox|Where-Object {$_.bookingId -eq $r.inputs.outage.bookingId -and $_.version -eq 1})[0]
   if(!$r.ContainsKey('recoveryRequest')){$r.recoveryRequest=@{eventId=$event.id;body=@{requestId=[Guid]::NewGuid().ToString();reason='T10.2 owned Kafka recovered; replay lowest failed version'}};Persist}
   $null=Expect (Api POST "/api/admin/operations/events/$($r.recoveryRequest.eventId)/replay" $r.recoveryRequest.body admin) 202
   Wait-Settled
   $a=Audit 'recovery-after';Check ($a.counts.tickets -eq 2 -and $a.ga[0].held -eq 0) 'recovery changed inventory/tickets'
   return @{eventId=$event.id;snapshot=$a}
  }
  Replay {
   Wait-Settled;$before=Audit 'replay-before';$event=@($before.outbox|Where-Object {$_.bookingId -eq $r.inputs.ga.bookingId -and $_.type -eq 'PaymentResultRecorded'})[0];Check ($null -ne $event) 'processed payment event missing'
   if(!$r.ContainsKey('duplicateRequest')){$r.duplicateRequest=@{eventId=$event.id;body=@{requestId=[Guid]::NewGuid().ToString();reason='T10.2 duplicate processed payment must be no-op'}};Persist}
   $first=Api POST "/api/admin/operations/events/$($event.id)/replay" $r.duplicateRequest.body admin;$second=Api POST "/api/admin/operations/events/$($event.id)/replay" $r.duplicateRequest.body admin;$null=Expect $first 202;$null=Expect $second 202
   Wait-Settled
   Wait-Until { [bool](Get-Content (Join-Path $reportRoot 'backend.out.log') | Where-Object {$_ -like "*$($event.id)*" -and $_ -match 'DUPLICATE'}) } 30
   $traces=@(Get-Content (Join-Path $reportRoot 'backend.out.log') | Where-Object {$_ -like "*$($event.id)*" -and $_ -match 'DUPLICATE'} | ForEach-Object {$line=$_|ConvertFrom-Json;@{at=$line.'@timestamp';eventId=$line.eventId;bookingId=$line.bookingId;correlationId=$line.correlationId;stage=$line.stage;outcome=$line.outcome;consumer=$line.consumer}})
   Check (@($traces|Where-Object {$_.consumer -eq 'payment-v1' -and $_.stage -eq 'consumer_processed'}).Count -ge 1) 'payment consumer duplicate not observed'
   $after=Audit 'replay-after';Check ($before.businessSha256 -eq $after.businessSha256) 'replay changed committed business state'
   $detail=Expect (Api GET "/api/admin/operations/events/$($event.id)" $null admin) 200
   Check ($detail.audit.Count -eq 1 -and $detail.remainingReplays -eq 2) 'same replay request consumed two audit entries/budget'
   return @{eventId=$event.id;http=@($first.status,$second.status);duplicateConsumerObserved=$true;duplicateTraces=$traces;auditCount=$detail.audit.Count;remainingReplays=$detail.remainingReplays;before=$before;after=$after}
  }
  Verify {
   Wait-Settled;$a=Audit 'final';$expected=@{bookings=4;items=4;tickets=2;payments=3;history=9;keys=4;outbox=12;inbox=15;projections=4;projectionEvents=12;compensations=1;compensationAttempts=1}
   foreach($key in $expected.Keys){Check ($a.counts[$key] -eq $expected[$key]) "final $key expected $($expected[$key]) got $($a.counts[$key])"}
   foreach($key in $a.violations.Keys){Check ($a.violations[$key] -eq 0) "invariant $key"}
   Check ($a.ga[0].capacity -eq 10 -and $a.ga[0].sold -eq 1 -and $a.ga[0].held -eq 0 -and @($a.seats|Where-Object {$_.label -eq 'A1' -and $_.status -eq 'SOLD'}).Count -eq 1) 'final allocations'
   return @{snapshot=$a;expected=$expected}
  }
 }
}
function Wait-Settled {
 Wait-Until {$a=Audit 'settling';@($a.outbox|Where-Object status -ne 'PUBLISHED').Count -eq 0 -and $a.violations.projectionLag -eq 0 -and $a.counts.inbox -eq ($a.counts.outbox+$a.counts.payments)} 65
}
function Run-Step($name){
 if($r.results.ContainsKey($name) -and $r.results[$name].status -eq 'PASS' -and $name -notin @('Show','Verify')){Write-Host "$name already PASS; displaying saved result, not repeating holds";return}
 $start=Get-Date;Write-Host "--- $name ---"
 try{$data=Main-Step $name;$r.results[$name]=@{status='PASS';started=$start.ToString('o');seconds=[math]::Round(((Get-Date)-$start).TotalSeconds,3);data=$data};Persist;Write-Host "$name PASS ($($r.results[$name].seconds)s)";if($data.bookingId){Write-Host "bookingId=$($data.bookingId)"};if($data.eventId){Write-Host "eventId=$($data.eventId)"};if($data.snapshot){Write-Host ('counts='+($data.snapshot.counts|ConvertTo-Json -Compress));Write-Host ('violations='+($data.snapshot.violations|ConvertTo-Json -Compress))}}
 catch{$r.results[$name]=@{status='FAIL';started=$start.ToString('o');seconds=[math]::Round(((Get-Date)-$start).TotalSeconds,3);error=$_.Exception.Message};Persist;throw}
}
try {
 if($Action -eq 'Prepare'){
  Check (!(Test-Path -LiteralPath $sessionPath)) 'session already exists; use Start or another session path'
  foreach($port in @(15433,19093,18081)){Check (!(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)) "port $port busy"}
  Check (!(Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue|Where-Object LocalAddress -ne '::1')) 'IPv4 frontend port5173 busy'
  $suffix=[Guid]::NewGuid().ToString('N').Substring(0,12);$copy=Join-Path (Split-Path $workspaceRoot -Qualifier) ('ticketing-t102-'+$suffix);Check (!(Test-Path $copy)) 'checkout exists'
  & git clone --quiet --no-hardlinks $workspaceRoot $copy;Check ($LASTEXITCODE -eq 0) 'clone failed'
  $source=@(& git -C $workspaceRoot ls-files --cached --others --exclude-standard);$manifest=@()
  foreach($rel in $source){if($rel -eq '.env' -or $rel -match '(^|/)\.env$'){continue};$from=Join-Path $workspaceRoot $rel;$to=Join-Path $copy $rel
   if(Test-Path -LiteralPath $from -PathType Leaf){New-Item -ItemType Directory -Force (Split-Path $to -Parent)|Out-Null;Copy-Item -LiteralPath $from -Destination $to -Force;$hash=(Get-FileHash $from -Algorithm SHA256).Hash;Check ((Get-FileHash $to -Algorithm SHA256).Hash -eq $hash) 'working-tree copy mismatch';$manifest+=@{path=$rel;sha256=$hash}}
   elseif(Test-Path -LiteralPath $to -PathType Leaf){Remove-Item -LiteralPath $to}
  }
  Save (Join-Path $reportRoot 'source-manifest.json') $manifest
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $copy 'infra/local-env.ps1');Check ($LASTEXITCODE -eq 0) 'new environment generation failed'
  $envFile=Join-Path $copy '.env';$text=[IO.File]::ReadAllText($envFile);$changes=@{POSTGRES_DB='ticketing_rehearsal';DB_PORT='15433';SERVER_PORT='18081';KAFKA_PORT='19093';KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:19093';VITE_API_BASE_URL='http://127.0.0.1:18081'}
  foreach($key in $changes.Keys){$text=$text -replace "(?m)^$key=.*$",("$key="+$changes[$key])};$text+="`r`nCOMPOSE_PROJECT_NAME=ticketing-t102-$suffix`r`n";[IO.File]::WriteAllText($envFile,$text,[Text.UTF8Encoding]::new($false))
  $script:s=@{checkout=$copy;project="ticketing-t102-$suffix";api='http://127.0.0.1:18081';dbPort=15433;kafkaPort=19093;envSha256=(Get-FileHash $envFile -Algorithm SHA256).Hash;head=(& git -C $workspaceRoot rev-parse HEAD);prepared=(Get-Date).ToString('o');runFile=$null};$script:r=$null;Persist
  & npm.cmd ci --prefix (Join-Path $copy 'frontend') *> (Join-Path $reportRoot 'npm-ci.log');Check ($LASTEXITCODE -eq 0) 'npm ci failed'
  Assert-Owned;Start-Product;Write-Host "Prepared $($s.project), $copy"
 }else{
  Check (Test-Path -LiteralPath $sessionPath) 'run Prepare first';$script:s=Load $sessionPath;$script:r=$null;Assert-Owned
  if($s.runFile){$script:r=Load $s.runFile}
  switch($Action){
   Start {Start-Product}
   Stop {Stop-Product}
   NewRun {
    Check ((Api GET '/actuator/health' $null anonymous).status -eq 200) 'start healthy environment first'
    $id=[Guid]::NewGuid().ToString('N').Substring(0,12);$dir=Join-Path $reportRoot ('runs/'+$id);New-Item -ItemType Directory -Force $dir|Out-Null
    $script:r=@{id=$id;directory=$dir;inputs=@{};results=@{};created=(Get-Date).ToString('o')};$s.runFile=Join-Path $dir 'run.json';Persist
    $r.ga=Fixture GA GA;Persist;$r.seated=Fixture SEATED SEATED;Persist;Run-Step Show
   }
   Rehearse {
    Check ($null -ne $r) 'run NewRun first';Check (@($r.results.Keys|Where-Object {$_ -ne 'Show'}).Count -eq 0) 'full rehearsal needs NewRun; resume individual Actions for an interrupted run';$start=Get-Date;$review=0;$r.timing=@{started=$start.ToString('o');mode=if($Paced){'HTTP_SQL_with_timed_review'}else{'HTTP_SQL_technical_only'};status='RUNNING'};Persist
    try{
     foreach($stage in @(@('Show',45),@('Ga',45),@('Seats',45),@('Late',45),@('Outage',35),@('Recover',30),@('Replay',35),@('Verify',30))){Run-Step $stage[0];if($Paced){Write-Host "Review/narration window $($stage[1])s: $($stage[0]); inspect stable API/SQL/UI state";for($j=0;$j -lt $stage[1];$j++){Start-Sleep -Seconds 1};$review+=$stage[1]}}
     $elapsed=((Get-Date)-$start).TotalSeconds;$r.timing.status='PASS';$r.timing.totalSeconds=[math]::Round($elapsed,3);$r.timing.reviewSeconds=$review;$r.timing.activeSeconds=[math]::Round(($elapsed-$review),3);if($Paced){Check ($elapsed -ge 300 -and $elapsed -le 600) 'paced main rehearsal outside 5..10 minutes'};Persist;Write-Host "Rehearsal PASS: $($r.timing.totalSeconds)s (review $review s)"
    }catch{$r.timing.status='FAIL';$r.timing.error=$_.Exception.Message;Persist;Compose @('up','-d','--wait','kafka');throw}
   }
   Backup {
    Check ($r.results.Verify.status -eq 'PASS') 'finish main scenario first';$before=Audit 'backup-main-before';$input=$r.inputs.ga;$changed=@{eventId=$r.ga.id;ticketTypeId=$r.ga.typeId;quantity=2};$res=Api POST '/api/bookings' $changed customer $input.key;Check ($res.status -eq 409 -and $res.body.code -eq 'IDEMPOTENCY_KEY_REUSED') 'changed body must conflict'
    if(!$r.ContainsKey('backupEvent')){$r.backupEvent=Fixture BACKUP SEATED;Persist};$e=$r.backupEvent;$av=Expect (Api GET "/api/events/$($e.id)/availability") 200;$seat=@($av.ticketTypes[0].seats|Where-Object label -eq 'A1')[0].id
    $first=Hold backupExpired $e $seat;Poll-Booking $first.bookingId EXPIRED;$second=Hold backupFailed $e $seat;Pay backupFailed FAILURE;Poll-Booking $second.bookingId PAYMENT_FAILED
    $av=Expect (Api GET "/api/events/$($e.id)/availability") 200;Check (@($av.ticketTypes[0].seats|Where-Object {$_.label -eq 'A1' -and $_.status -eq 'AVAILABLE'}).Count -eq 1) 'failed payment did not release seat'
    Wait-Settled;$after=Audit 'backup-main-after';Check ($before.businessSha256 -eq $after.businessSha256) 'backup affected main fixtures';$r.backup=@{status='PASS';changedKeyHttp=409;expiredBooking=$first.bookingId;failedBooking=$second.bookingId;seatAvailable=$true};Persist;Write-Host 'Backup PASS'
   }
   default {Check ($null -ne $r) 'run NewRun first';Run-Step $Action}
  }
 }
}finally{$http.Dispose()}
