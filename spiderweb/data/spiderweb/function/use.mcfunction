# ============================================================
#  クロスボウ（ウェブシューター）を撃ったときの処理（実行者＝プレイヤー）
# ============================================================

# ウェブシューター以外のクロスボウは普通に使わせる
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] unless items entity @s weapon.offhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run return 0

# --- 発射で実体化した「クモの糸」の矢を消す（見た目だけ矢、実体は自作の弾に差し替える） ---
tag @s add sw.self
execute as @e[type=arrow,tag=!sw.ent,distance=..4,nbt={item:{components:{"minecraft:custom_data":{spiderweb:{tool:"thread"}}}}}] at @s on owner if entity @s[tag=sw.self] run kill @e[type=arrow,tag=!sw.ent,limit=1,sort=nearest,distance=..4,nbt={item:{components:{"minecraft:custom_data":{spiderweb:{tool:"thread"}}}}}]
tag @s remove sw.self

# --- すでに使用中なら、もう一度の使用で解除（トグル） ---
execute if entity @s[tag=sw.active] run return run function spiderweb:release

# --- モードに応じて発射 ---
execute if score @s sw.mode matches 0 run function spiderweb:fire_grapple
execute if score @s sw.mode matches 1 run function spiderweb:fire_web
