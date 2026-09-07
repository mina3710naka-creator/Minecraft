# ============================================================
#  張り付き中の毎ティック処理（実行者＝プレイヤー / 実行位置＝プレイヤー）
# ============================================================

# Space -> 解除して5マスジャンプ
execute if score @s sw.jump matches 1.. run return run function spiderweb:release_jump

execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
function spiderweb:attach/tick_do with storage spiderweb:v
