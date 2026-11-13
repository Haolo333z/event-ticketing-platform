param([switch]$FullSuite)
$ErrorActionPreference='Stop'
# Reuse the existing owned-UUID databases and broker; never pause/stop demo workers or broker.
$tests=if($FullSuite){'*Test'}else{'PostgresMetricsTest,OperationsMetricsTest,KafkaRecoveryTest,TicketingApplicationTest'}
& "$PSScriptRoot/test-part8.ps1" -Tests $tests
Write-Output 'PASS T8.2 metrics gate; test resources cleaned by the isolated PostgreSQL/Kafka harness.'
