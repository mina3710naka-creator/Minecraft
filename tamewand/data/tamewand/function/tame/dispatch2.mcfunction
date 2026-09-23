# ============================================================
#  テイム処理の続き（マクロ / 実行者＝プレイヤー、tame/dispatch.mcfunction
#  から $(pid) を積んだストレージ付きで呼ばれる）
# ============================================================

# 現在の所有数をその場で数え直す（キル等による死骸カウントのズレが
# 起きないよう、スコアで持ち回さずタグの付与数を毎回数える。
# tag add はマッチした実体数をそのまま結果として返す性質を利用している）
$execute store result score #tw_cnt tw.tmp run tag @e[tag=tw.tamed,scores={tw.owner=$(pid)},limit=6] add tw.counted
tag @e[tag=tw.counted] remove tw.counted

execute if score #tw_cnt tw.tmp matches 5.. run tellraw @s [{"text":"[テイムの杖] ","color":"light_purple"},{"text":"これ以上は仲間にできません（同時に最大5体まで）","color":"red"}]
execute if score #tw_cnt tw.tmp matches 5.. run return 0

execute as @e[tag=!tw.tamed,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:trident,type=!minecraft:item_frame,type=!minecraft:glow_item_frame,type=!minecraft:painting,type=!minecraft:armor_stand,type=!minecraft:marker,type=!minecraft:leash_knot,type=!minecraft:boat,type=!minecraft:minecart,type=!minecraft:end_crystal,type=!minecraft:falling_block,type=!minecraft:ender_dragon,type=!minecraft:wither,distance=..4,limit=1,sort=nearest] at @s run function tamewand:tame/apply with storage tamewand:v
