@echo off
rem Installs the Python packages used by the converter tools. Run once.
chcp 65001 >nul
cd /d "%~dp0"
where python >nul 2>nul
if errorlevel 1 goto nopython
echo Installing packages (this can take a few minutes)...
python -m pip install --upgrade pip
python -m pip install --only-binary=:all: numpy scipy soundfile onnxruntime pretty_midi resampy librosa mir_eval
python -m pip install --no-deps basic-pitch
echo.
echo Done.
echo ffmpeg is also required. If it is missing:  winget install ffmpeg
pause
exit /b 0
:nopython
echo Python was not found. Install Python from https://www.python.org/downloads/
echo and check 'Add python.exe to PATH' in the installer.
pause
exit /b 1
