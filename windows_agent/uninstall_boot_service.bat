@echo off
title Remote Laptop Unlock - Remove Boot Task (specxy.net)
cd /d "%~dp0"

echo ============================================================
echo  Remote Laptop Unlock - Remove Boot Task
echo  Made by specxy.net ^| License: specxy.net
echo ============================================================
echo.

powershell -NoProfile -Command "Start-Process cmd.exe -ArgumentList '/c schtasks.exe /Delete /TN RemoteLaptopUnlockBoot /F & timeout /t 2' -Verb RunAs"

echo [SUCCESS] Task uninstaller invoked with Administrator privileges.
pause
