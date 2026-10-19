@echo off
setlocal enabledelayedexpansion
title Remote Laptop Unlock - Install APK to Android Phone
cd /d "%~dp0"

echo ============================================================
echo   Install Remote Laptop Unlock to Connected Android Device
echo ============================================================
echo.

:: Locate ADB
set "ADB_BIN=adb"
where adb >nul 2>&1
if %ERRORLEVEL% neq 0 (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "ADB_BIN=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    ) else (
        echo [ERROR] adb.exe was not found!
        echo Please ensure Android SDK platform-tools are installed or added to PATH.
        pause
        exit /b 1
    )
)

:: Find APK file
set "APK_PATH=%~dp0RemoteLaptopUnlock.apk"
if not exist "!APK_PATH!" (
    set "APK_PATH=%~dp0app\build\outputs\apk\debug\app-debug.apk"
)

if not exist "!APK_PATH!" (
    echo [ERROR] APK file not found!
    echo Please run build_apk.bat first to build the APK.
    pause
    exit /b 1
)

echo Checking for connected Android devices via ADB...
"%ADB_BIN%" devices
echo.

echo Installing APK: "!APK_PATH!"...
"%ADB_BIN%" install -r "!APK_PATH!"

if %ERRORLEVEL% equ 0 (
    echo.
    echo ============================================================
    echo [SUCCESS] App installed on device!
    echo Launching Remote Laptop Unlock...
    echo ============================================================
    "%ADB_BIN%" shell am start -n com.remoteunlock.app/.MainActivity
) else (
    echo.
    echo [ERROR] Installation failed. Ensure your phone has USB debugging enabled and is authorized.
)

echo.
pause
