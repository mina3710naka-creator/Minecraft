# ============================================================
#  MOB 追従用アンカー（見えないコウモリ）の初期化
#  実行者＝生成されたコウモリ
#  Marker エンティティは乗り物に乗れない（乗せられない）仕様なので、
#  台車やリードのアンカーと同じ「見えないコウモリ」を使い、これを
#  刺さった MOB に ride で乗せることで位置を自動追従させる
# ============================================================
data merge entity @s {NoAI:1b,Silent:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b,BatFlags:0b,active_effects:[{id:"minecraft:invisibility",amplifier:0b,duration:-1,show_particles:0b,ambient:1b}]}
tag @s add hs.anchor
tag @s add hs.tip
tag @s add hs.ent
scoreboard players operation @s hs.id = #cur hs.id
scoreboard players set @s hs.t 0
