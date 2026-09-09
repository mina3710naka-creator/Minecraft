# ============================================================
#  Shiftを押した瞬間の処理（実行者＝プレイヤー）
#  フック中ならその場で解除、そうでなければモード切替
# ============================================================
scoreboard players set @s sc.sneakp 1

execute if entity @s[tag=sc.hooked] run return run function sc:hook/release

execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] unless items entity @s weapon.offhand *[minecraft:custom_data~{sc_crossbow:1b}] run return 0
execute if entity @s[tag=sc.hookflying] run return 0
execute if entity @s[tag=sc.webflying] run return 0

scoreboard players add @s sc.mode 1
execute if score @s sc.mode matches 2.. run scoreboard players set @s sc.mode 0

playsound minecraft:block.note_block.hat player @s ~ ~ ~ 1 1.5

execute if score @s sc.mode matches 0 run title @s actionbar {"text":"モード1: フックショット","color":"aqua","bold":true}
execute if score @s sc.mode matches 1 run title @s actionbar {"text":"モード2: 巣づくり(糸を消費)","color":"white","bold":true}
