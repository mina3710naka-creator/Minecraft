# ============================================================
#  クモの巣クリアボールの投擲を検知（実行者＝プレイヤー）
#  手持ちを毎tick直接確認する、クロスボウの発射検知と同じ方式
# ============================================================
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{sc_ball:1b}] run function sc:check_throw
execute if items entity @s weapon.offhand *[minecraft:custom_data~{sc_ball:1b}] run function sc:check_throw
