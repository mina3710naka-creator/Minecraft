# ============================================================
#  モード1: マシンガン（実行者＝プレイヤー）
#  このモードでクロスボウを持っている間、矢を装填済みのクロスボウに
#  常に置き換え続けることで、構える動作なしで連射できるようにする
#  ※アイテムのcharged_projectilesコンポーネントの有無を直接調べる
#    ([!minecraft:charged_projectiles] のような否定記法)は版によって
#    正しく機能しない可能性があるため使わず、自前のスコア
#    （uw.mgloaded）で「今装填済みを渡したかどうか」を管理する
# ============================================================
execute unless score @s uw.mode matches 0 run return 0
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{uw_crossbow:1b}] run return 0
execute if score @s uw.mgloaded matches 1 run return 0

function uw:crossbow/machinegun/reload_main
scoreboard players set @s uw.mgloaded 1
