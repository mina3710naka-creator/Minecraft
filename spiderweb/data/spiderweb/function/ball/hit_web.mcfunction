# ============================================================
#  クモの巣に命中（実行者＝防具立て / 実行位置＝弾の現在地）
#  1ブロック先（進行方向）のクモの巣を起点に、繋がっている塊ごと除去する
# ============================================================
particle minecraft:end_rod ~ ~ ~ 0.2 0.2 0.2 0.02 10 normal @a
playsound minecraft:block.cobweb.break block @a ~ ~ ~ 1 1

execute positioned ^ ^ ^0.25 run function spiderweb:webclear/start

execute if entity @s run kill @s
