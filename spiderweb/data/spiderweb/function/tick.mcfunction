# ============================================================
#  毎ティック処理
# ============================================================

# --- 1) クロスボウの発射を検知 ---
execute as @a[scores={sw.use=1..}] at @s run function spiderweb:use
scoreboard players set @a sw.use 0

# --- 2) Shiftでモード切り替え（未使用時のみ、押した瞬間のみ反応） ---
execute as @a run function spiderweb:mode_toggle

# --- 3) 飛行中のフック（モード1・フックショット）を前進させる ---
execute as @e[type=armor_stand,tag=sw.hook] at @s run function spiderweb:hook/tick

# --- 4) 飛行中のクモの巣弾（モード2・巣づくり）を前進させる ---
execute as @e[type=armor_stand,tag=sw.web] at @s run function spiderweb:web/tick

# --- 4.5) 飛行中のクモの巣除去弾（左クリック）を前進させる ---
execute as @e[type=armor_stand,tag=sw.ball] at @s run function spiderweb:ball/tick

# --- 5) リード（ロープ）用アンカーを追従させる ---
execute as @e[type=bat,tag=sw.rope] at @s run function spiderweb:rope/tick

# --- 6) 引き寄せ中（見えない台車）の移動 ---
execute as @e[type=bat,tag=sw.carrier] at @s run function spiderweb:pull/tick

# --- 7) 糸に張り付いている間の処理（R / Shift+R / Space） ---
execute as @a[tag=sw.attached] at @s run function spiderweb:attach/tick

# --- 8) MOBを自分側へ引き寄せる処理 ---
execute as @e[tag=sw.yankmob] at @s run function spiderweb:yank/tick

# --- 9) キラキラのロープ描画 ---
execute as @a[tag=sw.active] at @s run function spiderweb:rope/line

# --- 10) 安全装置・後始末 ---
execute as @a[tag=sw.active] run function spiderweb:util/timeout
execute as @e[tag=sw.ent] run function spiderweb:util/entity_timeout
execute as @e[tag=sw.sweep] at @s run function spiderweb:util/sweep

# --- 11) 到達時の打ち上げ（レビテーション）の後始末 ---
execute as @a[tag=sw.launching] at @s run function spiderweb:launch_tick

# --- 12) キー入力検知（R・Space）を毎ティックリセット ---
scoreboard players set @a sw.rkey 0
scoreboard players set @a sw.jump 0
