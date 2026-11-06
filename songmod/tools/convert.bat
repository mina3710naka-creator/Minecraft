@echo off
rem Drag and drop mp4 / mp3 / mid files onto this file.
rem (This file must stay ASCII-only: cmd reads it before the code page is set.)
chcp 65001 >nul
cd /d "%~dp0"
if "%~1"=="" goto usage
where python >nul 2>nul
if errorlevel 1 goto trypy
python convert.py %*
goto done
:trypy
py convert.py %*
:done
echo.
pause
exit /b 0
:usage
echo Drag and drop mp4 / mp3 / mid files (any number) onto this file.
echo First time only: run install_requirements.bat
pause
exit /b 1
