# ============================================================
#  左クリック検知の準備（実行者＝プレイヤー / 毎ティック）
#  ウェブシューターを構えている間だけ、当たり判定（Interaction）
#  エンティティを1体維持し、追従させる。
# ============================================================

# 恒久的なプレイヤーID（当たり判定エンティティとの紐付けに使う）を
# 未割り当てのプレイヤーにだけ割り当てる
execute unless score @s sw.pid matches 1.. run scoreboard players add #nextp sw.pid 1
execute unless score @s sw.pid matches 1.. run scoreboard players operation @s sw.pid = #nextp sw.pid

# ウェブシューターを持っていなければ、当たり判定があれば片付けて終了
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run return run function spiderweb:punch/remove

execute store result storage spiderweb:v pid int 1 run scoreboard players get @s sw.pid
function spiderweb:punch/ensure with storage spiderweb:v
