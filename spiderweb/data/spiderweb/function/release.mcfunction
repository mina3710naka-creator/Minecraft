# ============================================================
#  解除（実行者＝プレイヤー）
#  発射中・引き寄せ中・張り付き中・MOB引き寄せ中、どの状態からでも
#  呼べる共通の後始末。打ち上げジャンプはここでは行わない
#  （打ち上げが必要なときは release_jump が先に効果を付与してから
#  ここを呼ぶ）。
# ============================================================
execute unless entity @s[tag=sw.active] run return 0

tag @s remove sw.active
tag @s remove sw.hooking
tag @s remove sw.webbing
tag @s remove sw.pulling
tag @s remove sw.attached
tag @s remove sw.yanking

# 台車に乗っていれば操作を返す（台車自体はutil/cleanupがID一致で片付ける）
ride @s dismount

particle minecraft:end_rod ~ ~1 ~ 0.3 0.4 0.3 0.03 14 normal @a
particle minecraft:happy_villager ~ ~1 ~ 0.3 0.4 0.3 0 6 normal @a
playsound minecraft:item.lead.untied player @a ~ ~ ~ 0.7 1.4

# このIDのフック・巣づくり弾・マーカー・ロープ・台車・引き寄せ中MOBを片付ける
execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
function spiderweb:util/cleanup with storage spiderweb:v

scoreboard players set @s sw.t 0
scoreboard players set @s sw.len 0
