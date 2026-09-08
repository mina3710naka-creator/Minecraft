# ============================================================
#  Shift+左クリック: 衝撃波（実行者＝プレイヤー）
#  自分を中心に半径0→18ブロックまで、15tick（0.75秒）かけて
#  リング状に広がる衝撃波を発生させる
# ============================================================
title @s actionbar {"text":"衝撃波！","color":"gold","bold":true}
playsound minecraft:entity.warden.sonic_boom player @a ~ ~ ~ 1 1.2
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 0.6 0.8
particle minecraft:explosion ~ ~0.1 ~ 0 0 0 0 1 normal @a

tag @e[tag=uw.shockhit] remove uw.shockhit
execute positioned ~ ~ ~ summon minecraft:marker run function uw:sword/shockwave/init
