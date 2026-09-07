# ============================================================
#  クモの巣除去弾の1ステップ（0.25ブロック）＋着弾判定
#  クモの巣（minecraft:cobweb）にだけ特別に反応する。それ以外の
#  通り抜けられないブロックでは何も除去せず消える。MOBはすり抜ける
#  （このデータパックの他の弾と異なり、当たり判定は一切見ない）。
# ============================================================
execute if block ^ ^ ^0.25 minecraft:cobweb run return run function spiderweb:ball/hit_web

execute unless block ^ ^ ^0.25 #spiderweb:passable run return run function spiderweb:ball/despawn

tp @s ^ ^ ^0.25
scoreboard players add @s sw.range 1

# 射程40ブロック（0.25×160）を超えたら消える
execute if score @s sw.range matches 160.. run return run function spiderweb:ball/despawn

scoreboard players remove @s sw.sub 1
execute if score @s sw.sub matches 1.. at @s run function spiderweb:ball/step
