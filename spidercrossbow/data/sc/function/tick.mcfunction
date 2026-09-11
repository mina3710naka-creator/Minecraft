# ============================================================
#  毎ティック処理
# ============================================================

# --- クロスボウの発射検知（自分の手持ちを毎tick直接確認する方式） ---
# ※ execute at は位置だけを合わせ、向き（rotation）は引き継がない。
#   自分の視点方向を発射方向に使う各アビリティのため rotated as で明示する
execute as @a at @s rotated as @s run function sc:detect_shot

# --- クモの巣クリアボールの投擲検知 ---
execute as @a at @s rotated as @s run function sc:detect_throw

# --- Shiftでのモード切替 / フック中のShiftタップ解除 ---
execute as @a run function sc:mode/toggle

# --- 持ち替えキー（F/交換キー）でのフック中の巻き取り・伸ばす検知 ---
execute as @a run function sc:input/swap_detect

# --- 飛行中の発射物（雪玉） ---
execute as @e[type=minecraft:snowball,tag=sc.hook] at @s run function sc:hook/tick
execute as @e[type=minecraft:snowball,tag=sc.webproj] at @s run function sc:web/tick
execute as @e[type=minecraft:snowball,tag=sc.ball] at @s run function sc:ball/tick

# --- フック中の振り子運動・MOB引き寄せ ---
execute as @e[type=minecraft:bat,tag=sc.carrier] at @s run function sc:hook/swing_tick
execute as @e[tag=sc.pulled] at @s run function sc:hook/mobpull_tick
execute as @a[tag=sc.hooked] run function sc:hook/release_check
execute as @a[tag=sc.launching] run function sc:hook/launch_tick

# --- 後始末 ---
execute as @e[tag=sc.ent] run function sc:util/entity_timeout
scoreboard players set @a sc.jump 0
