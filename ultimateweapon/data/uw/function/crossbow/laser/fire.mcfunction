# ============================================================
#  モード2: 持続レーザーの発射開始（実行者＝プレイヤー）
#  視点方向へこうもりを瞬時に100ブロック先まで飛ばし、その位置を
#  終点として5秒間（100tick）固定されたレーザーを設置する
# ============================================================
tag @s add uw.laser
scoreboard players set @s uw.t 100
playsound minecraft:entity.blaze.shoot player @a ~ ~ ~ 1.2 0.5
playsound minecraft:item.trident.riptide_1 player @a ~ ~ ~ 1 2

execute anchored eyes summon minecraft:bat run function uw:crossbow/laser/probe_init
