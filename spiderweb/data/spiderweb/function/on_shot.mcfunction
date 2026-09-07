# ============================================================
#  ウェブシューターを実際に撃った瞬間の処理（実行者＝プレイヤー / 実行位置＝プレイヤー）
#  detect_shot.mcfunction が「矢が出現し、持ち主がウェブシューターを
#  構えている」ことを確認した上で呼び出す。
# ============================================================

# 見た目だけの矢（実体はこの後こちらで射出する自作の弾）を消す。
# 出現直後でまだ足元1ブロック以内にあるものだけを対象にする。
kill @e[type=minecraft:arrow,tag=sw.seen,distance=..1,limit=1,sort=nearest]

# 既に使用中なら、もう一度の使用でその場を解除する（トグル）
execute if entity @s[tag=sw.active] run return run function spiderweb:release

# モードに応じて発射
execute if score @s sw.mode matches 0 run function spiderweb:fire_hook
execute if score @s sw.mode matches 1 run function spiderweb:fire_web
