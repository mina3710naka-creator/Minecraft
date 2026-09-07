# ============================================================
#  張り付き中の毎ティック処理（実行者＝プレイヤー / 実行位置＝プレイヤー）
# ============================================================

# Space -> 解除して5マスジャンプ
execute if score @s sw.jump matches 1.. run return run function spiderweb:release_launch

execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id

# 支点（アンカー）が消えていたら解除
$execute unless entity @e[tag=sw.tip,scores={sw.id=$(id)}] run return run function spiderweb:release

# Rキー：Shiftを押していれば伸ばす、押していなければ巻き取る
execute if score @s sw.rkey matches 1.. if predicate spiderweb:sneaking run function spiderweb:attach/extend with storage spiderweb:v
execute if score @s sw.rkey matches 1.. unless predicate spiderweb:sneaking run function spiderweb:attach/reel with storage spiderweb:v

# 現在の糸の長さで位置を固定し直す（重力で落ちないよう毎ティック固定）
function spiderweb:attach/hold with storage spiderweb:v
