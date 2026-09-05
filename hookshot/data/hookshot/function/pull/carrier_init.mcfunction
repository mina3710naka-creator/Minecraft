# ============================================================
#  台車（防具立て）の初期化（マクロ / 実行者＝台車 / 実行位置＝プレイヤーの位置）
#  Marker は付けない（＝実体としての当たり判定と Motion による移動を持たせる）。
#  これにより壁への衝突はゲーム本来の物理でブロックされ、めり込まなくなる。
# ============================================================
data merge entity @s {Invisible:1b,Invulnerable:1b,Silent:1b,NoGravity:1b,Marker:0b,NoBasePlate:1b}
tag @s add hs.carrier
tag @s add hs.ent

$scoreboard players set @s hs.id $(id)
# 初速 0.2 ブロック / ティック（ここから徐々に加速して滑らかに動く）
scoreboard players set @s hs.spd 20
scoreboard players set @s hs.pt 0

# プレイヤーをこの台車に乗せる（乗っている間、WASD 等の自力移動は効かなくなる）
$ride @a[tag=hs.pulling,scores={hs.id=$(id)},limit=1] mount @s
