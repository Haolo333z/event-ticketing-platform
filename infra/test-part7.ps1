param([string]$Tests='*Test')
. "$PSScriptRoot/local-env.ps1"
& docker @composeArgs up -d --wait postgres
if($LASTEXITCODE -ne 0){throw 'PostgreSQL unavailable'}
$testDatabase='ticketing_it_'+[Guid]::NewGuid().ToString('N')
$kafkaDatabase='ticketing_it_'+[Guid]::NewGuid().ToString('N')
$testProject='ticketing-kafka-it-'+[Guid]::NewGuid().ToString('N').Substring(0,12)
$oldKafkaPort=$env:KAFKA_PORT
$portProbe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$portProbe.Start();$testPort=$portProbe.LocalEndpoint.Port;$portProbe.Stop()
$testCompose=@('compose','--env-file',$envPath,'-f',(Join-Path $PSScriptRoot 'docker-compose.yml'),'-p',$testProject)
$env:KAFKA_PORT="$testPort"
try {
    & docker @testCompose up -d --wait kafka
    if($LASTEXITCODE -ne 0){throw 'Isolated Kafka unavailable'}
    & docker @composeArgs exec -T postgres createdb -U $env:POSTGRES_USER $testDatabase
    if($LASTEXITCODE -ne 0){throw 'Cannot create isolated database'}
    & docker @composeArgs exec -T postgres createdb -U $env:POSTGRES_USER $kafkaDatabase
    if($LASTEXITCODE -ne 0){throw 'Cannot create Kafka test database'}
    $env:PG_IT_URL="jdbc:postgresql://127.0.0.1:$($env:DB_PORT)/$testDatabase"
    $env:KAFKA_IT_PG_URL="jdbc:postgresql://127.0.0.1:$($env:DB_PORT)/$kafkaDatabase"
    $env:KAFKA_IT_BOOTSTRAP="127.0.0.1:$testPort"
    $env:KAFKA_IT_CONTAINER="$testProject-kafka-1"
    $env:KAFKA_IT_TOPIC="$testProject.booking.v1"
    Push-Location (Join-Path $repoRoot 'backend')
    try {
        & .\mvnw.cmd -B -ntp "-Dtest=$Tests" '-Duser.timezone=UTC' verify
        if($LASTEXITCODE -ne 0){throw 'Stage 7 tests failed; inspect backend/target/surefire-reports'}
    } finally { Pop-Location }
} finally {
    # Remove only the UUID project and database created by this invocation.
    & docker @testCompose down --volumes
    & docker @composeArgs exec -T postgres dropdb -U $env:POSTGRES_USER --if-exists --force $testDatabase
    & docker @composeArgs exec -T postgres dropdb -U $env:POSTGRES_USER --if-exists --force $kafkaDatabase
    foreach($name in @('PG_IT_URL','KAFKA_IT_PG_URL','KAFKA_IT_BOOTSTRAP','KAFKA_IT_CONTAINER','KAFKA_IT_TOPIC')){Remove-Item "Env:$name" -ErrorAction SilentlyContinue}
    $env:KAFKA_PORT=$oldKafkaPort
}
