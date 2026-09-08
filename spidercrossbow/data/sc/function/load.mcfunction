# ============================================================
#  スパイダークロスボウ / Spider Crossbow  -  初期化
# ============================================================

# --- モード・入力検知用 ---
scoreboard objectives add sc.mode dummy
scoreboard objectives add sc.mh dummy
scoreboard objectives add sc.oh dummy
scoreboard objectives add sc.nmh dummy
scoreboard objectives add sc.noh dummy
scoreboard objectives add sc.swap dummy
scoreboard objectives add sc.jump minecraft.custom:minecraft.jump

# --- クリック検知（インタラクション）用 ---
scoreboard objectives add sc.atk dummy
scoreboard objectives add sc.natk dummy

# --- 発射物・フック共通 ---
scoreboard objectives add sc.id dummy
scoreboard objectives add sc.t dummy
scoreboard objectives add sc.age dummy
scoreboard objectives add sc.sub dummy
scoreboard objectives add sc.range dummy

# --- フックの綱・振り子 ---
scoreboard objectives add sc.rope dummy
scoreboard objectives add sc.theta dummy
scoreboard objectives add sc.phi dummy
scoreboard objectives add sc.hookt dummy
scoreboard objectives add sc.py dummy
scoreboard objectives add sc.pp dummy
scoreboard objectives add sc.d dummy

# --- 特別な球（クモの巣除去）のフラッドフィル用カウンタ ---
scoreboard objectives add sc.ballc dummy

# ID採番カウンタ
scoreboard players add #next sc.id 0

# 前回のワールド終了時の残骸を掃除
kill @e[tag=sc.ent]
kill @e[tag=sc.interact]
kill @e[type=minecraft:arrow,tag=sc.seen]
tag @a remove sc.has_interact
tag @a remove sc.hookflying
tag @a remove sc.webflying
tag @a remove sc.hooked
tag @a remove sc.launching
tag @a remove sc.pulling
tag @e remove sc.pulled

scoreboard players add @a sc.mode 0
scoreboard players add @a sc.mh 0
scoreboard players add @a sc.oh 0

tellraw @a [{"text":"[スパイダークロスボウ] ","color":"aqua"},{"text":"読み込み完了 / ","color":"gray"},{"text":"/function sc:give","color":"yellow"},{"text":" でアイテム入手","color":"gray"}]
tellraw @a [{"text":"[スパイダークロスボウ] ","color":"aqua"},{"text":"操作方法は ","color":"gray"},{"text":"/function sc:help","color":"yellow"},{"text":" で確認できます","color":"gray"}]
