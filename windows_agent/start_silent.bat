@echo off
cd /d "%~dp0"
echo Launching Remote Laptop Unlock agent in background (silent mode)...
wscript.exe "%~dp0launch_hidden.vbs"
echo [SUCCESS] Agent started in background!
timeout /t 2 /nobreak >nul
