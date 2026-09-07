# ============================================================
#  MOBを1tick分プレイヤーへ近づける（マクロ / 実行者＝MOB）
# ============================================================

# 対応するプレイヤーがいなくなっていたら終了
$execute unless entity @a[tag=sw.yanking,scores={sw.id=$(id)}] run return run function spiderweb:yank/cleanup

# 十分近づいたら到達
$execute if entity @a[tag=sw.yanking,scores={sw.id=$(id)},distance=..2] run return run function spiderweb:yank/arrive with storage spiderweb:v

# 3秒（60tick）経っても届かない場合は強制終了（迷子防止）
execute if score @s sw.pt matches 60.. run return run function spiderweb:yank/arrive with storage spiderweb:v

# 加速しながらプレイヤーの方を向く（MOBのAIは無効化していないため、
# 通常の行動と綱引きしながら引き寄せられる）
scoreboard players add @s sw.spd 40
execute if score @s sw.spd matches 240.. run scoreboard players set @s sw.spd 240

$execute facing entity @a[tag=sw.yanking,scores={sw.id=$(id)},limit=1] feet run tp @s ~ ~ ~ ~ ~

execute store result storage spiderweb:v step double 0.00125 run scoreboard players get @s sw.spd
scoreboard players set @s sw.sub 8
function spiderweb:yank/move with storage spiderweb:v
