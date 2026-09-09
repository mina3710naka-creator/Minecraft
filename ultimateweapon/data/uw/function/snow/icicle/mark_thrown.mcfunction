# 直前に投げた本物の雪玉を探してマークする（実行者＝プレイヤー）
execute as @e[type=minecraft:snowball,tag=!uw.sbtagged,distance=..3,limit=1,sort=nearest] at @s run function uw:snow/icicle/mark_one
