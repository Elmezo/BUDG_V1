@echo off
REM Close IntelliJ IDEA first, then run this script to fix InvalidPathException.
REM Run from Explorer double-click or: clear_compile_server.bat

set FOLDER=%LOCALAPPDATA%\JetBrains\IdeaIC2025.2\compile-server
if exist "%FOLDER%" (
    rmdir /s /q "%FOLDER%"
    if exist "%FOLDER%" (
        echo Failed - IntelliJ may still be running. Close it and run this again.
    ) else (
        echo compile-server removed. You can reopen IntelliJ now.
    )
) else (
    echo Folder not found - may already be removed.
)
pause
