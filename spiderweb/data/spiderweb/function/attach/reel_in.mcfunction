# 巻き取り：支点に向かって登る（実行者＝プレイヤー）
scoreboard players remove @s sw.len 10
execute if score @s sw.len matches ..0 run scoreboard players set @s sw.len 0
playsound minecraft:block.chain.step player @a ~ ~ ~ 0.6 1.8
