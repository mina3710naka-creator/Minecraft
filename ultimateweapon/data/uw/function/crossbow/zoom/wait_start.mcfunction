# ============================================================
#  本当にズームし始めるまでの待機（実行者＝プレイヤー）
#  minecraft:using_item が実際に発火するまでは何もしない
# ============================================================
execute unless score @s uw.using matches 1.. run title @s actionbar {"text":"スパイグラスを右クリック長押しでズーム","color":"gray"}
execute unless score @s uw.using matches 1.. run return 0

tag @s add uw.zoomactive
playsound minecraft:item.spyglass.use player @s ~ ~ ~ 1 1.4
particle minecraft:end_rod ~ ~1.6 ~ 0.1 0.1 0.1 0 6 normal @s

# ※ positioned ^ ^ ^0 が無いと視点ではなく足元付近に出現してしまう
execute anchored eyes positioned ^ ^ ^0 summon minecraft:bat run function uw:crossbow/zoom/probe_init
