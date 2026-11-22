@echo off
rem Song Builder Converter
rem ファイルをドラッグ＆ドロップ: 自動で変換して .minecraft/songs に保存
rem ダブルクリック: GUI画面を起動
chcp 65001 >nul
cd /d "%~dp0"

where python >nul 2>nul
if not errorlevel 1 (
    set PYCMD=python
    goto checkargs
)
where py >nul 2>nul
if not errorlevel 1 (
    set PYCMD=py
    goto checkargs
)
goto nopython

:checkargs
if "%~1"=="" goto launchgui
echo 変換を開始します...
%PYCMD% convert.py %*
echo.
pause
exit /b 0

:launchgui
echo GUI画面を起動しています...
start "" %PYCMD% convert_gui.py
exit /b 0

:nopython
echo [エラー] Python が見つかりません。
echo 公式サイト (https://www.python.org/downloads/) から Python をインストールしてください。
echo ※インストール時は「Add python.exe to PATH」にチェックを入れてください。
pause
exit /b 1
