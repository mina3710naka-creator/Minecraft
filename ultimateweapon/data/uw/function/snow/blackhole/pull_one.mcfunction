# ============================================================
#  周囲の存在をブラックホールへ引き寄せる（実行者＝引き寄せられる側）
#  中心に近いほど速く引き寄せられ、1ブロック以内で押しつぶしダメージ
#  （プレイヤーは対象外。バニラの被ダメージ無敵時間対策で
#   クールダウンを設ける）
# ============================================================
execute unless entity @e[tag=uw.blackhole,distance=..10] run return 0

execute facing entity @e[tag=uw.blackhole,distance=..10,limit=1,sort=nearest] feet run tp @s ~ ~ ~ ~ ~

scoreboard players add @s uw.cool 0
execute if entity @e[tag=uw.blackhole,distance=..1] if score @s uw.cool matches ..0 run function uw:snow/blackhole/crush_one
execute unless entity @e[tag=uw.blackhole,distance=..1] if entity @e[tag=uw.blackhole,distance=1..3] run tp @s ^ ^ ^0.5
execute unless entity @e[tag=uw.blackhole,distance=..3] run tp @s ^ ^ ^0.25
