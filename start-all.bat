@echo off
setlocal
title EventFlow Enterprise 2026 - Khoi Dong 1-Cham

echo ===============================================================================
echo     EVENTFLOW ENTERPRISE 2026 - TRINH KHOI DONG 1-CHAM (WINDOWS)
echo ===============================================================================
echo.

cd /d "%~dp0"

:: 1. Kiem tra Docker Desktop da chay chua
echo [1/5] Kiem tra Docker Desktop...
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo [LOI] Docker Desktop chua duoc bat hoac Docker Daemon chua san sang!
    echo Vui long khoi dong Docker Desktop tren may cua ban va thu lai.
    echo.
    pause
    exit /b 1
)
echo [OK] Docker Desktop dang hoat dong.

:: 2. Giai phong xung dot cong 8080 hoac 5173 neu co tien trinh cu
echo.
echo [2/5] Kiem tra va giai phong cac cong 8080 va 5173 neu bi chiem dung...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ports = @(8080, 5173); foreach ($p in $ports) { $conns = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue; if ($conns) { $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique; foreach ($procId in $pids) { try { $pr = Get-Process -Id $procId -ErrorAction SilentlyContinue; if ($pr) { Write-Host ('   - Giai phong tien trinh ' + $pr.Name + ' (PID: ' + $procId + ') tai cong ' + $p); Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue } } catch {} } } }"
echo [OK] Cac cong 8080 va 5173 da san sang.

:: 3. Khoi dong PostgreSQL 17 va Kafka qua Docker Compose
echo.
echo [3/5] Khoi dong PostgreSQL 17 va Apache Kafka 4 qua Docker Compose...
docker compose --env-file .env -f infra/docker-compose.yml up -d
if %errorlevel% neq 0 (
    echo.
    echo [LOI] Khong the khoi dong cac container trong infra/docker-compose.yml!
    pause
    exit /b 1
)

echo    Dang doi PostgreSQL (port 5432) san sang...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$timeout = 35; $sw = [Diagnostics.Stopwatch]::StartNew(); while ($sw.Elapsed.TotalSeconds -lt $timeout) { try { $tcp = New-Object Net.Sockets.TcpClient; $tcp.Connect('127.0.0.1', 5432); $tcp.Close(); Write-Host '   [OK] PostgreSQL port 5432 da san sang ket noi!' -ForegroundColor Green; exit 0 } catch { Start-Sleep -Seconds 1 } }; Write-Host '   [CANH BAO] Khong the ket noi port 5432 sau 35 giay' -ForegroundColor Yellow; exit 1"

:: 4. Kiem tra file .jar va ma nguon Backend de tu dong package neu can
echo.
echo [4/5] Kiem tra dong bo file .jar backend voi ma nguon (.java / .sql)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$jar = 'backend\target\ticketing-backend-0.0.1-SNAPSHOT.jar'; if (-not (Test-Path $jar)) { exit 10 }; $jarTime = (Get-Item $jar).LastWriteTime; $newer = Get-ChildItem -Path 'backend\src' -Recurse -File | Where-Object { ($_.Extension -eq '.java' -or $_.Extension -eq '.sql') -and $_.LastWriteTime -gt $jarTime } | Select-Object -First 1; if ($newer) { exit 10 } else { exit 0 }"
if %errorlevel% equ 10 (
    echo    Phat hien ma nguon backend moi hon file .jar hoac file .jar chua ton tai.
    echo    Dang tien hanh dong goi lai backend voi Maven...
    call .\backend\mvnw.cmd package -DskipTests
    if %errorlevel% neq 0 (
        echo.
        echo [LOI] Dong goi backend bang Maven that bai! Vui long kiem tra ma nguon.
        pause
        exit /b 1
    )
    echo    [OK] Dong goi backend thanh cong!
) else (
    echo    [OK] File .jar backend da san sang va dong bo ma nguon moi nhat.
)

echo.
echo    Dang bat Backend trong cua so rieng "EventFlow Backend (8080)"...
start "EventFlow Backend (8080)" powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\start-backend-local.ps1"

echo    Dang doi Backend khoi dong hoan tat (http://127.0.0.1:8080/actuator/health UP)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$sw = [Diagnostics.Stopwatch]::StartNew(); while ($sw.Elapsed.TotalSeconds -lt 90) { try { $res = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2 -ErrorAction Stop; if ($res.status -eq 'UP') { Write-Host '   [OK] Backend da san sang (HTTP 200 / UP)!' -ForegroundColor Green; exit 0 } } catch { Start-Sleep -Seconds 2 } }; Write-Host '   [LOI] Backend chua phan hoi sau 90 giay!' -ForegroundColor Red; exit 1"
if %errorlevel% neq 0 (
    echo.
    echo [LOI] Backend khong phan hoi dung hen. Vui long kiem tra cua so "EventFlow Backend (8080)".
    pause
    exit /b 1
)

:: 5. Khoi dong Frontend va mo trinh duyet
echo.
echo [5/5] Khoi dong Frontend tai cong 5173...
start "EventFlow Frontend (5173)" cmd /c "cd /d ""%~dp0frontend"" && npm run dev"

echo    Dang doi Frontend khoi dong va tu dong mo trinh duyet...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$sw = [Diagnostics.Stopwatch]::StartNew(); while ($sw.Elapsed.TotalSeconds -lt 20) { try { $tcp = New-Object Net.Sockets.TcpClient; $tcp.Connect('127.0.0.1', 5173); $tcp.Close(); exit 0 } catch { Start-Sleep -Milliseconds 500 } }; exit 0"

start http://127.0.0.1:5173

echo.
echo ===============================================================================
echo     EVENTFLOW ENTERPRISE 2026 DA KHOI DONG THANH CONG!
echo.
echo     * Giao dien Web (Frontend) : http://127.0.0.1:5173
echo     * API Backend Service     : http://127.0.0.1:8080
echo     * Kiem tra Health         : http://127.0.0.1:8080/actuator/health
echo     * Database PostgreSQL 17  : 127.0.0.1:5432 (ticketing_demo)
echo     * Event Streaming Kafka 4 : 127.0.0.1:9092
echo.
echo     - Cua so Backend : "EventFlow Backend (8080)"
echo     - Cua so Frontend: "EventFlow Frontend (5173)"
echo     - De tat he thong: Chay file stop-all.bat
echo ===============================================================================
echo.
pause
