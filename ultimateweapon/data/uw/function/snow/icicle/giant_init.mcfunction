# ============================================================
#  巨大な氷塊の初期化（実行者＝生成されたブロックディスプレイ）
#  block_display で通常の氷ブロックを見た目だけ3倍に拡大している
#  （当たり判定は自前で管理するので、ブロック自体は設置されない）
# ============================================================
data merge entity @s {block_state:{Name:"minecraft:blue_ice"},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[-1.5f,-1.5f,-1.5f],scale:[3.0f,3.0f,3.0f]},teleport_duration:0,Glowing:1b}
tag @s add uw.icegiant
tag @s add uw.ent
scoreboard players set @s uw.t 0
