# ============================================================
#  台車（コウモリ）の初期化（マクロ / 実行者＝台車 / 実行位置＝プレイヤーの位置）
# ============================================================
data merge entity @s {NoAI:1b,Silent:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b,active_effects:[{id:"minecraft:invisibility",amplifier:0b,duration:-1,show_particles:0b,ambient:1b}]}
tag @s add sw.carrier
tag @s add sw.ent

$scoreboard players set @s sw.id $(id)
# 初速1.2ブロック/ティック（ここから徐々に加速して滑らかに動く）
scoreboard players set @s sw.spd 120
scoreboard players set @s sw.pt 0

$ride @a[tag=sw.pulling,scores={sw.id=$(id)},limit=1] mount @s
