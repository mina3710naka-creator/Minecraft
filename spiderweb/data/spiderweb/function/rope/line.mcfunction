# キラキラのロープ描画（実行者＝プレイヤー）
execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
scoreboard players set #i sw.t 56
function spiderweb:rope/line_start with storage spiderweb:v
