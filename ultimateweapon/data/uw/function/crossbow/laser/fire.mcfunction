# ============================================================
#  モード2: 持続レーザーの発射開始（実行者＝プレイヤー）
#  20tick（1秒）の間、毎ティック現在の視線方向にビームを撃ち続ける
# ============================================================
tag @s add uw.laser
scoreboard players set @s uw.t 20
playsound minecraft:entity.blaze.shoot player @a ~ ~ ~ 1.2 0.5
playsound minecraft:item.trident.riptide_1 player @a ~ ~ ~ 1 2
