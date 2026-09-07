# ============================================================
#  解除（実行者＝プレイヤー）
#  レビテーションの上昇力だけで真上に約5マス打ち上げる
#  （opboots/hookshot と同じ、ダメージを伴わない確実な方式）
# ============================================================
execute unless entity @s[tag=sc.hooked] run return 0

tag @s remove sc.hooked
tag @s remove sc.hookflying
ride @s dismount

execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
function sc:hook/release_cleanup with storage sc:v

particle minecraft:end_rod ~ ~1 ~ 0.3 0.4 0.3 0.03 14 normal @a
particle minecraft:cloud ~ ~0.1 ~ 0.3 0.1 0.3 0.01 15
playsound minecraft:item.lead.untied player @a ~ ~ ~ 0.7 1.4
playsound minecraft:entity.ender_dragon.flap player @a ~ ~ ~ 0.6 1.6

tag @s add sc.launching
scoreboard players set @s sc.t 0
effect give @s minecraft:levitation 4 23 true
