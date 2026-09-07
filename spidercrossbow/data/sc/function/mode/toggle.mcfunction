# ============================================================
#  Shiftキーでのモード切替 / フック中はShiftタップで解除
#  （実行者＝プレイヤー）
#  立ち上がりエッジ（押した瞬間）だけ反応する
# ============================================================
scoreboard players add @s sc.sneakp 0

execute unless score @s sc.sneakp matches 1.. if predicate sc:sneaking run function sc:mode/edge
execute unless predicate sc:sneaking run scoreboard players set @s sc.sneakp 0
