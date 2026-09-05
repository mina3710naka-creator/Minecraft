# キラキラのロープ描画（実行者＝プレイヤー）
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
scoreboard players set #i hs.t 56
function hookshot:rope/line_start with storage hookshot:v
