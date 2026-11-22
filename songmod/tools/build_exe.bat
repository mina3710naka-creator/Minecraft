@echo off
rem PyInstaller を使って GUI 変換ツールを単一の実行可能ファイル (SongConverter.exe) にビルドします。
chcp 65001 >nul
cd /d "%~dp0"

where python >nul 2>nul
if errorlevel 1 (
    echo Python が見つかりません。
    pause
    exit /b 1
)

echo PyInstaller を確認・インストールしています...
python -m pip install --upgrade pyinstaller

echo.
echo SongConverter.exe をビルドしています...
pyinstaller --noconfirm --onedir --windowed --name "SongConverter" ^
    --add-data "demucs_tools.py;." ^
    --add-data "songtools.py;." ^
    --add-data "convert.py;." ^
    --add-data "audio_to_notes.py;." ^
    --add-data "video_to_notes.py;." ^
    --add-data "pair_to_notes.py;." ^
    convert_gui.py

echo.
if exist "dist\SongConverter\SongConverter.exe" (
    echo ビルド完了: dist\SongConverter\SongConverter.exe
) else (
    echo ビルドに失敗しました。
)
pause
