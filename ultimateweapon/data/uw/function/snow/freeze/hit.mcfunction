# ============================================================
#  着弾: 半径5ブロック以内のMOBを20秒間行動不可にする
#  （実行者＝マーカー / 実行位置＝着弾地点）
#  範囲全体を覆うように、氷のエフェクトを一度だけ広範囲に発生させる
# ============================================================
playsound minecraft:block.glass.break player @a ~ ~ ~ 1 0.6
playsound minecraft:block.powder_snow.break player @a ~ ~ ~ 1 0.8
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 0.7 1.4

particle minecraft:explosion ~ ~ ~ 0 0 0 0 1 normal @a
particle minecraft:snowflake ~ ~0.5 ~ 5 1.5 5 0.05 200 normal @a
particle minecraft:cloud ~ ~0.3 ~ 5 1 5 0.02 60 normal @a
particle minecraft:block minecraft:snow_block ~ ~0.2 ~ 5 0.5 5 0.1 100 normal @a
particle minecraft:block minecraft:ice ~ ~0.2 ~ 5 0.5 5 0.1 60 normal @a
particle minecraft:item_slime ~ ~0.5 ~ 5 1 5 0 20 normal @a

execute as @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..5] at @s run function uw:snow/freeze/apply_one

function uw:util/quiet_kill
