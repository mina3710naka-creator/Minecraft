# ============================================================
#  解除（実行者＝プレイヤー）
#  発射・引き寄せ・張り付き・MOB引き寄せ、どの状態からでも呼べる
#  共通の後始末。打ち上げジャンプはここでは行わない
#  （打ち上げが必要な場合は release_launch から先に効果を付与してここを呼ぶ）
# ============================================================
execute unless entity @s[tag=sw.active] run return 0

tag @s remove sw.active
tag @s remove sw.hooking
tag @s remove sw.webbing
tag @s remove sw.pulling
tag @s remove sw.attached
tag @s remove sw.yanking

# 台車に乗っていれば操作を返す
ride @s dismount

particle minecraft:end_rod ~ ~1 ~ 0.3 0.4 0.3 0.03 14 normal @a
particle minecraft:happy_villager ~ ~1 ~ 0.3 0.4 0.3 0 6 normal @a
playsound minecraft:item.lead.untied player @a ~ ~ ~ 0.7 1.4

# 自分のIDのフック・巣弾・マーカー・ロープ・台車・引き寄せ中MOBの後始末
execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
function spiderweb:util/cleanup with storage spiderweb:v

scoreboard players set @s sw.t 0
scoreboard players set @s sw.len 0
