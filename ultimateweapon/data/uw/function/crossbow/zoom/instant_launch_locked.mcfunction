# ロックオン対象へ向けて発射（実行者＝プレイヤー）
effect give @e[tag=uw.locktgt,limit=1,sort=nearest] minecraft:glowing 2 0 true
execute anchored eyes facing entity @e[tag=uw.locktgt,limit=1,sort=nearest] feet positioned ^ ^ ^0.6 summon minecraft:item run function uw:crossbow/zoom/firework_init
tag @e[tag=uw.locktgt] remove uw.locktgt
