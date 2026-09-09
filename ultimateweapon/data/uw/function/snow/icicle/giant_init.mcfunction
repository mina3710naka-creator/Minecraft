# ============================================================
#  巨大な氷塊の初期化（実行者＝生成された falling_block）
#  ※ block_display + transformation 拡大の見た目上の巨大化は、
#    着弾判定が全く機能しなかった（当たり判定・落下ともに実体を
#    伴わない見た目だけの存在のため）。このパックで実績のある
#    falling_block + NoGravity + 自前 tp 降下方式に戻した
# ============================================================
data merge entity @s {BlockState:{Name:"minecraft:blue_ice"},NoGravity:1b,Time:1,DropItem:0b,Glowing:1b}
tag @s add uw.icegiant
tag @s add uw.ent
scoreboard players set @s uw.t 0
