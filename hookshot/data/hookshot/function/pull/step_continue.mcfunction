# ============================================================
#  台車の 1 ステップ移動後の共通処理（マクロ / 実行者＝台車）
#  pull/step_move.mcfunction / pull/step_move_up.mcfunction の
#  tp の直後から呼ばれる。
# ============================================================

# 十分近づいたら打ち上げ処理へ（tp 後の実座標を実行位置に反映させてから判定しないと、
# 移動前の座標のまま距離判定してしまい、到達しても打ち上げに進めなくなる。
# マーカーのぎりぎりまで近づけるよう、しきい値を詰めている）
$execute at @s if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..0.2] run return run function hookshot:pull/arrive with storage hookshot:v

# 残りのステップ（移動後の位置に実行位置を合わせ直してから再帰）
scoreboard players remove @s hs.sub 1
execute if score @s hs.sub matches 1.. at @s run function hookshot:pull/step with storage hookshot:v
