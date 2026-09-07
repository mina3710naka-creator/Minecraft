# ============================================================
#  リード用アンカーの追従（マクロ / 実行者＝コウモリ）
# ============================================================

# 基本は弾（またはアンカー・引き寄せ中のMOB）の位置へ
$execute at @e[tag=sw.tip,scores={sw.id=$(id)},limit=1] run tp @s ~ ~ ~

# バニラのリードは10ブロックほどで切れるので、離れすぎたら
# プレイヤーと対象を結ぶ線上の8ブロック地点に留める
$execute if entity @a[tag=sw.active,scores={sw.id=$(id)},distance=8..] as @a[tag=sw.active,scores={sw.id=$(id)},limit=1] at @s anchored eyes facing entity @e[tag=sw.tip,scores={sw.id=$(id)},limit=1] feet positioned ^ ^ ^8 run tp @e[tag=sw.rope,scores={sw.id=$(id)},limit=1] ~ ~ ~

# 何かの拍子にリードが外れていたら繋ぎ直す
$execute if entity @a[tag=sw.active,scores={sw.id=$(id)}] unless data entity @s leash run data modify entity @s leash.UUID set from entity @a[tag=sw.active,scores={sw.id=$(id)},limit=1] UUID

# リードが切れて落ちたアイテムは回収させない
kill @e[type=item,distance=..2,limit=2,nbt={PickupDelay:10s,Item:{id:"minecraft:lead"}}]
