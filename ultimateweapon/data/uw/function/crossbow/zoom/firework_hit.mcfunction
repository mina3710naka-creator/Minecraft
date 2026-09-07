# ============================================================
#  着弾: エンドクリスタル2個を起爆する（実行者＝マーカー / 実行位置＝着弾地点）
# ============================================================
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 1 0.8

summon minecraft:end_crystal ~-0.3 ~ ~ {ShowBottom:0b,Tags:["uw.crystal_burst"]}
summon minecraft:end_crystal ~0.3 ~ ~ {ShowBottom:0b,Tags:["uw.crystal_burst"]}
damage @e[type=minecraft:end_crystal,tag=uw.crystal_burst,limit=2,sort=nearest] 1 minecraft:generic

execute as @e[tag=uw.locktgt] run effect clear @s minecraft:glowing
tag @e[tag=uw.locktgt] remove uw.locktgt

function uw:util/quiet_kill
