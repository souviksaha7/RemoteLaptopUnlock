@echo off
setlocal enabledelayedexpansion
title Remote Laptop Biometric Unlock - Control Center
cd /d "%~dp0"

:MENU
cls
echo ============================================================
echo   REMOTE LAPTOP BIOMETRIC UNLOCK - CONTROL CENTER
echo   Made by specxy.net ^| License: specxy.net
echo ============================================================
echo.
echo   [WINDOWS AGENT]
echo   1. Run Windows Setup Wizard (Dependencies + Firewall + .env)
echo   2. Start Agent (Console Mode - Visible output for testing)
echo   3. Start Agent (Silent Background Mode)
echo   4. Register Agent to Run at System Boot (Task Scheduler)
echo   5. Remove Boot Task
echo   6. Stop Running Agent
echo   7. Run Diagnostics / Self-Test (test_agent.py)
echo.
echo   [ANDROID APP]
echo   8. Build Android APK (Debug)
echo   9. Install APK to Connected Phone (via ADB)
echo   10. Open Android Project in File Explorer
echo.
echo   [SYSTEM]
echo   11. Open README.md Guide
echo   0. Exit
echo ============================================================
echo.
set /p CHOICE="Select an option [0-11]: "

if "%CHOICE%"=="1" (
    call "windows_agent\setup_windows.bat"
    goto MENU
)
if "%CHOICE%"=="2" (
    call "windows_agent\start_agent.bat"
    goto MENU
)
if "%CHOICE%"=="3" (
    call "windows_agent\start_silent.bat"
    goto MENU
)
if "%CHOICE%"=="4" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "windows_agent\register_boot_task.ps1"
    goto MENU
)
if "%CHOICE%"=="5" (
    call "windows_agent\uninstall_boot_service.bat"
    goto MENU
)
if "%CHOICE%"=="6" (
    call "windows_agent\stop_agent.bat"
    goto MENU
)
if "%CHOICE%"=="7" (
    python "windows_agent\test_agent.py"
    pause
    goto MENU
)
if "%CHOICE%"=="8" (
    call "android_app\build_apk.bat"
    goto MENU
)
if "%CHOICE%"=="9" (
    call "android_app\install_apk.bat"
    goto MENU
)
if "%CHOICE%"=="10" (
    explorer "android_app"
    goto MENU
)
if "%CHOICE%"=="11" (
    notepad "README.md"
    goto MENU
)
if "%CHOICE%"=="0" (
    exit /b 0
)

goto MENU
