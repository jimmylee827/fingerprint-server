@echo off
setlocal EnableDelayedExpansion

:: ============================================================
::  Fingerprint REST API Server - Startup Script
:: ============================================================

:: Enable UTF-8
chcp 65001 >nul 2>&1

:: Initialize variables
set "JAVA_CMD="
set "JAVA_OK=0"
set "DRIVER_OK=0"
set "COMPILE_OK=0"
set "LOG_FILE=startup_log.txt"

:: Get script directory
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

:: Start logging
echo ============================================================ > "%LOG_FILE%"
echo  Fingerprint REST API Server - Startup Log >> "%LOG_FILE%"
echo  Date: %DATE% %TIME% >> "%LOG_FILE%"
echo ============================================================ >> "%LOG_FILE%"

:: ============================================================
::  BANNER
:: ============================================================
cls
echo ============================================================
echo   Fingerprint REST API Server - Startup
echo ============================================================
echo.

:: ============================================================
::  STEP 1: Check Java Installation
:: ============================================================
echo [1/4] Checking Java installation...
echo [1/4] Checking Java installation... >> "%LOG_FILE%"

:: Method 1: Check if java is in PATH
where java >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "JAVA_CMD=java"
    echo       Found java in PATH >> "%LOG_FILE%"
    goto :java_found
)

:: Method 2: Check JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        goto :java_found
    )
)

:: Java not found
echo       [ERROR] Java not found!
echo       [ERROR] Java not found! >> "%LOG_FILE%"
goto :check_failed

:java_found
:: Get Java version for display
for /f "tokens=3" %%v in ('"%JAVA_CMD%" -version 2^>^&1') do (
    if not defined JAVA_VER_SET (
        set "JAVA_VER=%%~v"
        set "JAVA_VER_SET=1"
    )
)
echo       [OK] Java found: version !JAVA_VER!
echo       [OK] Java found: version !JAVA_VER! >> "%LOG_FILE%"
set "JAVA_OK=1"
echo.

:: ============================================================
::  STEP 2: Check ZKTeco Driver
:: ============================================================
echo [2/4] Checking ZKTeco driver...
echo [2/4] Checking ZKTeco driver... >> "%LOG_FILE%"

if exist "%SystemRoot%\System32\libzkfp.dll" (
    echo       [OK] Driver found in System32
    echo       [OK] Driver found >> "%LOG_FILE%"
    set "DRIVER_OK=1"
    goto :driver_done
)
if exist "%SystemRoot%\SysWOW64\libzkfp.dll" (
    echo       [OK] Driver found in SysWOW64
    echo       [OK] Driver found >> "%LOG_FILE%"
    set "DRIVER_OK=1"
    goto :driver_done
)
echo       [ERROR] ZKTeco driver not found!
echo       [ERROR] Driver not found >> "%LOG_FILE%"
goto :check_failed

:driver_done
echo.

:: ============================================================
::  STEP 3: Compile
:: ============================================================
echo [3/4] Compiling...
echo [3/4] Compiling... >> "%LOG_FILE%"

:: Clean bin directory
if exist "%SCRIPT_DIR%bin" rmdir /s /q "%SCRIPT_DIR%bin" >nul 2>&1
mkdir "%SCRIPT_DIR%bin"

:: Create data directory if needed
if not exist "%SCRIPT_DIR%data" mkdir "%SCRIPT_DIR%data"

:: Find javac
set "JAVAC_CMD="
where javac >nul 2>&1
if !ERRORLEVEL! EQU 0 (
    set "JAVAC_CMD=javac"
) else if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
)

if not defined JAVAC_CMD (
    echo       [ERROR] javac not found!
    goto :check_failed
)

:: Build classpath for all JARs
set "LIB_CP="
for %%f in ("%SCRIPT_DIR%lib\*.jar") do (
    if defined LIB_CP (
        set "LIB_CP=!LIB_CP!;%%f"
    ) else (
        set "LIB_CP=%%f"
    )
)

echo       Classpath: !LIB_CP! >> "%LOG_FILE%"

:: Compile all source files
echo       Compiling source files...
"!JAVAC_CMD!" -cp "!LIB_CP!" -d "%SCRIPT_DIR%bin" ^
    "%SCRIPT_DIR%src\com\fingerprint\model\*.java" ^
    "%SCRIPT_DIR%src\com\fingerprint\server\*.java" ^
    2>> "%LOG_FILE%"

if !ERRORLEVEL! NEQ 0 (
    echo       [ERROR] Compilation failed!
    echo       [ERROR] Compilation failed >> "%LOG_FILE%"
    echo       Check %LOG_FILE% for details
    goto :check_failed
)

echo       [OK] Compiled successfully
echo       [OK] Compiled successfully >> "%LOG_FILE%"
set "COMPILE_OK=1"
echo.

:: ============================================================
::  STEP 4: Check Fingerprint Device
:: ============================================================
echo [4/4] Checking fingerprint device...
echo [4/4] Checking fingerprint device... >> "%LOG_FILE%"
echo       [INFO] Please ensure your fingerprint reader is connected
echo.

:: ============================================================
::  ALL CHECKS PASSED
:: ============================================================
echo ============================================================
echo   All Checks Passed!
echo ============================================================
echo.
echo   [OK] Java:     !JAVA_VER!
echo   [OK] Driver:   Installed
echo   [OK] Compiled: Ready
echo.
echo   Server will start on: http://localhost:8080
echo   API tests file: api-tests.http
echo.
echo ============================================================ >> "%LOG_FILE%"
echo  ALL CHECKS PASSED - Starting server >> "%LOG_FILE%"
echo ============================================================ >> "%LOG_FILE%"

:: ============================================================
::  RUN THE SERVER
:: ============================================================
echo.
echo Starting Fingerprint REST API Server...
echo.
echo ------------------------------------------------------------ >> "%LOG_FILE%"
echo Server started at: %DATE% %TIME% >> "%LOG_FILE%"
echo ------------------------------------------------------------ >> "%LOG_FILE%"

:: Build runtime classpath
set "RUN_CP=%SCRIPT_DIR%bin"
for %%f in ("%SCRIPT_DIR%lib\*.jar") do (
    set "RUN_CP=!RUN_CP!;%%f"
)

"%JAVA_CMD%" -cp "!RUN_CP!" com.fingerprint.server.FingerprintServer

echo.
echo ------------------------------------------------------------ >> "%LOG_FILE%"
echo Server stopped at: %DATE% %TIME% >> "%LOG_FILE%"
echo ------------------------------------------------------------ >> "%LOG_FILE%"

echo.
echo ============================================================
echo   Server stopped
echo ============================================================
echo.
echo Press any key to close...
pause >nul
goto :end

:: ============================================================
::  CHECK FAILED
:: ============================================================
:check_failed
echo.
echo ============================================================
echo   Startup Failed
echo ============================================================
echo.
echo   Please fix the issues above and try again.
echo   Log file: %SCRIPT_DIR%%LOG_FILE%
echo.
echo ============================================================ >> "%LOG_FILE%"
echo  STARTUP FAILED >> "%LOG_FILE%"
echo ============================================================ >> "%LOG_FILE%"
echo.
echo Press any key to exit...
pause >nul

:end
endlocal
