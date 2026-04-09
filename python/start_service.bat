@echo off
REM Start Regulator Bulk Validation Service
echo Starting Regulator Bulk Validation Service...
echo.

REM Set database environment variables (modify these as needed)
set DB_HOST=localhost
set DB_PORT=3306
set DB_USERNAME=root
set DB_PASSWORD=
set DB_NAME=project

echo Database Configuration:
echo   Host: %DB_HOST%
echo   Port: %DB_PORT%
echo   Database: %DB_NAME%
echo.

REM Check if virtual environment exists
if not exist venv (
    echo Creating virtual environment...
    python -m venv venv
    echo.
)

REM Activate virtual environment
call venv\Scripts\activate.bat

REM Install dependencies
echo Installing/updating dependencies...
pip install -r requirements.txt
echo.

REM Start the service
echo Starting FastAPI service on http://localhost:8000
echo Press Ctrl+C to stop the service
echo.
python bulk_validation_service.py

