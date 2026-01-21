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
echo   Fingerprint Registration Script
echo ============================================================
echo.

:: Read serverPort from config.json using PowerShell
for /f "usebackq" %%p in (`powershell -NoProfile -Command "(Get-Content -Raw 'config.json' | ConvertFrom-Json).serverPort"`) do set PORT=%%p

if "%PORT%"=="" (
    echo [ERROR] Could not read serverPort from config.json
    goto :cleanup
)

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
    echo Please start the server first using start.bat
    goto :cleanup
)

:: Get name input
echo.
set /p NAME="Enter name: "
if "%NAME%"=="" (
    echo [ERROR] Name cannot be empty
    goto :cleanup
)

:: Get role input
echo.
echo Select role:
echo   A/Admin = Admin
echo   U/User  = User
echo.
set /p ROLE_INPUT="Enter role (A/U): "

:: Convert role input to proper value (case-insensitive)
set ROLE=
if /i "%ROLE_INPUT%"=="a" set ROLE=Admin
if /i "%ROLE_INPUT%"=="admin" set ROLE=Admin
if /i "%ROLE_INPUT%"=="u" set ROLE=User
if /i "%ROLE_INPUT%"=="user" set ROLE=User

if "%ROLE%"=="" (
    echo [ERROR] Invalid role. Please enter A/Admin or U/User
    goto :cleanup
)

echo.
echo ============================================================
echo   Registration Details
echo ============================================================
echo   Name: %NAME%
echo   Role: %ROLE%
echo ============================================================
echo.
echo Sending registration request...
echo Please place your finger on the scanner when prompted.
echo.

:: Make API request to register using external PowerShell script
powershell -NoProfile -ExecutionPolicy Bypass -File "register-api.ps1" -Port "%PORT%" -ApiKey "%API_KEY%" -Name "%NAME%" -Role "%ROLE%"

goto :cleanup
