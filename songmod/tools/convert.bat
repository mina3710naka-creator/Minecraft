@echo off
rem Drag and drop ONE file (mp4 / mp3 / mid), or TWO files of the same song
rem (keyboard video + original video), onto this file. ASCII-only on purpose.
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
echo Drag and drop ONE file (mp4 / mp3 / mid) onto this file,
echo or TWO files of the same song (a video with a keyboard + the original video).
echo First time only: run install_requirements.bat
pause
exit /b 1
