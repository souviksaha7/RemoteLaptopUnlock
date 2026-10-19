@echo off
title Remote Laptop Unlock - Remove Windows Startup

set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "SHORTCUT_PATH=%STARTUP_FOLDER%\RemoteLaptopUnlock.lnk"

echo Removing startup shortcut if present...
if exist "%SHORTCUT_PATH%" (
    del /f /q "%SHORTCUT_PATH%"
    echo [SUCCESS] Startup shortcut removed.
) else (
    echo [INFO] Shortcut was not found in Startup folder.
)

pause
