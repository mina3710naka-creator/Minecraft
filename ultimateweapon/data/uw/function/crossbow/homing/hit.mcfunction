# ============================================================
#  着弾（実行者＝マーカー / 実行位置＝着弾地点）80ダメージ
# ============================================================
particle minecraft:explosion ~ ~ ~ 0 0 0 0 1 normal @a
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 0.6 1.6

damage @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..1.2,limit=1,sort=nearest] 80 minecraft:generic

tag @e[tag=uw.homingtgt] remove uw.homingtgt
function uw:util/quiet_kill
