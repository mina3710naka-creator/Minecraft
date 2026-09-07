# ============================================================
#  リード用アンカーの初期化（実行者＝生成されたコウモリ）
#  見えないコウモリを弾側に置き、プレイヤーとの間にリードを張る
# ============================================================
data merge entity @s {NoAI:1b,Silent:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b,BatFlags:0b,active_effects:[{id:"minecraft:invisibility",amplifier:0b,duration:-1,show_particles:0b,ambient:1b}]}
tag @s add sw.rope
tag @s add sw.ent
scoreboard players operation @s sw.id = #cur sw.id
scoreboard players set @s sw.t 0

data modify entity @s leash.UUID set from entity @a[tag=sw.caster,limit=1] UUID
