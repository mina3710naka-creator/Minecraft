# ============================================================
#  着弾（実行者＝防具立て / 実行位置＝着弾地点）
#  MOBに刺さった場合はそのMOBを自分側へ「引き寄せる」(yank)。
#  それ以外（ブロックなど）に刺さった場合はマーカーを固定し、
#  自分がそこへ「引き寄せられる」(pull)。
# ============================================================
particle minecraft:crit ~ ~ ~ 0.15 0.15 0.15 0.06 16 normal @a
particle minecraft:end_rod ~ ~ ~ 0.1 0.1 0.1 0.02 12 normal @a
particle minecraft:happy_villager ~ ~ ~ 0.2 0.2 0.2 0 8 normal @a
playsound minecraft:block.chain.place block @a ~ ~ ~ 1 1.5

execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id

# --- ブロックなどに刺さった場合：マーカーを固定し、持ち主を引き寄せ状態へ ---
scoreboard players operation #cur sw.id = @s sw.id
execute unless entity @e[tag=!sw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:end_crystal,distance=..3] summon minecraft:marker run function spiderweb:hook/anchor
execute unless entity @e[tag=!sw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:end_crystal,distance=..3] run function spiderweb:hook/hit_block with storage spiderweb:v

# --- MOBに刺さった場合：そのMOBを引き寄せ対象にする ---
execute as @e[tag=!sw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:end_crystal,distance=..3,limit=1,sort=nearest] at @s run function spiderweb:hook/hit_mob with storage spiderweb:v

# フック本体（防具立て）は役目を終える
execute if entity @s run kill @s
