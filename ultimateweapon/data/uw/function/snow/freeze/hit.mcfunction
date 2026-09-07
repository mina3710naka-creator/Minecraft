# ============================================================
#  着弾: 半径5ブロック以内のMOBを20秒間行動不可にする
#  （実行者＝マーカー / 実行位置＝着弾地点）
# ============================================================
playsound minecraft:block.glass.break player @a ~ ~ ~ 1 0.6
playsound minecraft:block.powder_snow.break player @a ~ ~ ~ 1 0.8
particle minecraft:snowflake ~ ~ ~ 2 1 2 0.05 60 normal @a
particle minecraft:cloud ~ ~ ~ 1.5 1 1.5 0.02 20 normal @a

execute as @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..5] at @s run function uw:snow/freeze/apply_one

function uw:util/quiet_kill
