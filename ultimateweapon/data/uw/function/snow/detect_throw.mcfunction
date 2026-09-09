# ============================================================
#  雪玉を投げた瞬間を検知（実行者＝プレイヤー / 実行位置＝プレイヤー）
#  minecraft.used:minecraft.snowball の統計で「投げた」ことを検知し、
#  本物の雪玉（実物）にタグを付けて追跡する。効果はバニラの物理演算で
#  実際に飛んだ本物の雪玉が着弾した地点で発動する（自作の弾は使わない）
# ============================================================
playsound minecraft:entity.snowball.throw player @s ~ ~ ~ 1 1

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_blackhole:1b}] run function uw:snow/blackhole/mark_thrown
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_blackhole:1b}] run function uw:snow/blackhole/mark_thrown

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_freeze:1b}] run function uw:snow/freeze/mark_thrown
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_freeze:1b}] run function uw:snow/freeze/mark_thrown

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_snow_icicle:1b}] run function uw:snow/icicle/mark_thrown
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_snow_icicle:1b}] run function uw:snow/icicle/mark_thrown
