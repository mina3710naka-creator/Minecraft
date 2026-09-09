# ============================================================
#  モード0: マシンガン（実行者＝プレイヤー）
#  このモードでクロスボウを持っている間、矢を装填済みのクロスボウに
#  常に置き換え続けることで、構える動作なしで連射できるようにする
#  ※ minecraft:charged_projectiles コンポーネントの有無をアイテム側で
#    毎tick判定する方式（*[!minecraft:charged_projectiles]）は、発射後に
#    コンポーネントが空リストとして残るか完全に消えるかが不安定で
#    正しく再判定できなかったため、プレイヤー側のスコアボードで
#    「装填済みかどうか」を自前管理する方式に変更した。
#    フラグは発射を検知した瞬間（crossbow/on_shot.mcfunction）に
#    リセットされ、この関数が次のtickで再装填する。
# ============================================================
execute unless score @s uw.mode matches 0 run return 0

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_crossbow:1b}] unless score @s uw.mgloaded matches 1 run function uw:crossbow/machinegun/reload_main
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_crossbow:1b}] unless score @s uw.mgloadedo matches 1 run function uw:crossbow/machinegun/reload_off

execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{uw_crossbow:1b}] run scoreboard players set @s uw.mgloaded 0
execute unless items entity @s weapon.offhand *[minecraft:custom_data~{uw_crossbow:1b}] run scoreboard players set @s uw.mgloadedo 0
