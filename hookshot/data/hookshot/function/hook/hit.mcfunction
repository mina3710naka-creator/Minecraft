# ============================================================
#  着弾（実行者＝防具立て / 実行位置＝着弾地点）
#  MOB に刺さった場合はその MOB にアンカーを追従させ、動いた先へ向きを
#  変えながら引き寄せられるようにする。それ以外はその場にマーカーを
#  固定し、プレイヤーを引き寄せ状態にする
# ============================================================
particle minecraft:crit ~ ~ ~ 0.15 0.15 0.15 0.06 16 normal @a
particle minecraft:end_rod ~ ~ ~ 0.1 0.1 0.1 0.02 12 normal @a
particle minecraft:happy_villager ~ ~ ~ 0.2 0.2 0.2 0 8 normal @a
playsound minecraft:block.chain.place block @a ~ ~ ~ 1 1.5

scoreboard players operation #cur hs.id = @s hs.id
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id

# MOB に刺さった場合は追従アンカー（見えないコウモリ）をその MOB に乗せ、
# そうでなければ従来通りマーカーを着弾地点に固定する
execute as @e[tag=!hs.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:fishing_bobber,type=!minecraft:end_crystal,distance=..3,limit=1,sort=nearest] at @s run function hookshot:hook/anchor_to_mob with storage hookshot:v
execute unless entity @e[tag=!hs.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:fishing_bobber,type=!minecraft:end_crystal,distance=..3] summon minecraft:marker run function hookshot:hook/anchor_init

# 持ち主を引き寄せ状態へ
function hookshot:hook/hit_owner with storage hookshot:v

# フック本体（防具立て）は役目を終える
execute if entity @s run kill @s
