# ============================================================
#  リード用アンカーの初期化（実行者＝生成されたコウモリ）
#  見えないコウモリをフック側に置き、プレイヤーとの間にリードを張る
# ============================================================
data merge entity @s {NoAI:1b,Silent:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b,BatFlags:0b,active_effects:[{id:"minecraft:invisibility",amplifier:0b,duration:-1,show_particles:0b,ambient:1b}]}
tag @s add hs.rope
tag @s add hs.ent
scoreboard players operation @s hs.id = #cur hs.id
scoreboard players set @s hs.t 0

# リードをプレイヤーに繋ぐ
data modify entity @s leash set from entity @a[tag=hs.caster,limit=1] UUID
