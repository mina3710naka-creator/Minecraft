# ============================================================
#  Shiftキーでのモード切替（実行者＝プレイヤー）
#  最強のクロスボウを持ち、発射中/ズーム中でない時、Shiftを
#  「押した瞬間」だけ反応する（立ち上がりエッジのみ）
# ============================================================
scoreboard players add @s uw.mode 0
scoreboard players add @s uw.sneakp 0

execute unless entity @s[tag=uw.aiming] unless entity @s[tag=uw.laser] if score @s uw.sneakp matches 0 if predicate uw:sneaking if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_crossbow:1b}] run function uw:crossbow/mode/apply

execute if predicate uw:sneaking run scoreboard players set @s uw.sneakp 1
execute unless predicate uw:sneaking run scoreboard players set @s uw.sneakp 0
