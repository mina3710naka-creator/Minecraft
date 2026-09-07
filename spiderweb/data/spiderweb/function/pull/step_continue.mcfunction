# ============================================================
#  台車の1ステップ移動後の共通処理（マクロ / 実行者＝台車）
# ============================================================
$execute at @s if entity @e[tag=sw.anchor,scores={sw.id=$(id)},distance=..0.2] run return run function spiderweb:pull/arrive with storage spiderweb:v

scoreboard players remove @s sw.sub 1
execute if score @s sw.sub matches 1.. at @s run function spiderweb:pull/step with storage spiderweb:v
