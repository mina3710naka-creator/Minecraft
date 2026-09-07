# ============================================================
#  雪玉を投げた瞬間を検知（実行者＝プレイヤー / 実行位置＝プレイヤー）
#  ※以前は execute on owner で判定していたが、実機で反応しない
#    ケースが確認されたため、モード切替と同じ「自分の手持ち
#    アイテムを毎tick直接確認する」方式に統一した。
# ============================================================
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_blackhole:1b}] run function uw:snow/blackhole/check_throw
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_blackhole:1b}] run function uw:snow/blackhole/check_throw

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_freeze:1b}] run function uw:snow/freeze/check_throw
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_freeze:1b}] run function uw:snow/freeze/check_throw

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_icicle:1b}] run function uw:snow/icicle/check_throw
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_icicle:1b}] run function uw:snow/icicle/check_throw
