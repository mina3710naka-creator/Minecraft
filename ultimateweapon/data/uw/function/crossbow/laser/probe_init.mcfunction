# ============================================================
#  レーザー終点用のこうもりを初期化（実行者＝生成されたこうもり）
#  視点方向へ最大100ブロック、瞬時にレイキャストして止まった位置に固定する
# ============================================================
tp @s ~ ~ ~ ~ ~
data merge entity @s {NoAI:1b,Invisible:1b,Invulnerable:1b,Silent:1b,NoGravity:1b,Health:20.0f}
effect give @s minecraft:invisibility 1000000 0 true
tag @s add uw.laserbat
tag @s add uw.ent

scoreboard players set @s uw.sub 100
function uw:crossbow/laser/probe_step
