@echo off
title PKLocker - Device Owner Setup Tool
color 0A

set ADB="C:\Users\shanm\AppData\Local\Android\Sdk\platform-tools\adb.exe"

echo.
echo ============================================
echo    PKLocker - Device Owner Setup Tool
echo ============================================
echo.
echo INSTRUCTIONS (Pehle yeh karein):
echo   1. Target phone ko Factory Reset karein
echo   2. Setup wizard mein koi Google account ADD NA karein (Skip karein)
echo   3. Settings ^> About Phone ^> Build Number par 7 dafa tap karein
echo   4. Settings ^> Developer Options ^> USB Debugging ON karein
echo   5. Target phone ko USB cable se PC se connect karein
echo   6. Phone par "Allow USB Debugging" ka popup aaye toh OK karein
echo   7. Phir Enter dabayein
echo.
echo ============================================
pause

echo.
echo [1/4] Checking ADB connection...
%ADB% devices
echo.
echo Agar upar koi device ID dikh raha hai toh connection OK hai!
echo Agar "unauthorized" likha hai toh phone par USB debugging allow karein.
echo.
pause

echo.
echo [2/4] Installing PKLocker APK on target phone...
if exist "app\release\app-release.apk" (
    %ADB% install -r "app\release\app-release.apk"
    echo.
    echo APK install ho gayi!
) else (
    echo ERROR: app-release.apk nahi mili app\release\ mein
    echo Pehle Android Studio se Build ^> Build Bundle/APK ^> Build APK karein
    pause
    exit /b 1
)
echo.

echo [3/4] Checking accounts on target phone...
%ADB% shell pm list users
echo.
echo Agar upar sirf "UserInfo{0:Owner:4c13}" jaisa 1 user hai toh OK hai.
echo.

echo [4/4] Setting PKLocker as Device Owner...
echo.
%ADB% shell dpm set-device-owner com.pksafe.lock.manager/.receiver.AdminReceiver

echo.
echo ============================================
if %ERRORLEVEL% EQU 0 (
    color 0A
    echo    KAMYAAB! Device Owner set ho gaya!
    echo    PKLocker ka ab target phone par full control hai!
    echo.
    echo    Ab target phone par PKLocker app kholein
    echo    aur shopkeeper login karein.
) else (
    color 0C
    echo    FAIL HO GAYA! Yeh check karein:
    echo.
    echo    1. Phone mein koi Google account toh nahi add kiya?
    echo       Fix: Settings ^> Accounts ^> sab accounts remove karein
    echo.
    echo    2. Phone factory reset nahi kiya?  
    echo       Fix: Settings ^> System ^> Reset ^> Factory Reset
    echo.
    echo    3. USB Debugging on nahi hai?
    echo       Fix: Developer Options mein USB Debugging enable karein
    echo.
    echo    4. Pehle se Device Owner set hai?
    echo       Fix: Factory reset karein aur dubara try karein
)
echo ============================================
echo.
pause
