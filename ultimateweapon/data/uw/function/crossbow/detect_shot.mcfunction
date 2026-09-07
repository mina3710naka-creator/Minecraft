# ============================================================
#  新しく出現した矢を1回だけ確認する（実行者＝矢 / 実行位置＝矢）
#  クロスボウの「構える→離す」の統計は不安定なため使わず、実際に
#  矢が出現したことそのものを発射の合図にする（所有者が最強の
#  クロスボウを構えていた場合のみ反応する）。
# ============================================================
tag @s add uw.seen
execute on owner at @s if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_crossbow:1b}] run function uw:crossbow/on_shot
execute on owner at @s if items entity @s weapon.offhand *[minecraft:custom_data~{uw_crossbow:1b}] run function uw:crossbow/on_shot
