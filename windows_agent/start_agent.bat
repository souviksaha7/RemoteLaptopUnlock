@echo off
title Remote Laptop Unlock - Windows Agent (Console Mode)
cd /d "%~dp0"

echo ============================================================
echo  Starting Remote Laptop Unlock Windows Agent
echo ============================================================
echo.
python agent.py
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Agent exited with error code %ERRORLEVEL%.
    pause
)
