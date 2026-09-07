# アンカーを見失った場合の緊急解除（マクロ / 実行者＝台車）
execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
function sc:hook/detach_lost2 with storage sc:v
