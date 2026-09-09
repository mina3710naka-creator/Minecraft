# ============================================================
#  モード1: マシンガン（実行者＝プレイヤー）
#  このモードでクロスボウを持っている間、矢を装填済みのクロスボウに
#  常に置き換え続けることで、構える動作なしで連射できるようにする
# ============================================================
execute unless score @s uw.mode matches 0 run return 0

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_crossbow:1b},!minecraft:charged_projectiles] run function uw:crossbow/machinegun/reload_main
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_crossbow:1b},!minecraft:charged_projectiles] run function uw:crossbow/machinegun/reload_off
