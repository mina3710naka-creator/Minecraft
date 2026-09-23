# ============================================================
#  1体の仲間を攻撃モードへ切り替える
#  （マクロ / 実行者＝その仲間 / 実行位置＝その仲間）
# ============================================================
$scoreboard players set @s tw.aim $(tid)
scoreboard players set @s tw.mode 1
scoreboard players set @s tw.cd 0

particle minecraft:crit ~ ~1 ~ 0.15 0.2 0.15 0 4 normal @a
