@echo off
setlocal
set "MANAGER=%~dp0..\release\smooth-bridge-manager-0.1.0.jar"
echo.
echo Smooth Bridge rollback (last apply only). CLOSE MINECRAFT FIRST!
set /p "INSTANCE=Path to copied Minecraft instance: "
if not exist "%INSTANCE%\mods\" (
    echo ERROR: mods folder missing.
    pause
    exit /b 2
)
java -jar "%MANAGER%" status --game-dir "%INSTANCE%"
echo.
choice /c YN /n /m "Restore previous managed profile? [Y/N]: "
if errorlevel 2 goto end
java -jar "%MANAGER%" rollback --game-dir "%INSTANCE%"
:end
pause
endlocal
