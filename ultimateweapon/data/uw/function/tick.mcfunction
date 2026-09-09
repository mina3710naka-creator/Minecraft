# ============================================================
#  毎ティック処理
# ============================================================

# --- クロスボウ ---
# 1) 発射を検知（クロスボウを持っているプレイヤー自身を毎tick確認する）
execute as @a at @s run function uw:crossbow/detect_shot
# 2) Shiftでのモード切替（未発射時、押した瞬間のみ）
execute as @a run function uw:crossbow/mode/toggle
# 3) マシンガンモード: 装填済みクロスボウへの差し替え
execute as @a at @s run function uw:crossbow/machinegun/check
# 4) レーザー・ミサイル花火の進行
execute as @a[tag=uw.laser] at @s run function uw:crossbow/laser/tick
execute as @e[tag=uw.uwfirework] at @s run function uw:crossbow/zoom/firework_tick

# --- 剣 ---
execute as @a at @s run function uw:sword/interact/ensure
execute as @e[type=minecraft:interaction,tag=uw.sword_interact] at @s run function uw:sword/interact/tick
execute as @e[tag=uw.shockwave] at @s run function uw:sword/shockwave/tick
execute as @e[tag=uw.circle2] at @s run function uw:sword/circle/tick2

# --- 雪玉 --- (投げた回数の統計で検知。hookshotの釣り竿と同じ実績のある方式)
execute as @a[scores={uw.snowuse=1..}] at @s run function uw:snow/detect_throw
execute as @e[tag=uw.sbtrack_blackhole] at @s run function uw:snow/blackhole/tracker_tick
execute as @e[tag=uw.blackhole] at @s run function uw:snow/blackhole/tick
execute as @e[tag=uw.sbtrack_freeze] at @s run function uw:snow/freeze/tracker_tick
execute as @e[tag=uw.frozen] at @s run function uw:snow/freeze/tick
execute as @e[tag=uw.sbtrack_icicle] at @s run function uw:snow/icicle/tracker_tick
execute as @e[tag=uw.icegiant] at @s run function uw:snow/icicle/giant_tick

# --- 後始末 ---
execute as @e[tag=uw.ent] run function uw:util/entity_timeout
# バニラの被ダメージ無敵時間対策クールダウンの減衰(レーザー・ブラックホール共通)
execute as @e[scores={uw.cool=1..}] run scoreboard players remove @s uw.cool 1
scoreboard players set @a uw.using 0
scoreboard players set @a uw.snowuse 0
