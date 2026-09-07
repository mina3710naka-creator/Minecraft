# ============================================================
#  新しく出現した雪玉を1回だけ確認する（実行者＝雪玉 / 実行位置＝雪玉）
#  種類ごとにcustom_dataを持つ専用雪玉かどうかを、投げた本人（owner）の
#  手持ちアイテムで判定する。バニラの雪玉の物理挙動はそのまま使わず、
#  ここで即座にキルして自作の弾を代わりに飛ばす
# ============================================================
tag @s add uw.seen

execute on owner if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_blackhole:1b}] run function uw:snow/blackhole/launch
execute on owner if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_blackhole:1b}] run function uw:snow/blackhole/launch

execute on owner if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_freeze:1b}] run function uw:snow/freeze/launch
execute on owner if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_freeze:1b}] run function uw:snow/freeze/launch

execute on owner if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_icicle:1b}] run function uw:snow/icicle/launch
execute on owner if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_icicle:1b}] run function uw:snow/icicle/launch

kill @s
