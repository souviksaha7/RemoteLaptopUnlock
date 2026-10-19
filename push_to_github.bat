@echo off
title Push to GitHub - Remote Laptop Biometric Unlock (specxy.net)
cd /d "%~dp0"

echo ============================================================
echo  Pushing to GitHub: https://github.com/souviksaha7/RemoteLaptopUnlock
echo  Made by specxy.net ^| License: specxy.net
echo ============================================================
echo.

git add .
git commit -m "update: Remote Laptop Biometric Unlock (Android + Windows) by specxy.net" 2>nul

git push -u origin main

if %ERRORLEVEL% equ 0 (
    echo.
    echo ============================================================
    echo  [SUCCESS] Code successfully pushed to GitHub!
    echo  View at: https://github.com/souviksaha7/RemoteLaptopUnlock
    echo ============================================================
) else (
    echo.
    echo [INFO] If you haven't created the repository on GitHub yet:
    echo 1. Go to https://github.com/new
    echo 2. Set Repository name to: RemoteLaptopUnlock
    echo 3. Click "Create repository" (leave it empty)
    echo 4. Run this script again or push via GitHub Desktop!
)

pause
