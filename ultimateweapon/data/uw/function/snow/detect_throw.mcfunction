# ============================================================
#  雪玉を投げた瞬間を検知（実行者＝プレイヤー / 実行位置＝プレイヤー）
#  ※これまで「近くの未処理の雪玉を探す」方式を2回試したが実機で
#    機能しなかったため、hookshotの釣り竿と全く同じ、実績のある
#    統計 minecraft.used:minecraft.snowball ベースの検知に統一した。
#    毎tick 0 にリセットするので雪玉を投げた回数の統計は増えなくなる。
# ============================================================
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_blackhole:1b}] run function uw:snow/blackhole/check_throw
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_blackhole:1b}] run function uw:snow/blackhole/check_throw

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_freeze:1b}] run function uw:snow/freeze/check_throw
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_freeze:1b}] run function uw:snow/freeze/check_throw

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_icicle:1b}] run function uw:snow/icicle/check_throw
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_icicle:1b}] run function uw:snow/icicle/check_throw
