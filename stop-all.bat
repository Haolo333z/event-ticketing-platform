@echo off
setlocal EnableDelayedExpansion
title EventFlow Enterprise 2026 - Tat He Thong

echo ===============================================================================
echo     EVENTFLOW ENTERPRISE 2026 - TRINH TAT TOAN BO HE THONG (WINDOWS)
echo ===============================================================================
echo.

cd /d "%~dp0"

:: 1. Dong tien trinh Backend (8080) va Frontend (5173)
echo [1/3] Dang dong cac tien trinh Backend (8080) va Frontend (5173)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ports = @(8080, 5173); foreach ($p in $ports) { $conns = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue; if ($conns) { $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique; foreach ($procId in $pids) { try { $pr = Get-Process -Id $procId -ErrorAction SilentlyContinue; if ($pr) { Write-Host ('   - Da dung tien trinh ' + $pr.Name + ' (PID: ' + $procId + ') tai cong ' + $p); Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue } } catch {} } } }"

:: 2. Dong cac cua so Command Prompt / PowerShell theo tieu de neu con mo
echo.
echo [2/3] Dang dong cac cua so chay Backend va Frontend...
taskkill /FI "WINDOWTITLE eq EventFlow Backend (8080)*" /F /T >nul 2>&1
taskkill /FI "WINDOWTITLE eq EventFlow Frontend (5173)*" /F /T >nul 2>&1
echo [OK] Cac cua so tien trinh da duoc dong sach se.

:: 3. Dung Docker Compose (khong xoa du lieu Database / Volume)
echo.
echo [3/3] Dang dung cac container Docker (PostgreSQL 17 va Kafka 4)...
docker compose --env-file .env -f infra/docker-compose.yml stop
if %errorlevel% neq 0 (
    docker compose -f infra/docker-compose.yml stop >nul 2>&1
)
echo [OK] Cac container Docker da duoc dung an toan.

echo.
echo ===============================================================================
echo     EVENTFLOW ENTERPRISE 2026 DA DUOC TAT GON TOAN BO!
echo.
echo     * Backend (port 8080) va Frontend (port 5173) da duoc dong hoan toan.
echo     * Cac container Docker da dung. Du lieu database duoc bao toan nguyen ven.
echo     * De khoi dong lai he thong: Chay file start-all.bat
echo ===============================================================================
echo.
echo Nhan phim bat ky de thoat...
pause >nul

