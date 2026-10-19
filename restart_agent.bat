@echo off
title Remote Laptop Unlock - Restart Agent (specxy.net)
cd /d "%~dp0"

echo ============================================================
echo  Remote Laptop Biometric Unlock - Service Restarter
echo  Made by specxy.net ^| License: specxy.net
echo ============================================================
echo.
echo Stopping old agent and applying latest updates...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0windows_agent\register_boot_task.ps1"

pause
