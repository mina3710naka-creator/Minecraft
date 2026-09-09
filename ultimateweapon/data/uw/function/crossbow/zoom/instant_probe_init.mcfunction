# 即時ロックオン用こうもりの初期化（実行者＝生成されたこうもり）
data merge entity @s {NoAI:1b,Invisible:1b,Invulnerable:1b,Silent:1b,NoGravity:1b,Health:20.0f}
effect give @s minecraft:invisibility 1000000 0 true
tag @s add uw.ent
scoreboard players set @s uw.sub 100
function uw:crossbow/zoom/instant_probe_step
