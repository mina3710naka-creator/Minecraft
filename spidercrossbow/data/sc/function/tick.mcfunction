# ============================================================
#  毎ティック処理
# ============================================================

# --- クロスボウの発射検知（自分の手持ちを毎tick直接確認する方式） ---
execute as @a at @s run function sc:detect_shot

# --- クリック検知（Shift+左クリックで特別な球） ---
execute as @a at @s run function sc:click/ensure
execute as @e[type=minecraft:interaction,tag=sc.interact] at @s run function sc:click/tick

# --- Shiftでのモード切替（保留・確定） / フック中のShiftタップ解除 ---
execute as @a run function sc:mode/toggle
execute as @a[tag=sc.modepend] run function sc:mode/pend_tick

# --- 持ち替えキー（F/交換キー）でのフック中の巻き取り・伸ばす検知 ---
execute as @a run function sc:input/swap_detect

# --- 飛行中の発射物（本物の矢を流用） ---
execute as @e[type=minecraft:arrow,tag=sc.hook] at @s run function sc:hook/tick
execute as @e[type=minecraft:arrow,tag=sc.webproj] at @s run function sc:web/tick
execute as @e[type=armor_stand,tag=sc.ball] at @s run function sc:ball/tick

# --- フック中の振り子運動・MOB引き寄せ ---
execute as @e[type=minecraft:bat,tag=sc.carrier] at @s run function sc:hook/swing_tick
execute as @e[tag=sc.pulled] at @s run function sc:hook/mobpull_tick
execute as @a[tag=sc.hooked] run function sc:hook/release_check
execute as @a[tag=sc.launching] run function sc:hook/launch_tick

# --- 後始末 ---
execute as @e[tag=sc.ent] run function sc:util/entity_timeout
scoreboard players set @a sc.jump 0
