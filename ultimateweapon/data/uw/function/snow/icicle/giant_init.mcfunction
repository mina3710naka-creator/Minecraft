# ============================================================
#  巨大な氷塊の初期化（実行者＝生成されたfalling_block）
#  以前は minecraft:block_display の拡大表示で試みていたが、実機で
#  全く着弾しない不具合が確認されたため、このパック内で既に実績のある
#  minecraft:falling_block（BlockState指定）＋NoGravity手動制御の
#  構成に戻した（つらら雨のときに使っていたのと同じ技術）
# ============================================================
data merge entity @s {BlockState:{Name:"minecraft:blue_ice"},Time:1,DropItem:0b,NoGravity:1b,Glowing:1b}
tag @s add uw.icegiant
tag @s add uw.ent
scoreboard players set @s uw.t 0
