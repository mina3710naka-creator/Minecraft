# ============================================================
#  張り付き中の毎ティック処理の本体（マクロ / 実行者＝プレイヤー）
# ============================================================

# 支点（アンカーや引き寄せ中MOB）が消えていたら解除する
$execute unless entity @e[tag=sw.tip,scores={sw.id=$(id)}] run return run function spiderweb:release

# Rキー：Shiftを押していれば伸ばす、押していなければ巻き取る
execute if score @s sw.rkey matches 1.. if predicate spiderweb:sneaking run function spiderweb:attach/extend with storage spiderweb:v
execute if score @s sw.rkey matches 1.. unless predicate spiderweb:sneaking run function spiderweb:attach/reel_in

# 現在の糸の長さで位置を固定し直す（毎ティック固定して重力に落とされないようにする）
execute store result storage spiderweb:v lenf double 0.1 run scoreboard players get @s sw.len
function spiderweb:attach/hold with storage spiderweb:v
