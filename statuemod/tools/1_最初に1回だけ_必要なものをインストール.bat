@echo off
chcp 932 >nul
set PYTHONIOENCODING=cp932:replace
cd /d "%~dp0"
echo ============================================================
echo  像づくりツールに必要なものを、まとめてインストールします（最初に 1 回だけ）
echo ============================================================
echo.
set "PY="
for %%C in (py python python3) do (
  if not defined PY (
    %%C -c "import sys;print(sys.version_info[0])" 2>nul | findstr /x "3" >nul && set "PY=%%C"
  )
)
if not defined PY (
  echo Python が見つかりません。インストールを試みます。
  winget --version >nul 2>&1
  if errorlevel 1 (
    echo.
    echo winget が使えないので、ブラウザで Python のダウンロードページを開きます。
    echo インストーラーの最初の画面で、「Add python.exe to PATH」にチェックを入れてから Install Now を押してください。
    echo インストールが終わったら、このウィンドウを閉じて、もう一度この bat をダブルクリックしてください。
    start "" "https://www.python.org/downloads/"
    echo.
    pause
    exit /b 1
  )
  winget install -e --id Python.Python.3.12 --accept-package-agreements --accept-source-agreements
  echo.
  echo Python のインストールが終わったら、このウィンドウを閉じて、もう一度この bat をダブルクリックしてください。
  echo.
  pause
  exit /b 0
)
echo Python が見つかりました: %PY%
%PY% --version
echo.
echo ライブラリをインストールします（pillow, numpy, trimesh）...
%PY% -m pip install --upgrade pip
%PY% -m pip install --upgrade pillow numpy trimesh
if errorlevel 1 (
  echo.
  echo インストールに失敗しました。上のメッセージを確認してください。
  pause
  exit /b 1
)
echo.
%PY% -c "import PIL, numpy, trimesh; print('確認 OK: pillow / numpy / trimesh が使えます')"
echo.
echo 準備ができました。次は
echo   2_画像から_地面に敷く像を作る.bat   （画像用）
echo   3_3Dモデルから_像を作る.bat        （3D モデル用）
echo を使ってください。
echo.
pause
