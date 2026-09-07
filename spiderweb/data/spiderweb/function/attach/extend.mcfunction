# ============================================================
#  伸ばす：支点から降りる（マクロ / 実行者＝プレイヤー）
#  伸ばした先の1段下がすでに塞がっている（地面に着く）場合は
#  それ以上伸ばさない。60ブロックで頭打ちにする。
# ============================================================
$execute at @e[tag=sw.tip,scores={sw.id=$(id)},limit=1] positioned ~ ~-$(lenf) ~ positioned ~ ~-1 ~ if block ~ ~ ~ #spiderweb:passable if score @s sw.len matches ..590 run scoreboard players add @s sw.len 10
playsound minecraft:block.chain.step player @a ~ ~ ~ 0.6 0.8
