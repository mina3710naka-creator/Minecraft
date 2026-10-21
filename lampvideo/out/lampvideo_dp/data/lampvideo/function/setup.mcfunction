kill @e[type=marker,tag=lampvideo]
summon marker ~ ~ ~ {Tags:["lampvideo"]}
fill ~ ~ ~-1 ~63 ~63 ~-1 black_concrete
fill ~ ~ ~ ~63 ~63 ~ redstone_lamp[lit=false]
tellraw @s [{"text":"[ランプ動画] 画面を設置しました。南側から北を向いて見てください。/function lampvideo:play で再生","color":"green"}]
