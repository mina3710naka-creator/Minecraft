# ============================================================
#  引き寄せ開始（マクロ / 実行者＝プレイヤー / 実行位置＝プレイヤー）
#  見えない台車（コウモリ）を出し、プレイヤーをその上にrideで乗せる。
# ============================================================
tag @s remove sw.hooking
tag @s add sw.pulling

execute summon minecraft:bat run function spiderweb:pull/carrier_init with storage spiderweb:v

playsound minecraft:item.lead.tied player @a ~ ~ ~ 0.8 1.3
