# ロックオン対象へ向けて花火を発射（実行者＝プレイヤー）
execute anchored eyes facing entity @e[tag=uw.locktgt,limit=1,sort=nearest] feet positioned ^ ^ ^0.6 summon minecraft:item run function uw:crossbow/zoom/firework_init
