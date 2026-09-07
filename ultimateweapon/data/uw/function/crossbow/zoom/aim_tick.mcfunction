# ============================================================
#  ズーム中の毎ティック処理（実行者＝プレイヤー）
# ============================================================

# 離した瞬間（このtickまでにusing_itemが一度も発火していなければ離したとみなす）
execute if score @s uw.using matches 0 run return run function uw:crossbow/zoom/release

# ロックオン対象を毎ティック探し直す
execute as @e[tag=uw.locktgt] run effect clear @s minecraft:glowing
tag @e[tag=uw.locktgt] remove uw.locktgt

scoreboard players set @s uw.sub 20
execute anchored eyes run function uw:crossbow/zoom/aim_scan

execute if entity @e[tag=uw.locktgt] run title @s actionbar {"text":"ロックオン！ 右クリックを離して発射","color":"gold","bold":true}
execute unless entity @e[tag=uw.locktgt] run title @s actionbar {"text":"MOBに照準を合わせてください","color":"gray"}
