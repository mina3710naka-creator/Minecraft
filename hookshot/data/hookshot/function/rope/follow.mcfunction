# ============================================================
#  リード用アンカーの追従（マクロ / 実行者＝コウモリ）
# ============================================================

# 基本はフック（または着弾マーカー）の位置へ
$execute at @e[tag=hs.tip,scores={hs.id=$(id)},limit=1] run tp @s ~ ~ ~

# バニラのリードは 10 ブロックほどで切れるので、離れすぎたら
# プレイヤーとフックを結ぶ線上の 8 ブロック地点に留める
$execute if entity @a[tag=hs.active,scores={hs.id=$(id)},distance=8..] as @a[tag=hs.active,scores={hs.id=$(id)},limit=1] at @s anchored eyes facing entity @e[tag=hs.tip,scores={hs.id=$(id)},limit=1] feet positioned ^ ^ ^8 run tp @e[tag=hs.rope,scores={hs.id=$(id)},limit=1] ~ ~ ~

# 何かの拍子にリードが外れていたら繋ぎ直す
$execute if entity @a[tag=hs.active,scores={hs.id=$(id)}] unless data entity @s leash run data modify entity @s leash.UUID set from entity @a[tag=hs.active,scores={hs.id=$(id)},limit=1] UUID

# リードが切れて落ちたアイテムは回収させない（プレイヤーが捨てたリードは対象外）
kill @e[type=item,distance=..2,limit=2,nbt={PickupDelay:10s,Item:{id:"minecraft:lead"}}]
