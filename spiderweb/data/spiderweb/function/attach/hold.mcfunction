# ============================================================
#  現在の糸の長さ（sw.len、0.1ブロック単位）ぶんアンカーの真下へ
#  毎ティック固定し直す（実行者＝プレイヤー）
# ============================================================
execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
execute store result storage spiderweb:v lenf double 0.1 run scoreboard players get @s sw.len
function spiderweb:attach/hold_do with storage spiderweb:v
