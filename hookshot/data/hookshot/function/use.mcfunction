# ============================================================
#  釣り竿を使ったときの処理（実行者＝プレイヤー）
# ============================================================

# フックショット用の釣り竿以外は普通に釣りをさせる
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{hookshot:1b}] unless items entity @s weapon.offhand *[minecraft:custom_data~{hookshot:1b}] run return 0

# --- まず自分が出した釣り針をキルする ---
tag @s add hs.self
execute as @e[type=fishing_bobber] at @s on origin if entity @s[tag=hs.self] run kill @e[type=fishing_bobber,limit=1,sort=nearest,distance=..2]
tag @s remove hs.self

# --- すでにフック中ならもう一度の使用で解除（トグル） ---
execute if entity @s[tag=hs.active] run return run function hookshot:release

# --- 発射 ---
function hookshot:fire
