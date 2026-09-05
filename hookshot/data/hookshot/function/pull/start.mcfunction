# ============================================================
#  引き寄せ開始（実行者＝プレイヤー）
# ============================================================
tag @s remove hs.hooking
tag @s add hs.pulling
scoreboard players set @s hs.pt 0
# 初速 0.25 ブロック / ティック（ここから徐々に加速して滑らかに動く）
scoreboard players set @s hs.spd 25
playsound minecraft:item.lead.tied player @a ~ ~ ~ 0.8 1.3
