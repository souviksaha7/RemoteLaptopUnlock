@echo off
setlocal enabledelayedexpansion
title Remote Laptop Unlock - Setup Windows Startup

echo ============================================================
echo  Remote Laptop Unlock - Register with Windows Startup
echo ============================================================
echo.

:: Path to launch_hidden.vbs in current directory
set "SCRIPT_DIR=%~dp0"
set "VBS_PATH=%SCRIPT_DIR%launch_hidden.vbs"
set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "SHORTCUT_PATH=%STARTUP_FOLDER%\RemoteLaptopUnlock.lnk"

if not exist "%VBS_PATH%" (
    echo [ERROR] Could not find launch_hidden.vbs at:
    echo "%VBS_PATH%"
    pause
    exit /b 1
)

echo Creating startup shortcut in:
echo "%STARTUP_FOLDER%"
echo.

:: Create shortcut via temporary PowerShell command
powershell -NoProfile -Command ^
    "$wsh = New-Object -ComObject WScript.Shell; " ^
    "$shortcut = $wsh.CreateShortcut('%SHORTCUT_PATH%'); " ^
    "$shortcut.TargetPath = '%VBS_PATH%'; " ^
    "$shortcut.WorkingDirectory = '%SCRIPT_DIR%'; " ^
    "$shortcut.Description = 'Remote Laptop Biometric Unlock Agent'; " ^
    "$shortcut.Save()"

if exist "%SHORTCUT_PATH%" (
    echo [SUCCESS] Startup shortcut created successfully!
    echo The Windows agent will now automatically start silently on user logon.
) else (
    echo [ERROR] Failed to create shortcut in Startup folder.
)

echo.
pause
