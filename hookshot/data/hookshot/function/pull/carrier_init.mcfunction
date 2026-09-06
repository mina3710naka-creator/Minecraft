# ============================================================
#  台車（コウモリ）の初期化（マクロ / 実行者＝台車 / 実行位置＝プレイヤーの位置）
#  NoAI でコウモリ自身の（ランダムに飛び回る）AI だけを止める。
#  防具立てではなくコウモリを使っているのは、実体としての当たり判定を
#  持たせておくため（tp の移動先チェックに加え、二重の安全策になる）。
# ============================================================
data merge entity @s {NoAI:1b,Silent:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b,active_effects:[{id:"minecraft:invisibility",amplifier:0b,duration:-1,show_particles:0b,ambient:1b}]}
tag @s add hs.carrier
tag @s add hs.ent

$scoreboard players set @s hs.id $(id)
# 初速 0.6 ブロック / ティック（ここから徐々に加速して滑らかに動く）
scoreboard players set @s hs.spd 60
scoreboard players set @s hs.pt 0

# 詰まり検出（実際に座標が進んでいるかの直接比較）用に、現在位置を記録
execute store result score @s hs.lx run data get entity @s Pos[0] 100
execute store result score @s hs.ly run data get entity @s Pos[1] 100
execute store result score @s hs.lz run data get entity @s Pos[2] 100
scoreboard players set @s hs.stc 0

# プレイヤーをこの台車に乗せる（乗っている間、WASD 等の自力移動は効かなくなる）
$ride @a[tag=hs.pulling,scores={hs.id=$(id)},limit=1] mount @s
