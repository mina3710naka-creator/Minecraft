# ============================================================
#  自動追尾弾の毎ティック処理（実行者＝マーカー / 実行位置＝マーカー）
#  最大寿命 100 tick（5秒）、1 tick に 4 ブロック進む
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 100.. run function uw:util/quiet_kill

# 追尾: 一番近い生物へ向きを合わせる（射手や矢・演出用エンティティは除外）
execute if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:snowball,type=!minecraft:trident,type=!minecraft:firework_rocket,type=!minecraft:end_crystal,distance=..40,limit=1,sort=nearest] facing entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:snowball,type=!minecraft:trident,type=!minecraft:firework_rocket,type=!minecraft:end_crystal,distance=..40,limit=1,sort=nearest] feet run tp @s ~ ~ ~ ~ ~

scoreboard players set @s uw.sub 4
function uw:crossbow/homing/step
