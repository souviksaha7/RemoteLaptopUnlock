@echo off
title Remote Laptop Unlock - Register Boot Task (specxy.net)
cd /d "%~dp0"

echo ============================================================
echo  Remote Laptop Biometric Unlock - Register Boot Task
echo  Made by specxy.net ^| License: specxy.net
echo ============================================================
echo.
echo Registering agent to start automatically at system boot...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0register_boot_task.ps1"

pause
