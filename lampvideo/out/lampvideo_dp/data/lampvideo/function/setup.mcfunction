kill @e[type=marker,tag=lampvideo]
summon marker ~ ~ ~ {Tags:["lampvideo"]}
fill ~ ~0 ~-1 ~359 ~44 ~-1 black_concrete
fill ~ ~0 ~ ~359 ~44 ~ redstone_lamp[lit=false]
fill ~ ~45 ~-1 ~359 ~89 ~-1 black_concrete
fill ~ ~45 ~ ~359 ~89 ~ redstone_lamp[lit=false]
fill ~ ~90 ~-1 ~359 ~134 ~-1 black_concrete
fill ~ ~90 ~ ~359 ~134 ~ redstone_lamp[lit=false]
fill ~ ~135 ~-1 ~359 ~179 ~-1 black_concrete
fill ~ ~135 ~ ~359 ~179 ~ redstone_lamp[lit=false]
fill ~ ~180 ~-1 ~359 ~224 ~-1 black_concrete
fill ~ ~180 ~ ~359 ~224 ~ redstone_lamp[lit=false]
fill ~ ~225 ~-1 ~359 ~269 ~-1 black_concrete
fill ~ ~225 ~ ~359 ~269 ~ redstone_lamp[lit=false]
fill ~ ~270 ~-1 ~359 ~314 ~-1 black_concrete
fill ~ ~270 ~ ~359 ~314 ~ redstone_lamp[lit=false]
fill ~ ~315 ~-1 ~359 ~359 ~-1 black_concrete
fill ~ ~315 ~ ~359 ~359 ~ redstone_lamp[lit=false]
tellraw @s [{"text":"[ランプ動画] 画面を設置しました。南側から北を向いて見てください。/function lampvideo:play で再生","color":"green"}]
