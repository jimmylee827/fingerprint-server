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
echo   Fingerprint Export Script
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
    echo Please start the server first using RUN_start.bat
    goto :cleanup
)

:: List all fingerprints in table format
echo.
echo ============================================================
echo   Registered Fingerprints
echo ============================================================
echo.
powershell -NoProfile -Command "$headers = @{ 'Authorization' = 'Bearer %API_KEY%' }; try { $response = Invoke-WebRequest -Uri 'http://localhost:%PORT%/api/fingerprints' -Method GET -Headers $headers -TimeoutSec 10 -UseBasicParsing; $fingerprints = $response.Content | ConvertFrom-Json; if ($fingerprints.Count -eq 0) { Write-Host '  No fingerprints registered.' -ForegroundColor Yellow; exit 1 }; Write-Host ('  {0,-40} {1,-20} {2,-10}' -f 'ID', 'NAME', 'ROLE'); Write-Host ('  {0,-40} {1,-20} {2,-10}' -f '----------------------------------------', '--------------------', '----------'); foreach ($fp in $fingerprints) { Write-Host ('  {0,-40} {1,-20} {2,-10}' -f $fp.id, $fp.name, $fp.role) }; exit 0 } catch { Write-Host '  [ERROR] Failed to fetch fingerprints' -ForegroundColor Red; exit 1 }"
if %ERRORLEVEL% neq 0 (
    echo.
    echo No fingerprints available to export.
    goto :cleanup
)

echo.
echo ============================================================

:: Get ID input
echo.
set /p EXPORT_ID="Enter ID to export: "
if "%EXPORT_ID%"=="" (
    echo [ERROR] ID cannot be empty
    goto :cleanup
)

echo.
echo Exporting fingerprint...

:: Make API request to export using external PowerShell script
powershell -NoProfile -ExecutionPolicy Bypass -File "ps_export-api.ps1" -Port "%PORT%" -ApiKey "%API_KEY%" -ExportId "%EXPORT_ID%"

goto :cleanup
