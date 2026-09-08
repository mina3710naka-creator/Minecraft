# ============================================================
#  Shiftを押した瞬間（実行者＝プレイヤー）
#  すぐには切り替えず、少しの間だけ「保留」にする
#  （この間にShift+左クリックがあれば取り消され、特別な球だけが発動する）
# ============================================================
scoreboard players set @s sc.sneakp 1

execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] unless items entity @s weapon.offhand *[minecraft:custom_data~{sc_crossbow:1b}] run return 0
execute if entity @s[tag=sc.hookflying] run return 0
execute if entity @s[tag=sc.webflying] run return 0

tag @s add sc.modepend
scoreboard players set @s sc.modependt 0
