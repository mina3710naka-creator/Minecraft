# ============================================================
#  周囲の存在をブラックホールへ引き寄せる（実行者＝引き寄せられる側）
#  中心に近いほど速く引き寄せられ、1ブロック以内で押しつぶしダメージ
#  ※毎tick同じダメージを与え続けるとバニラの被ダメージ無敵時間で
#    2回目以降が無効化されるため、クールダウン付きで間隔を空ける
# ============================================================
execute unless entity @e[tag=uw.blackhole,distance=..10] run return 0

execute facing entity @e[tag=uw.blackhole,distance=..10,limit=1,sort=nearest] feet run tp @s ~ ~ ~ ~ ~

execute if entity @e[tag=uw.blackhole,distance=..1] if score @s uw.cool matches ..0 run function uw:snow/blackhole/crush_one
execute if score @s uw.cool matches 1.. run scoreboard players remove @s uw.cool 1

execute unless entity @e[tag=uw.blackhole,distance=..1] if entity @e[tag=uw.blackhole,distance=1..3] run tp @s ^ ^ ^0.5
execute unless entity @e[tag=uw.blackhole,distance=..3] run tp @s ^ ^ ^0.25
