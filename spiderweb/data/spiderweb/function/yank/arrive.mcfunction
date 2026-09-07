# ============================================================
#  MOBがプレイヤーの側まで引き寄せられた（マクロ / 実行者＝MOB）
# ============================================================
particle minecraft:crit ~ ~1 ~ 0.3 0.3 0.3 0.05 10 normal @a
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 0.4 1.8
effect give @s minecraft:slowness 1 1 true

$execute as @a[tag=sw.yanking,scores={sw.id=$(id)},limit=1] run function spiderweb:release
