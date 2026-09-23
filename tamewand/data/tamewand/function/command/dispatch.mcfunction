# ============================================================
#  Shift+攻撃でMOBに命中したときの処理（実行者＝プレイヤー）
#  近くにいる自分の仲間全員に、命中した相手への攻撃を命令する
# ============================================================

# 一度もテイムしたことが無ければ、命令できる仲間もいないので何もしない
execute unless score @s tw.pid = @s tw.pid run return 0

# 命令対象（すでに自分やよその仲間になっている相手は対象外）が
# 近くにいなければ何もしない
execute unless entity @e[tag=!tw.tamed,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:trident,type=!minecraft:item_frame,type=!minecraft:glow_item_frame,type=!minecraft:painting,type=!minecraft:armor_stand,type=!minecraft:marker,type=!minecraft:leash_knot,type=!minecraft:boat,type=!minecraft:minecart,type=!minecraft:end_crystal,type=!minecraft:falling_block,type=!minecraft:ender_dragon,type=!minecraft:wither,distance=..4,limit=1,sort=nearest] run return 0

# 新しい攻撃対象IDを発行し、命中した相手に目印を付ける
execute store result storage tamewand:v tid int 1 run scoreboard players get #next tw.tid
scoreboard players add #next tw.tid 1
execute as @e[tag=!tw.tamed,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:trident,type=!minecraft:item_frame,type=!minecraft:glow_item_frame,type=!minecraft:painting,type=!minecraft:armor_stand,type=!minecraft:marker,type=!minecraft:leash_knot,type=!minecraft:boat,type=!minecraft:minecart,type=!minecraft:end_crystal,type=!minecraft:falling_block,type=!minecraft:ender_dragon,type=!minecraft:wither,distance=..4,limit=1,sort=nearest] at @s run function tamewand:command/mark_target with storage tamewand:v

# 自分のテイム済みの仲間（半径16ブロック以内）に攻撃を命令
execute store result storage tamewand:v pid int 1 run scoreboard players get @s tw.pid
function tamewand:command/order_allies with storage tamewand:v
