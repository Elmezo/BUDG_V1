@echo off
REM BUDG Platform - Automatic Setup Script (Windows Batch)
REM This script sets up the project automatically when moving to a new machine

echo =========================================
echo BUDG Platform - Automatic Setup
echo =========================================
echo.

REM Check if PowerShell is available
where powershell >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: PowerShell is required but not found!
    echo Please install PowerShell or run setup.ps1 manually
    pause
    exit /b 1
)

REM Run PowerShell script
powershell -ExecutionPolicy Bypass -File "%~dp0\setup.ps1"

pause

