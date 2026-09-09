# ============================================================
#  着弾: 半径5ブロック以内のMOBを20秒間行動不可にする
#  （実行者＝マーカー / 実行位置＝着弾地点）
# ============================================================
playsound minecraft:block.glass.break player @a ~ ~ ~ 1 0.6
playsound minecraft:block.powder_snow.break player @a ~ ~ ~ 1 0.8
playsound minecraft:block.glass.break player @a ~ ~ ~ 1 1.4
# 着弾した瞬間に半径5ブロック全体へ一回だけ氷のエフェクトを広げる
particle minecraft:snowflake ~ ~1 ~ 5 2 5 0.08 200 normal @a
particle minecraft:cloud ~ ~1 ~ 4.5 1.5 4.5 0.03 80 normal @a
particle minecraft:item_snowball ~ ~1 ~ 4.5 1.5 4.5 0 30 normal @a
particle minecraft:block minecraft:ice ~ ~1 ~ 4.5 1.5 4.5 0.1 60 normal @a

execute as @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..5] at @s run function uw:snow/freeze/apply_one

function uw:util/quiet_kill
