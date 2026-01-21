@echo off
setlocal enabledelayedexpansion

:: Ensure we always pause before exit
goto :main

:cleanup
echo.
echo Press any key to exit...
pause >nul
exit /b %ERRORLEVEL%

:main
echo ============================================================
echo   Fingerprint Import Script
echo ============================================================
echo.

:: Check if transfer folder exists and has files
if not exist "transfer" (
    echo [ERROR] transfer folder not found!
    echo.
    echo Please run RUN_export.bat first to create transfer files.
    goto :cleanup
)

:: Count JSON files in transfer folder
set FILE_COUNT=0
for %%f in (transfer\fp_transfer_*.json) do set /a FILE_COUNT+=1

if %FILE_COUNT%==0 (
    echo [ERROR] No transfer files found in transfer folder!
    echo.
    echo Please run RUN_export.bat first to create transfer files.
    goto :cleanup
)

echo Found %FILE_COUNT% transfer file(s) in transfer folder:
echo.

:: List files with index numbers
set INDEX=0
for %%f in (transfer\fp_transfer_*.json) do (
    set /a INDEX+=1
    set "FILE_!INDEX!=%%f"
    
    :: Extract info from filename and file content
    for /f "usebackq" %%n in (`powershell -NoProfile -Command "$json = Get-Content -Raw '%%f' | ConvertFrom-Json; Write-Host $json.fingerprint.name"`) do set "NAME_!INDEX!=%%n"
    
    echo   [!INDEX!] %%~nxf
    powershell -NoProfile -Command "$json = Get-Content -Raw '%%f' | ConvertFrom-Json; Write-Host '       Name: ' $json.fingerprint.name '| Role:' $json.fingerprint.role"
    echo.
)

:: Ask user to select a file
echo ============================================================
set /p SELECTION="Select file to import (1-%INDEX%): "

:: Validate selection
set VALID=0
for /l %%i in (1,1,%INDEX%) do (
    if "%SELECTION%"=="%%i" set VALID=1
)

if %VALID%==0 (
    echo.
    echo [ERROR] Invalid selection: %SELECTION%
    echo Please enter a number between 1 and %INDEX%
    goto :cleanup
)

:: Get selected file path
set "SELECTED_FILE=!FILE_%SELECTION%!"
echo.
echo Selected: %SELECTED_FILE%

:: Show file contents summary
echo.
echo Transfer File Details:
powershell -NoProfile -Command "$json = Get-Content -Raw '%SELECTED_FILE%' | ConvertFrom-Json; Write-Host '  Name:        ' $json.fingerprint.name; Write-Host '  Role:        ' $json.fingerprint.role; Write-Host '  Exported At: ' $json.exportedAt"

:: Read serverPort from config.json using PowerShell
for /f "usebackq" %%p in (`powershell -NoProfile -Command "(Get-Content -Raw 'config.json' | ConvertFrom-Json).serverPort"`) do set PORT=%%p

if "%PORT%"=="" (
    echo [ERROR] Could not read serverPort from config.json
    goto :cleanup
)

echo.
echo Server Port: %PORT%

:: Read INTERNAL_KEY from .env file
set API_KEY=
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        if "%%a"=="INTERNAL_KEY" set API_KEY=%%b
    )
)

:: Check if server is running
echo.
echo Checking if server is running...
powershell -NoProfile -Command "try { $response = Invoke-WebRequest -Uri 'http://localhost:%PORT%/api/status' -Method GET -Headers @{'Authorization'='Bearer %API_KEY%'} -TimeoutSec 3 -UseBasicParsing; Write-Host '[OK] Server is running'; exit 0 } catch { Write-Host '[ERROR] Server is not running or not responding'; exit 1 }"
if %ERRORLEVEL% neq 0 (
    echo.
    echo Please start the server first using RUN_start.bat
    goto :cleanup
)

:: Confirm import
echo.
echo ============================================================
echo   Ready to Import
echo ============================================================
echo.
set /p CONFIRM="Proceed with import? (Y/N): "
if /i not "%CONFIRM%"=="y" (
    if /i not "%CONFIRM%"=="yes" (
        echo.
        echo Import cancelled.
        goto :cleanup
    )
)

echo.
echo Importing fingerprint...

:: Make API request to import using external PowerShell script
powershell -NoProfile -ExecutionPolicy Bypass -File "ps_import-api.ps1" -Port "%PORT%" -ApiKey "%API_KEY%" -FilePath "%SELECTED_FILE%"

goto :cleanup
