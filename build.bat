@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title Crit Client - build
echo ==============================
echo   Crit Client build script
echo ==============================
echo.

rem ---------- 1. Java 25+ ----------
where java >nul 2>nul
if errorlevel 1 goto :nojava
set "JV="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do if not defined JV set "JV=%%~v"
for /f "delims=." %%m in ("%JV%") do set "JMAJOR=%%m"
echo [check] Java found: %JV%
if %JMAJOR% LSS 25 goto :oldjava
echo [ ok  ] Java %JMAJOR% is new enough.
goto :gradlecheck

:nojava
echo [FAIL] Java is not installed or not on PATH.
goto :askjava
:oldjava
echo [FAIL] Java %JMAJOR% is too old. Minecraft 26.2 mods need Java 25 or newer.
:askjava
where winget >nul 2>nul
if errorlevel 1 (
    echo        Install JDK 25 from https://adoptium.net then run this file again.
    goto :end
)
set /p ANS="Install Temurin JDK 25 now with winget? (Y/N): "
if /i not "%ANS%"=="Y" goto :end
winget install --id EclipseAdoptium.Temurin.25.JDK -e --accept-package-agreements --accept-source-agreements
echo.
echo Java installed. CLOSE this window, open a new one, and run build.bat again.
goto :end

rem ---------- 2. Gradle wrapper ----------
:gradlecheck
where gradle >nul 2>nul
if errorlevel 1 goto :nosystemgradle
echo [step] Syncing the Gradle wrapper to your installed Gradle version...
call gradle wrapper
if errorlevel 1 (
    echo [FAIL] Couldn't generate the wrapper. Your Gradle may be too old for Java 25.
    echo        Install the latest Gradle from https://gradle.org/install and retry.
    goto :end
)
goto :build

:nosystemgradle
if exist gradlew.bat (
    echo [ ok  ] Using the existing Gradle wrapper.
    goto :build
)
echo [FAIL] Gradle isn't installed.
where winget >nul 2>nul
if errorlevel 1 (
    echo        Install Gradle from https://gradle.org/install then run this file again.
    goto :end
)
set /p ANS="Install Gradle now with winget? (Y/N): "
if /i not "%ANS%"=="Y" goto :end
winget install --id Gradle.Gradle -e --accept-package-agreements --accept-source-agreements
echo.
echo Gradle installed. CLOSE this window, open a new one, and run build.bat again.
goto :end

rem ---------- 3. Build ----------
:build
if not exist gradle.properties (
    echo [FAIL] gradle.properties is missing. Run this from the crit-client folder.
    goto :end
)
echo.
echo [step] Building. The first run downloads Minecraft and takes a few minutes...
echo.
call gradlew.bat build
if errorlevel 1 goto :buildfail

set "JAR="
for %%f in (build\libs\*.jar) do (
    echo %%~nxf | findstr /i "sources" >nul || set "JAR=%%f"
)
if not defined JAR (
    echo [FAIL] Build finished but no jar was found in build\libs.
    goto :end
)
echo.
echo ==============================
echo   BUILD SUCCESSFUL
echo   !JAR!
echo ==============================
echo.
set /p ANS="Copy it to your .minecraft\mods folder now? (Y/N): "
if /i not "%ANS%"=="Y" goto :openfolder
if not exist "%APPDATA%\.minecraft\mods" mkdir "%APPDATA%\.minecraft\mods"
copy /y "!JAR!" "%APPDATA%\.minecraft\mods\" >nul
echo Copied. You also need Fabric Loader and Fabric API for 26.2 in that profile.
:openfolder
start "" "build\libs"
goto :end

:buildfail
echo.
echo ==============================
echo   BUILD FAILED
echo ==============================
echo Common fixes:
echo  - "Could not find fabric-api": open gradle.properties and set fabric_version
echo    to the newest +26.2 build listed at https://fabricmc.net/develop
echo  - "cannot find symbol" in a .java file: Minecraft 26.2 API changed, send me the
echo    error text and I'll fix it.
echo  - Download errors: check your internet connection and retry.

:end
echo.
pause
endlocal
