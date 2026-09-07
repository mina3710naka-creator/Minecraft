# ============================================================
#  伸ばす：アンカーから降りる（マクロ / 実行者＝プレイヤー）
#  1段下が塞がっている（地面に着いた）場合はそれ以上伸ばさない
# ============================================================
execute store result storage spiderweb:v lenf double 0.1 run scoreboard players get @s sw.len
$execute at @e[tag=sw.tip,scores={sw.id=$(id)},limit=1] positioned ~ ~-$(lenf) ~ positioned ~ ~-1 ~ if block ~ ~ ~ #spiderweb:passable if score @s sw.len matches ..590 run scoreboard players add @s sw.len 10
playsound minecraft:block.chain.step player @a ~ ~ ~ 0.6 0.8
