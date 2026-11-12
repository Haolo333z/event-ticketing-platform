$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../../infra/local-env.ps1"
$container = (& docker @composeArgs ps -q postgres).Trim()
if (!$container) { throw 'Start demo PostgreSQL before measuring.' }
$job = Start-Job -ArgumentList $container,$env:POSTGRES_USER,$env:POSTGRES_DB -ScriptBlock {
    param($container,$user,$db)
    'BEGIN; SELECT pg_advisory_xact_lock(1414); SELECT pg_sleep(8); COMMIT;' |
      docker exec -i $container psql -U $user -d $db -v ON_ERROR_STOP=1
}
try {
    $ready=$false
    for($i=0;$i -lt 30;$i++) {
        $held="SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND objid=1414 AND granted;" |
          docker exec -i $container psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -At
        if (($held -join '').Trim() -eq '1') { $ready=$true; break }
        Start-Sleep -Milliseconds 100
    }
    if (!$ready) { throw 'Lock holder did not start in time.' }
    $watch=[Diagnostics.Stopwatch]::StartNew()
    $sql=@'
\set VERBOSITY verbose
BEGIN;
SET LOCAL lock_timeout='1500ms';
SET LOCAL statement_timeout='5s';
SELECT pg_advisory_xact_lock(1414);
ROLLBACK;
'@
    $ErrorActionPreference = 'Continue' # SQLSTATE on stderr is expected in this measurement.
    $output=$sql | docker exec -i $container psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB 2>&1
    $ErrorActionPreference = 'Stop'
    $watch.Stop()
    $text=$output -join "`n"
    if ($text -notmatch '55P03' -or $text -notmatch 'ROLLBACK') { throw "Unexpected timeout result: $text" }
    Write-Output "PASS: lock_timeout=1500ms, statement_timeout=5s; SQLSTATE 55P03; ROLLBACK; elapsed=$($watch.ElapsedMilliseconds)ms (includes docker/psql overhead)."
} finally {
    Wait-Job $job -Timeout 15 | Out-Null
    Receive-Job $job | Out-Null
    Remove-Job $job -Force
}
