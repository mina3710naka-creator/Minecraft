# ============================================================
#  モード3: ズーム開始（実行者＝プレイヤー）
#  クロスボウを本物の望遠鏡（スパイグラス）に入れ替える。
#  スパイグラスを右クリック長押しするとバニラのズーム演出が働く。
# ============================================================
loot replace entity @s weapon.mainhand loot uw:give/aim_spyglass

tag @s add uw.aiming
scoreboard players set @s uw.using 0

playsound minecraft:item.spyglass.use player @s ~ ~ ~ 1 1
title @s actionbar {"text":"望遠鏡モード！ 右クリック長押しでズーム","color":"light_purple"}
