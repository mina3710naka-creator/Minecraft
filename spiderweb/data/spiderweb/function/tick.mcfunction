# ============================================================
#  毎ティック処理
# ============================================================

# --- 1) クロスボウの発射を検知（新しく出現した矢を1回だけ確認する） ---
execute as @e[type=minecraft:arrow,tag=!sw.seen] at @s run function spiderweb:detect_shot

# --- 2) Shiftでのモード切り替え（未使用時、押した瞬間のみ） ---
execute as @a run function spiderweb:mode/toggle

# --- 3) 飛行中の弾を進める ---
execute as @e[type=minecraft:armor_stand,tag=sw.hook] at @s run function spiderweb:hook/tick
execute as @e[type=minecraft:armor_stand,tag=sw.web] at @s run function spiderweb:web/tick
execute as @e[type=minecraft:armor_stand,tag=sw.ball] at @s run function spiderweb:ball/tick

# --- 4) リード用アンカー（コウモリ）を追従させる ---
execute as @e[type=minecraft:bat,tag=sw.rope] at @s run function spiderweb:rope/tick

# --- 5) 見えない台車でのプレイヤー引き寄せ ---
execute as @e[type=minecraft:bat,tag=sw.carrier] at @s run function spiderweb:pull/tick

# --- 6) 糸に張り付いている間の処理（R / Shift+R / Space） ---
execute as @a[tag=sw.attached] at @s run function spiderweb:attach/tick

# --- 7) MOBを自分側へ引き寄せる処理 ---
execute as @e[tag=sw.yankmob] at @s run function spiderweb:yank/tick

# --- 8) キラキラのロープ描画 ---
execute as @a[tag=sw.active] at @s run function spiderweb:rope/line

# --- 9) 安全装置・後始末 ---
execute as @a[tag=sw.active] run function spiderweb:util/timeout
execute as @e[tag=sw.ent] run function spiderweb:util/entity_timeout
execute as @e[tag=sw.sweep] at @s run function spiderweb:util/sweep

# --- 10) 解除ジャンプ（レビテーション）の後始末 ---
execute as @a[tag=sw.launching] at @s run function spiderweb:jump_tick

# --- 11) 統計ベースのキー入力検知を次のティックのためにリセット ---
scoreboard players set @a sw.rkey 0
scoreboard players set @a sw.jump 0
