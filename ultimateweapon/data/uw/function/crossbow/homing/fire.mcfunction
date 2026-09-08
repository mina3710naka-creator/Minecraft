# ============================================================
#  モード1: 自動追尾弾の発射（実行者＝プレイヤー）
#  視点方向に一番近いMOBをロックオンしてから矢を放つ
# ============================================================
playsound minecraft:entity.arrow.shoot player @a ~ ~ ~ 1 0.6
particle minecraft:crit ~ ~1 ~ 0.2 0.2 0.2 0.05 8 normal @a

tag @e[tag=uw.homingtgt] remove uw.homingtgt
scoreboard players set @s uw.sub 20
execute anchored eyes run function uw:crossbow/homing/scan_step

execute anchored eyes positioned ^ ^ ^0.5 summon minecraft:item run function uw:crossbow/homing/init
