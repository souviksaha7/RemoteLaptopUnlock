@echo off
setlocal enabledelayedexpansion
title Remote Laptop Unlock - Windows Setup Wizard
cd /d "%~dp0"

echo ============================================================
echo   Remote Laptop Biometric Unlock - Windows Setup Wizard
echo   Made by specxy.net ^| License: specxy.net
echo ============================================================
echo.

:: 1. Verify Python
echo [1/5] Checking Python installation...
python --version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Python was not found in PATH!
    echo Please install Python 3.8+ from https://www.python.org/ and check 'Add Python to PATH'.
    pause
    exit /b 1
)
python --version
echo.

:: 2. Install requirements
echo [2/5] Installing Python dependencies...
python -m pip install -r requirements.txt
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to install dependencies.
    pause
    exit /b 1
)
echo.

:: 3. Configure .env
echo [3/5] Checking configuration (.env)...
if not exist ".env" (
    echo Creating .env from template .env.example...
    copy ".env.example" ".env" >nul
    echo Please edit .env to configure your real Windows password and secure token!
) else (
    echo .env configuration file already exists.
)
echo.

:: 4. Windows Firewall setup
echo [4/5] Configuring Windows Defender Firewall rules...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup_firewall.ps1"
echo.

:: 5. Windows Boot Task configuration
echo [5/5] Configuring Auto-Start at System Boot (Task Scheduler)...
echo This allows the agent to run automatically when the laptop boots,
echo so you can unlock it with your phone without touching the keyboard.
set /p SETUP_BOOT="Register boot task now? (Y/N, default Y): "
if /i "!SETUP_BOOT!"=="N" (
    echo Skipping boot task registration.
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0register_boot_task.ps1"
)

echo.
echo ============================================================
echo   Setup Complete!
echo ============================================================
echo You can run the agent in console mode via: start_agent.bat
echo Or silently in background via: start_silent.bat
echo.
pause
