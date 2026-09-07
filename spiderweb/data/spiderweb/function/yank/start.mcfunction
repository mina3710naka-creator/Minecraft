# ============================================================
#  MOB引き寄せ開始（マクロ / 実行者＝プレイヤー / 実行位置＝プレイヤー）
#  台車は使わず、MOB本体を毎ティックtpステップでプレイヤー側へ進める。
# ============================================================
tag @s remove sw.hooking
tag @s add sw.yanking

scoreboard players set @s sw.pt 0
scoreboard players set @s sw.t 0

playsound minecraft:item.lead.tied player @a ~ ~ ~ 0.8 1.3
