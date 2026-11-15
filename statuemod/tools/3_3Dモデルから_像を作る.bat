@echo off
chcp 932 >nul
set PYTHONIOENCODING=cp932:replace
setlocal enabledelayedexpansion
cd /d "%~dp0"
echo ============================================================
echo  3D モデルから、色つきの像の JSON を作ります
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
set "MDL=%~1"
if "!MDL!"=="" (
  echo 3D モデル（glb / gltf / obj / ply / stl）を、このウィンドウにドラッグして Enter を押してください。
  set /p MDL=モデル: 
)
set "MDL=!MDL:"=!"
if not exist "!MDL!" (
  echo モデルが見つかりません: !MDL!
  pause
  exit /b 1
)
set "H=200"
set /p H=像の高さ（ブロック。150 ブロック=1m なので、身長 174cm なら 261） [200]: 
set "UP=y"
set /p UP=モデルの上の軸（y / z。Blender の OBJ などは z のことが多い） [y]: 
set "ROT=0"
set /p ROT=正面の向きの回転（0 / 90 / 180 / 270） [0]: 
set "NAME=model"
set /p NAME=ゲームで使う名前（半角英数字） [model]: 
if not exist "%~dp0出力" mkdir "%~dp0出力"
set "OUT=%~dp0出力\!NAME!.json"
echo.
echo 作っています...（少し時間がかかります）
%PY% "%~dp0model_to_statue.py" "!MDL!" --height !H! --up !UP! --rotate-y !ROT! -o "!OUT!" --preview "%~dp0出力\!NAME!_preview.png" --name !NAME!
if errorlevel 1 (
  echo.
  echo 失敗しました。上のメッセージを確認してください。
  pause
  exit /b 1
)
start "" "%~dp0出力\!NAME!_preview.png"
echo.
echo できました: !OUT!
echo 完成予想の画像（正面・右・背面・上）を開きました。
echo 向きや上下が違うときは、もう一度この bat を実行して、軸と回転を変えてください。
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
echo ゲーム内: 建てたい場所に立ち、建てたい方向を向いて  /statue build !NAME!
echo.
pause
