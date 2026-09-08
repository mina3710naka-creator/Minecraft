# ============================================================
#  自動追尾弾の初期化（実行者＝生成されたアイテム実体）
#  見た目でわかるよう、実際に矢のアイコンが飛んでいく実体を使う
#  （マーカーは完全に不可視のため、以前は何も見えなかった）
# ============================================================
data merge entity @s {Item:{id:"minecraft:arrow",count:1},NoGravity:1b,PickupDelay:32767s,Glowing:1b}
tag @s add uw.homing
tag @s add uw.ent
scoreboard players set @s uw.t 0
