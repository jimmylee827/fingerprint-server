@echo off
setlocal EnableDelayedExpansion

:: ============================================================
::  SimpleFingerprintReader - One-Click Startup Script
:: ============================================================
::  This script checks all prerequisites, compiles if needed,
::  and runs the fingerprint capture program.
:: ============================================================

:: Enable UTF-8
chcp 65001 >nul 2>&1

:: Initialize variables
set "JAVA_CMD="
set "JAVA_OK=0"
set "DRIVER_OK=0"
set "COMPILE_OK=0"
set "ALL_OK=0"
set "ARCH=unknown"
set "LOG_FILE=startup_log.txt"

:: Get script directory
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

:: Start logging
echo ============================================================ > "%LOG_FILE%"
echo  SimpleFingerprintReader - Startup Log >> "%LOG_FILE%"
echo  Date: %DATE% %TIME% >> "%LOG_FILE%"
echo ============================================================ >> "%LOG_FILE%"
echo. >> "%LOG_FILE%"

:: ============================================================
::  BANNER
:: ============================================================
cls
echo %CYAN%============================================================%RESET%
echo %CYAN%  SimpleFingerprintReader - One-Click Startup%RESET%
echo %CYAN%============================================================%RESET%
echo.

:: ============================================================
::  STEP 1: Detect System Architecture
:: ============================================================
echo [1/5] Detecting system architecture...
echo [1/5] Detecting system architecture... >> "%LOG_FILE%"

if "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
    set "ARCH=64-bit"
    echo       [OK] System: 64-bit Windows
    echo       [OK] System: 64-bit Windows >> "%LOG_FILE%"
    goto :arch_done
)
if "%PROCESSOR_ARCHITECTURE%"=="x86" (
    if defined PROCESSOR_ARCHITEW6432 (
        set "ARCH=64-bit"
        echo       [OK] System: 64-bit Windows [32-bit process]
        echo       [OK] System: 64-bit Windows [32-bit process] >> "%LOG_FILE%"
    ) else (
        set "ARCH=32-bit"
        echo       [OK] System: 32-bit Windows
        echo       [OK] System: 32-bit Windows >> "%LOG_FILE%"
    )
    goto :arch_done
)
set "ARCH=64-bit"
echo       [WARN] Could not detect architecture, assuming 64-bit
echo       [WARN] Could not detect architecture, assuming 64-bit >> "%LOG_FILE%"

:arch_done
echo.

:: ============================================================
::  STEP 2: Check Java Installation
:: ============================================================
echo [2/5] Checking Java installation...
echo [2/5] Checking Java installation... >> "%LOG_FILE%"

:: Method 1: Check if java is in PATH
where java >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "JAVA_CMD=java"
    echo       Found java in PATH >> "%LOG_FILE%"
    goto :java_found
)

:: Method 2: Check JAVA_HOME
if defined JAVA_HOME (
    echo       Checking JAVA_HOME: %JAVA_HOME% >> "%LOG_FILE%"
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        echo       Found java via JAVA_HOME >> "%LOG_FILE%"
        goto :java_found
    )
)

:: Method 3: Check common installation paths
echo       Checking common installation paths... >> "%LOG_FILE%"
for %%P in (
    "C:\Program Files\Java"
    "C:\Program Files (x86)\Java"
    "C:\Program Files\Eclipse Adoptium"
    "C:\Program Files\Amazon Corretto"
    "C:\Program Files\Zulu"
    "C:\Program Files\BellSoft"
) do (
    if exist "%%~P" (
        for /d %%J in ("%%~P\*") do (
            if exist "%%~J\bin\java.exe" (
                set "JAVA_CMD=%%~J\bin\java.exe"
                echo       Found java at: %%~J >> "%LOG_FILE%"
                goto :java_found
            )
        )
    )
)

:: Method 4: Check Microsoft JDK path
for /d %%J in ("C:\Program Files\Microsoft\jdk-*") do (
    if exist "%%~J\bin\java.exe" (
        set "JAVA_CMD=%%~J\bin\java.exe"
        echo       Found java at: %%~J >> "%LOG_FILE%"
        goto :java_found
    )
)

:: Java not found
echo       [ERROR] Java not found!
echo       [ERROR] Java not found! >> "%LOG_FILE%"
echo.
echo       Java is required to run this program.
echo.
echo       How to install Java:
echo         1. Search Google for: "Java JDK download"
echo            or: "OpenJDK download"
echo            or: "Amazon Corretto download"
echo.
echo         2. Download the !ARCH! version for Windows
echo.
echo         3. Run the installer and follow the instructions
echo.
echo         4. Restart your computer after installation
echo.
echo       [INFO] Java installation instructions provided >> "%LOG_FILE%"
goto :check_failed

:java_found
:: Verify Java works and get version
echo       Using: %JAVA_CMD% >> "%LOG_FILE%"
"%JAVA_CMD%" -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo       [ERROR] Java found but failed to execute!
    echo       [ERROR] Java found but failed to execute >> "%LOG_FILE%"
    goto :check_failed
)

:: Get Java version for display
set "JAVA_VER=detected"
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
::  STEP 3: Check ZKTeco Driver Installation
:: ============================================================
echo [3/5] Checking ZKTeco driver installation...
echo [3/5] Checking ZKTeco driver installation... >> "%LOG_FILE%"

set "DRIVER_PATH="

:: Check System32
if exist "%SystemRoot%\System32\libzkfp.dll" (
    set "DRIVER_PATH=%SystemRoot%\System32\libzkfp.dll"
    echo       Found driver in System32 >> "%LOG_FILE%"
    goto :driver_found
)

:: Check SysWOW64
if exist "%SystemRoot%\SysWOW64\libzkfp.dll" (
    set "DRIVER_PATH=%SystemRoot%\SysWOW64\libzkfp.dll"
    echo       Found driver in SysWOW64 >> "%LOG_FILE%"
    goto :driver_found
)

:: Check Program Files
for %%P in (
    "C:\Program Files\ZKTeco"
    "C:\Program Files (x86)\ZKTeco"
    "C:\Program Files\Zkteco"
    "C:\Program Files (x86)\Zkteco"
    "C:\ZKTeco"
) do (
    if exist "%%~P\libzkfp.dll" (
        set "DRIVER_PATH=%%~P\libzkfp.dll"
        echo       Found driver at: %%~P >> "%LOG_FILE%"
        goto :driver_found
    )
    if exist "%%~P" (
        for /r "%%~P" %%F in (libzkfp.dll) do (
            if exist "%%F" (
                set "DRIVER_PATH=%%F"
                echo       Found driver at: %%F >> "%LOG_FILE%"
                goto :driver_found
            )
        )
    )
)

:: Check if DLL is in current directory or lib folder
if exist "%SCRIPT_DIR%libzkfp.dll" (
    set "DRIVER_PATH=%SCRIPT_DIR%libzkfp.dll"
    echo       Found driver in script directory >> "%LOG_FILE%"
    goto :driver_found
)
if exist "%SCRIPT_DIR%lib\libzkfp.dll" (
    set "DRIVER_PATH=%SCRIPT_DIR%lib\libzkfp.dll"
    echo       Found driver in lib directory >> "%LOG_FILE%"
    goto :driver_found
)

:: Driver not found
echo       [ERROR] ZKTeco driver not found!
echo       [ERROR] ZKTeco driver not found! >> "%LOG_FILE%"
echo.
echo       The fingerprint reader driver is required.
echo.
echo       How to install the driver:
echo         1. Locate your ZKFinger SDK zip file
echo.
echo         2. Look for a folder named:
echo            "driver", "drivers", "setup", or "installer"
echo.
echo         3. Run the driver installer for your Windows version
echo            [!ARCH!]
echo.
echo         4. Connect your fingerprint reader device
echo.
echo         5. Restart your computer after installation
echo.
echo       [INFO] Driver installation instructions provided >> "%LOG_FILE%"
goto :check_failed

:driver_found
echo       [OK] Driver found: %DRIVER_PATH%
echo       [OK] Driver found: %DRIVER_PATH% >> "%LOG_FILE%"
set "DRIVER_OK=1"
echo.

:: ============================================================
::  STEP 4: Check/Create Directories and Compile
:: ============================================================
echo [4/5] Checking compilation...
echo [4/5] Checking compilation... >> "%LOG_FILE%"

:: Check for source file
set "SRC_FILE=%SCRIPT_DIR%src\com\fingerprint\test\SimpleFingerprintReader.java"
set "CLASS_FILE=%SCRIPT_DIR%bin\com\fingerprint\test\SimpleFingerprintReader.class"

if not exist "%SRC_FILE%" (
    echo       [ERROR] Source file not found!
    echo       [ERROR] Source file not found: %SRC_FILE% >> "%LOG_FILE%"
    echo       Expected: %SRC_FILE%
    goto :check_failed
)
echo       Source file found >> "%LOG_FILE%"

:: Always clean and recreate bin directory for fresh compilation
if exist "%SCRIPT_DIR%bin" (
    echo       Cleaning previous compilation... >> "%LOG_FILE%"
    rmdir /s /q "%SCRIPT_DIR%bin" >nul 2>&1
)
echo       Creating bin directory... >> "%LOG_FILE%"
mkdir "%SCRIPT_DIR%bin"

:: Create outputs directory if needed
if not exist "%SCRIPT_DIR%outputs" (
    echo       Creating outputs directory... >> "%LOG_FILE%"
    mkdir "%SCRIPT_DIR%outputs"
    echo       [OK] Created outputs directory
)

:: Always recompile
echo       Compiling... (always recompile on start)
echo       Running javac... >> "%LOG_FILE%"

:: Find javac
set "JAVAC_CMD="
where javac >nul 2>&1
if !ERRORLEVEL! EQU 0 (
    set "JAVAC_CMD=javac"
) else if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" (
        set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
    )
)

if not defined JAVAC_CMD (
    :: Try to find javac in same location as java
    for %%J in ("%JAVA_CMD%") do set "JAVA_DIR=%%~dpJ"
    if exist "!JAVA_DIR!javac.exe" (
        set "JAVAC_CMD=!JAVA_DIR!javac.exe"
    )
)

if not defined JAVAC_CMD (
    echo       [ERROR] javac [Java compiler] not found!
    echo       [ERROR] javac not found >> "%LOG_FILE%"
    echo       Please install the full JDK [not just JRE]
    goto :check_failed
)

echo       Using compiler: !JAVAC_CMD! >> "%LOG_FILE%"

"!JAVAC_CMD!" -cp "%SCRIPT_DIR%lib\ZKFingerReader.jar" -d "%SCRIPT_DIR%bin" "%SRC_FILE%" 2>> "%LOG_FILE%"

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
::  STEP 5: Check Fingerprint Device Connection
:: ============================================================
echo [5/5] Checking fingerprint device...
echo [5/5] Checking fingerprint device... >> "%LOG_FILE%"

:: Note: We can't easily check USB devices from batch, so we'll just warn
echo       [INFO] Please ensure your fingerprint reader is connected
echo       [INFO] Device connection reminder displayed >> "%LOG_FILE%"
echo       If the device is not connected, the program will fail to start.
echo.

:: ============================================================
::  ALL CHECKS PASSED - SUMMARY
:: ============================================================
set "ALL_OK=1"
echo ============================================================
echo   All Checks Passed!
echo ============================================================
echo.
echo   [OK] System:      !ARCH! Windows
echo   [OK] Java:        !JAVA_VER!
echo   [OK] Driver:      Installed
echo   [OK] Compiled:    Ready
echo.
echo   Fingerprints will be saved to:
echo   %SCRIPT_DIR%outputs\test_fingerprint.bmp
echo   %SCRIPT_DIR%outputs\test_fingerprint.txt
echo.
echo   Type 'exit' in the program to stop capturing.
echo.
echo ============================================================ >> "%LOG_FILE%"
echo  ALL CHECKS PASSED - Starting program >> "%LOG_FILE%"
echo ============================================================ >> "%LOG_FILE%"

:: ============================================================
::  RUN THE PROGRAM
:: ============================================================
echo.
echo Starting SimpleFingerprintReader...
echo.
echo ------------------------------------------------------------ >> "%LOG_FILE%"
echo Program started at: %DATE% %TIME% >> "%LOG_FILE%"
echo ------------------------------------------------------------ >> "%LOG_FILE%"

"%JAVA_CMD%" -cp "%SCRIPT_DIR%bin;%SCRIPT_DIR%lib\ZKFingerReader.jar" com.fingerprint.test.SimpleFingerprintReader

echo.
echo ------------------------------------------------------------ >> "%LOG_FILE%"
echo Program ended at: %DATE% %TIME% >> "%LOG_FILE%"
echo ------------------------------------------------------------ >> "%LOG_FILE%"

echo.
echo ============================================================
echo   Program ended
echo ============================================================
echo.
echo Press any key to close...
pause >nul
goto :end

:: ============================================================
::  CHECK FAILED - EXIT
:: ============================================================
:check_failed
echo.
echo ============================================================
echo   Startup Check Failed
echo ============================================================
echo.
echo   Please fix the issues above and try again.
echo.
echo   Log file: %SCRIPT_DIR%%LOG_FILE%
echo.
echo ============================================================ >> "%LOG_FILE%"
echo  STARTUP FAILED - See errors above >> "%LOG_FILE%"
echo ============================================================ >> "%LOG_FILE%"
echo.
echo Press any key to exit...
pause >nul

:end
endlocal
