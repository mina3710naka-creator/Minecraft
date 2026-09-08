# ============================================================
#  ブロックに着弾：固定アンカー設置（マクロ / 実行者＝生成されたマーカー）
# ============================================================
$scoreboard players set @s sc.id $(id)
tag @s add sc.anchor
tag @s add sc.ent

$execute as @a[tag=sc.hookflying,scores={sc.id=$(id)},limit=1] at @s run function sc:hook/attach with storage sc:v
