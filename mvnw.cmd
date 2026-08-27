@echo off
setlocal
REM Lightweight Maven bootstrap wrapper (no pre-installed Maven required).
REM Downloads Apache Maven into tools\maven on first run, then delegates.
set "MAVEN_VERSION=3.9.9"
set "TOOLS_DIR=%~dp0tools\maven"
set "MVN=%TOOLS_DIR%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd"

if exist "%MVN%" goto :run

echo [mvnw] Apache Maven %MAVEN_VERSION% not found, downloading (first run only)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; $url='https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip'; $zip=Join-Path $env:TEMP 'maven-%MAVEN_VERSION%-bin.zip'; if (!(Test-Path $zip)) { Invoke-WebRequest -Uri $url -OutFile $zip }; $dest=Join-Path (Resolve-Path '%~dp0').Path 'tools\maven'; New-Item -ItemType Directory -Force -Path $dest | Out-Null; Expand-Archive -Path $zip -DestinationPath $dest -Force"
if errorlevel 1 (
    echo [mvnw] Failed to bootstrap Maven. Check network access and retry.
    exit /b 1
)
if not exist "%MVN%" (
    echo [mvnw] Maven bootstrap incomplete.
    exit /b 1
)

:run
call "%MVN%" %*
exit /b %ERRORLEVEL%
