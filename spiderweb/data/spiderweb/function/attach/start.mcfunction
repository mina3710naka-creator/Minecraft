# ============================================================
#  糸に張り付く（マクロ / 実行者＝プレイヤー / 実行位置＝到達地点）
#  台車から降ろして片付け、以降はプレイヤー自身を直接「同じ座標へ
#  毎ティックtp」して固定する（実質的に重力を無効化する）。
# ============================================================
ride @s dismount
$execute as @e[tag=sw.carrier,scores={sw.id=$(id)}] at @s run function spiderweb:util/quiet_kill

tag @s remove sw.pulling
tag @s add sw.attached
scoreboard players set @s sw.len 0
scoreboard players set @s sw.t 0

particle minecraft:end_rod ~ ~ ~ 0.15 0.2 0.15 0.01 10 normal @a
playsound minecraft:block.scaffolding.place player @a ~ ~ ~ 1 1.2
tellraw @s [{"text":"[ウェブシューター] ","color":"aqua"},{"text":"糸に張り付いた（R:巻き取り / Shift+R:伸ばす / Space:解除してジャンプ）","color":"gray"}]
