@echo off
setlocal enabledelayedexpansion
title Remote Laptop Unlock - Build Android APK
cd /d "%~dp0"

echo ============================================================
echo   Building Remote Laptop Unlock Android APK (Debug)
echo ============================================================
echo.

:: Detect Java JDK
if not defined JAVA_HOME (
    if exist "C:\Program Files\Java\jdk-17" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    ) else if exist "C:\Program Files\Android\Android Studio\jbr" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    )
)

if defined JAVA_HOME (
    echo Using JAVA_HOME: !JAVA_HOME!
) else (
    echo [WARNING] JAVA_HOME is not set. Gradle will attempt to use default java.
)

:: Detect Android SDK
if not defined ANDROID_HOME (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    )
)

if defined ANDROID_HOME (
    echo Using ANDROID_HOME: !ANDROID_HOME!
)

echo.
echo Running Gradle assembleDebug...
call gradlew.bat assembleDebug

if %ERRORLEVEL% equ 0 (
    echo.
    echo ============================================================
    echo [SUCCESS] APK built successfully!
    echo ============================================================
    set "APK_SRC=%~dp0app\build\outputs\apk\debug\app-debug.apk"
    set "APK_DEST=%~dp0RemoteLaptopUnlock.apk"
    if exist "!APK_SRC!" (
        copy /y "!APK_SRC!" "!APK_DEST!" >nul
        echo Ready-to-install APK copied to:
        echo !APK_DEST!
    )
) else (
    echo.
    echo [ERROR] Gradle build failed with exit code %ERRORLEVEL%.
)

echo.
pause
