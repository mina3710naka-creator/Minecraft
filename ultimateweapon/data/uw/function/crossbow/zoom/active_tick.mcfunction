# ============================================================
#  実際にズーム中の毎ティック処理（実行者＝プレイヤー）
# ============================================================

# 離した瞬間（このtickまでにusing_itemが発火していなければ離したとみなす）
execute if score @s uw.using matches 0 run return run function uw:crossbow/zoom/release

# ロックオン対象を毎ティック探し直す
execute as @e[tag=uw.locktgt] run effect clear @s minecraft:glowing
tag @e[tag=uw.locktgt] remove uw.locktgt

# 照準用こうもりを視点方向へ瞬時に飛ばし直し、その道中のMOBを探す
# （positioned ^ ^ ^0 が無いと足元付近から発射してしまう）
execute anchored eyes positioned ^ ^ ^0 as @e[tag=uw.aimprobe,limit=1,sort=nearest] run function uw:crossbow/zoom/probe_reset

# 新しくロックオンした瞬間だけ演出を出す
execute if entity @e[tag=uw.locktgt] unless entity @s[tag=uw.hadlock] run function uw:crossbow/zoom/lock_feedback
execute if entity @e[tag=uw.locktgt] run tag @s add uw.hadlock
execute unless entity @e[tag=uw.locktgt] run tag @s remove uw.hadlock

execute if entity @e[tag=uw.locktgt] run title @s actionbar {"text":"ロックオン！ 右クリックを離して発射","color":"gold","bold":true}
execute unless entity @e[tag=uw.locktgt] run title @s actionbar {"text":"MOBに照準を合わせてください","color":"gray"}
