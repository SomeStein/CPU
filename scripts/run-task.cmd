@echo off
setlocal
set "ROOT_DIR=%~dp0.."
set "BUNDLED_PYTHON=%ROOT_DIR%\tools\runtime\windows-x64\python\python.exe"

if exist "%BUNDLED_PYTHON%" (
  "%BUNDLED_PYTHON%" "%ROOT_DIR%\scripts\launcher.py" %*
  exit /b %errorlevel%
)

where py >nul 2>nul
if %errorlevel%==0 (
  py -3 "%ROOT_DIR%\scripts\launcher.py" %*
  exit /b %errorlevel%
)

python "%ROOT_DIR%\scripts\launcher.py" %*
exit /b %errorlevel%
