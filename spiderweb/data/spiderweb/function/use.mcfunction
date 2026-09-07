# ============================================================
#  クロスボウ（ウェブシューター）を撃ったときの処理（実行者＝プレイヤー）
#  arrow_seen.mcfunction が「矢が出現し、持ち主がウェブシューターを
#  構えている」ことを確認した上で呼び出す。
# ============================================================

# --- 発射で実体化した矢を消す（見た目だけ矢、実体は自作の弾に差し替える） ---
# すぐ足元に出現したばかり（sw.seen付きでまだ1ブロック以内）の矢だけを対象にする
kill @e[type=arrow,tag=sw.seen,tag=!sw.ent,distance=..1,limit=1,sort=nearest]

# --- すでに使用中なら、もう一度の使用で解除（トグル） ---
execute if entity @s[tag=sw.active] run return run function spiderweb:release

# --- モードに応じて発射 ---
execute if score @s sw.mode matches 0 run function spiderweb:fire_grapple
execute if score @s sw.mode matches 1 run function spiderweb:fire_web
