# 2発目の待機カウント（実行者＝マーカー / 実行位置＝マーカー）
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 6.. run function uw:sword/circle/burst
execute if score @s uw.t matches 6.. run function uw:util/quiet_kill
