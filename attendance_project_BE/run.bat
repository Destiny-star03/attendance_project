@echo off
setlocal

cd /d "%~dp0"

set "HOST=0.0.0.0"
set "PORT=8000"
set "APP_MODULE=api.server:app"

if exist "venv\Scripts\python.exe" (
    set "PYTHON=venv\Scripts\python.exe"
) else (
    set "PYTHON=python"
)

echo Starting attendance backend server...
echo URL: http://%HOST%:%PORT%
echo Swagger: http://127.0.0.1:%PORT%/docs
echo.

"%PYTHON%" -m uvicorn %APP_MODULE% --host %HOST% --port %PORT% --reload

endlocal
