# ============================================================
#  マーカーに到達（実行者＝台車 / 実行位置＝台車）
#  すぐに解除せず、5 ブロック分上昇する「ジャンプ」演出をしてから
#  プレイヤーを解放する（tick.mcfunction / pull/tick.mcfunction が
#  以後 pull/jump_tick を毎ティック呼び出す）。
# ============================================================
tag @s add hs.jumping
scoreboard players set @s hs.jh 0
scoreboard players set @s hs.spd 120
