@echo off
REM Bulk Upload Wizard Build Script for Windows
REM This script installs dependencies and builds the React application

echo ========================================
echo Bulk Upload Wizard - Build Script
echo ========================================
echo.

REM Check if Node.js is installed
where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Node.js is not installed!
    echo Please install Node.js from https://nodejs.org/
    echo Recommended version: 18.x or higher
    pause
    exit /b 1
)

REM Check if npm is installed
where npm >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: npm is not installed!
    echo Please install Node.js from https://nodejs.org/
    pause
    exit /b 1
)

echo Node.js version:
node --version
echo.
echo npm version:
npm --version
echo.

REM Check if node_modules exists
if not exist "frontend\node_modules" (
    echo Step 1: Installing dependencies...
    echo This may take a few minutes on first run...
    echo.
    cd frontend
    npm install
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: npm install failed!
        pause
        exit /b 1
    )
    cd ..
    echo.
    echo Dependencies installed successfully!
    echo.
) else (
    echo Step 1: Dependencies already installed (skipping)
    echo.
)

echo Step 2: Building React application...
echo.
cd frontend
npm run build
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)
cd ..

echo.
echo ========================================
echo Build completed successfully! ✓
echo ========================================
echo.
echo Output location: src\main\webapp\assets\js\bulk-upload\
echo - bundle.js  (React application)
echo - bundle.css (TailwindCSS styles)
echo.
echo Next steps:
echo 1. Build Java application: mvn clean package
echo 2. Deploy WAR to Tomcat
echo 3. Access wizard at: http://localhost:8080/bulk-upload.html
echo.
pause

