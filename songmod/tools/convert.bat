@echo off
rem mp4 / mp3 / mid をこのファイルの上にドラッグ&ドロップすると、音符の JSON に変換して、Minecraft の songs フォルダへ入れます。
chcp 65001 >nul
cd /d "%~dp0"
if "%~1"=="" (
  echo mp4 / mp3 / mid のファイル(いくつでも)を、このファイルの上にドラッグ^&ドロップしてください。
  pause
  exit /b 1
)
where python >nul 2>nul
if %errorlevel%==0 (
  python convert.py %*
) else (
  py convert.py %*
)
echo.
pause
