@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
    echo Gradle not in PATH. Use IntelliJ IDEA Gradle import or install Gradle 8.14.3 first.
    echo Prebuilt binaries are in release\.
    pause
    exit /b 2
)
cd /d "%~dp0.."
gradle build :manager:jar
if errorlevel 1 (
  echo BUILD FAILED, read Gradle output.
) else (
  echo Built under build\libs and manager\build\libs
)
pause
endlocal
