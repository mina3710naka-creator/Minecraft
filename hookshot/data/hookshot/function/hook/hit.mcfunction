# ============================================================
#  着弾（実行者＝防具立て / 実行位置＝着弾地点）
#  その場にマーカーを固定し、プレイヤーを引き寄せ状態にする
# ============================================================
particle minecraft:crit ~ ~ ~ 0.15 0.15 0.15 0.06 16 normal @a
particle minecraft:end_rod ~ ~ ~ 0.1 0.1 0.1 0.02 12 normal @a
particle minecraft:happy_villager ~ ~ ~ 0.2 0.2 0.2 0 8 normal @a
playsound minecraft:block.chain.place block @a ~ ~ ~ 1 1.5

# 着弾地点に固定されるマーカー
scoreboard players operation #cur hs.id = @s hs.id
execute summon minecraft:marker run function hookshot:hook/anchor_init

# 持ち主を引き寄せ状態へ
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
function hookshot:hook/hit_owner with storage hookshot:v

# フック本体（防具立て）は役目を終える
execute if entity @s run kill @s
