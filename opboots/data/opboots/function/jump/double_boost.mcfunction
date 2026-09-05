# ============================================================
#  二段ジャンプの発動（実行者＝プレイヤー）
# ============================================================
tag @s add ob.dj_used
scoreboard players set @s ob.vv 45

playsound minecraft:entity.bat.takeoff master @s ~ ~ ~ 1 1.4
particle minecraft:cloud ~ ~ ~ 0.25 0.1 0.25 0.02 8 force @s

function opboots:jump/launch
