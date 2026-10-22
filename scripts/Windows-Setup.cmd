@echo off
setlocal
set "MANAGER=%~dp0..\release\smooth-bridge-manager-0.1.0.jar"
echo.
echo =============================================
echo  Smooth Bridge v0.1.0 - Fabric 1.20.1 / Kilt
 echo =============================================
echo Only use a COPY of Smooth Odyssey. Exit Minecraft before APPLY.
echo.
set /p "INSTANCE=Path to copied Minecraft instance (folder containing mods): "
if not exist "%INSTANCE%\mods\" (
    echo ERROR: Instance mods folder not found. No changes were made.
    pause
    exit /b 2
)
set /p "PROFILE=Profile [skarrier/azazel/cataclysm/all/none] (default skarrier): "
if "%PROFILE%"=="" set "PROFILE=skarrier"
echo.
echo ======== READ-ONLY PLAN ========
java -jar "%MANAGER%" plan --game-dir "%INSTANCE%" --profile "%PROFILE%"
if errorlevel 2 echo Plan reported an error. Check output before continuing.
echo.
choice /c YN /n /m "APPLY to copied instance mods/ now? [Y/N]: "
if errorlevel 2 goto end
java -jar "%MANAGER%" apply --game-dir "%INSTANCE%" --profile "%PROFILE%"
if errorlevel 1 (
    echo Apply blocked or failed. Read error above.
) else (
    echo Apply complete. First launch on a new test world only!
)
:end
echo.
pause
endlocal
