# ============================================================
#  振り子用の見えない台車（コウモリ）の初期化
#  （マクロ / 実行者＝台車 / 実行位置＝プレイヤーの位置）
#  綱の初期長さは着弾までの距離（sc.range は 0.25 ブロック単位）
# ============================================================
data merge entity @s {NoAI:1b,Silent:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b,BatFlags:0b,active_effects:[{id:"minecraft:invisibility",amplifier:0b,duration:-1,show_particles:0b,ambient:1b}]}
tag @s add sc.carrier
tag @s add sc.ent

$scoreboard players set @s sc.id $(id)

$scoreboard players set @s sc.rope $(range)
execute if score @s sc.rope matches ..11 run scoreboard players set @s sc.rope 12
execute if score @s sc.rope matches 241.. run scoreboard players set @s sc.rope 240

$scoreboard players set @s sc.theta $(theta)
$scoreboard players set @s sc.phi $(phi)
execute if score @s sc.phi matches ..14 run scoreboard players set @s sc.phi 15
execute if score @s sc.phi matches 86.. run scoreboard players set @s sc.phi 85

$ride @a[tag=sc.hooked,scores={sc.id=$(id)},limit=1] mount @s
