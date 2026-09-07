# ============================================================
#  毎ティック処理
# ============================================================

# --- クロスボウ ---
# 1) 新しく出現した矢を確認（発射検知）
execute as @e[type=minecraft:arrow,tag=!uw.seen] at @s run function uw:crossbow/detect_shot
# 2) Shiftでのモード切替（未発射時、押した瞬間のみ）
execute as @a run function uw:crossbow/mode/toggle
# 3) ズーム中の照準・離した瞬間の検知
execute as @a[tag=uw.aiming] at @s run function uw:crossbow/zoom/aim_tick
# 4) 自動追尾弾・レーザー・花火の進行
execute as @e[tag=uw.homing] at @s run function uw:crossbow/homing/tick
execute as @a[tag=uw.laser] at @s run function uw:crossbow/laser/tick
execute as @e[tag=uw.uwfirework] at @s run function uw:crossbow/zoom/firework_tick

# --- 剣 ---
execute as @a at @s run function uw:sword/interact/ensure
execute as @e[type=minecraft:interaction,tag=uw.sword_interact] at @s run function uw:sword/interact/tick
execute as @a[tag=uw.flying] at @s run function uw:sword/flight/tick
execute as @e[tag=uw.rain] at @s run function uw:sword/rain/tick
execute as @e[tag=uw.circle2] at @s run function uw:sword/circle/tick2

# --- 雪玉 ---
execute as @e[type=minecraft:snowball,tag=!uw.seen] at @s run function uw:snow/detect_throw
execute as @e[tag=uw.bhproj] at @s run function uw:snow/blackhole/proj_tick
execute as @e[tag=uw.blackhole] at @s run function uw:snow/blackhole/tick
execute as @e[tag=uw.fzproj] at @s run function uw:snow/freeze/proj_tick
execute as @e[tag=uw.frozen] at @s run function uw:snow/freeze/tick
execute as @e[tag=uw.icproj] at @s run function uw:snow/icicle/proj_tick
execute as @e[tag=uw.icicle] at @s run function uw:snow/icicle/tick

# --- 後始末 ---
execute as @e[tag=uw.ent] run function uw:util/entity_timeout
scoreboard players set @a uw.using 0
