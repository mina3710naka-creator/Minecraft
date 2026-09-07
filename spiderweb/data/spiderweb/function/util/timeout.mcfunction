# ============================================================
#  プレイヤー側の安全装置（発射から8秒で強制解除）
#  ※糸に張り付いている間（sw.attached）は、Rやスペースで自分から
#    解除するまでずっと張り付いていられる仕様なので対象外にする。
# ============================================================
scoreboard players add @s sw.t 1
execute unless entity @s[tag=sw.attached] if score @s sw.t matches 160.. run function spiderweb:release
