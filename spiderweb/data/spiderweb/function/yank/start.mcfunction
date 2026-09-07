# ============================================================
#  MOB引き寄せ開始（実行者＝プレイヤー）
#  台車は使わず、MOB本体を毎ティックtpステップでプレイヤー側へ
#  進める（プレイヤーは動かない）。
# ============================================================
tag @s remove sw.hooking
tag @s add sw.yanking

scoreboard players set @s sw.pt 0
scoreboard players set @s sw.t 0

playsound minecraft:item.lead.tied player @a ~ ~ ~ 0.8 1.3
