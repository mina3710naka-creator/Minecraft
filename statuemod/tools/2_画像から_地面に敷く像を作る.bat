@echo off
chcp 932 >nul
set PYTHONIOENCODING=cp932:replace
setlocal enabledelayedexpansion
cd /d "%~dp0"
echo ============================================================
echo  画像から、地面に敷く像（1 層）の JSON を作ります
echo ============================================================
echo.
set "PY="
for %%C in (py python python3) do (
  if not defined PY (
    %%C -c "import sys;print(sys.version_info[0])" 2>nul | findstr /x "3" >nul && set "PY=%%C"
  )
)
if not defined PY (
  echo.
  echo Python が見つかりません。先に「1_最初に1回だけ_必要なものをインストール.bat」を実行してください。
  echo.
  pause
  exit /b 1
)
set "IMG=%~1"
if "!IMG!"=="" (
  echo 画像ファイル（png / jpg / webp）を、このウィンドウにドラッグして Enter を押してください。
  set /p IMG=画像: 
)
set "IMG=!IMG:"=!"
if not exist "!IMG!" (
  echo 画像が見つかりません: !IMG!
  pause
  exit /b 1
)
set "SIZE=400"
set /p SIZE=大きさ（長いほうの辺のブロック数） [400]: 
set "D=n"
set /p D=色を混ぜて細かい階調を出しますか? （y/n） [n]: 
set "NAME=image"
set /p NAME=ゲームで使う名前（半角英数字） [image]: 
if not exist "%~dp0出力" mkdir "%~dp0出力"
set "OUT=%~dp0出力\!NAME!.json"
set "OPT="
if /i "!D!"=="y" set "OPT=--dither"
echo.
echo 作っています...（大きい画像は、少し時間がかかります）
%PY% "%~dp0image_to_floor.py" "!IMG!" --size !SIZE! !OPT! -o "!OUT!" --preview "%~dp0出力\!NAME!_preview.png"
if errorlevel 1 (
  echo.
  echo 失敗しました。上のメッセージを確認してください。
  pause
  exit /b 1
)
start "" "%~dp0出力\!NAME!_preview.png"
echo.
echo できました: !OUT!
echo 完成予想の画像を開きました（ブロックの色に置きかえた見た目です）。
echo.
set "STAT=%APPDATA%\.minecraft\statues"
if exist "!STAT!" (
  set "C=y"
  set /p C=ゲームの statues フォルダにコピーしますか? （y/n） [y]: 
  if /i "!C!"=="y" (
    copy /y "!OUT!" "!STAT!\" >nul
    echo コピーしました: !STAT!
  )
) else (
  echo ゲームの statues フォルダが見つかりません。できた JSON を .minecraft\statues にコピーしてください。
)
echo.
echo ゲーム内: 敷きたい向きを向いて  /statue build !NAME! 0
echo.
pause
