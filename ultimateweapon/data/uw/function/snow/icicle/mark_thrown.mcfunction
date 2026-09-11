# ============================================================
#  つらら→氷結レーザーに変更（実行者＝プレイヤー）
#  投げた瞬間（発動した瞬間）に本物の雪玉を即座にkillし、飛ばして
#  着弾を待つことはしない。代わりに周囲20ブロックの全MOBへ同時に
#  氷結レーザーを飛ばしてダメージを与える
# ============================================================
kill @e[type=minecraft:snowball,tag=!uw.sbtagged,distance=..3,limit=1,sort=nearest]
playsound minecraft:entity.glow_squid.ambient player @a ~ ~ ~ 1 0.6
playsound minecraft:block.glass.break player @a ~ ~ ~ 1 0.7
title @s actionbar {"text":"氷結レーザー発射！","color":"aqua","bold":true}

execute as @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..20] run function uw:snow/icicle/laser_hit

execute as @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..20] run tag @s add uw.icetgt
function uw:snow/icicle/laser_beams
