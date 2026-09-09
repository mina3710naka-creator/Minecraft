# ============================================================
#  着弾地点の真上に巨大な氷塊を出現させて落とす
#  （実行者＝追跡マーカー / 実行位置＝着弾地点）
# ============================================================
tag @s remove uw.sbtrack_icicle
playsound minecraft:block.glass.break player @a ~ ~ ~ 1 0.5
particle minecraft:snowflake ~ ~ ~ 0.5 0.2 0.5 0 10 normal @a

execute positioned ~ ~20 ~ summon minecraft:block_display run function uw:snow/icicle/giant_init

function uw:util/quiet_kill
