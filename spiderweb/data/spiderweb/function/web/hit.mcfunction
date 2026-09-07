# ============================================================
#  着弾（実行者＝防具立て / 実行位置＝着弾地点）
#  着弾したMOBかブロックを中心に3x3x3のクモの巣を設置する。
#  クモの糸が貫通できる程度の柔らかいブロック（#spiderweb:passable、
#  空気や草花・水など）だけを巣に置き換える。
# ============================================================
particle minecraft:end_rod ~ ~ ~ 0.2 0.2 0.2 0.02 16 normal @a
particle minecraft:cloud ~ ~ ~ 0.3 0.3 0.3 0.01 10 normal @a
playsound minecraft:block.cobweb.place block @a ~ ~ ~ 1 1

fill ~-1 ~-1 ~-1 ~1 ~1 ~1 minecraft:cobweb replace #spiderweb:passable

execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
function spiderweb:web/finish with storage spiderweb:v

execute if entity @s run kill @s
