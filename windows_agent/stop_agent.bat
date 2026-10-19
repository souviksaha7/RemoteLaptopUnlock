@echo off
title Remote Laptop Unlock - Stop Agent
cd /d "%~dp0"

echo ============================================================
echo  Stopping Remote Laptop Unlock Windows Agent
echo ============================================================
echo.

powershell -NoProfile -Command ^
    "$procs = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*agent.py*' }; " ^
    "if ($procs) { " ^
    "    $procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Host ('[STOPPED] Terminated process ID: ' + $_.ProcessId) -ForegroundColor Green }; " ^
    "} else { " ^
    "    Write-Host '[INFO] No running agent.py processes found.' -ForegroundColor Yellow; " ^
    "}"

echo.
timeout /t 2 /nobreak >nul
