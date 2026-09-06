# ============================================================
#  毎ティック処理
# ============================================================

# --- 1) 釣り竿の使用を検知 ---
execute as @a[scores={hs.use=1..}] at @s run function hookshot:use
scoreboard players set @a hs.use 0

# --- 2) 飛行中のフック（透明な防具立て）を前進させる ---
execute as @e[type=armor_stand,tag=hs.hook] at @s run function hookshot:hook/tick

# --- 3) リード（ロープ）用アンカーを追従させる ---
execute as @e[type=bat,tag=hs.rope] at @s run function hookshot:rope/tick

# --- 4) プレイヤーの引き寄せ（見えない台車を Motion で動かし、プレイヤーを ride で乗せて運ぶ） ---
execute as @e[type=bat,tag=hs.carrier] at @s run function hookshot:pull/tick

# --- 5) キラキラのロープ描画 ---
execute as @a[tag=hs.active] at @s run function hookshot:rope/line

# --- 6) 安全装置・後始末 ---
execute as @a[tag=hs.active] run function hookshot:util/timeout
execute as @e[tag=hs.ent] run function hookshot:util/entity_timeout
execute as @e[tag=hs.sweep] at @s run function hookshot:util/sweep
