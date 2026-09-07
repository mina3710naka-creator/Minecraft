# ============================================================
#  持ち替えキー（アイテム交換 / デフォルトF、Rに割り当て推奨）の検知
#  （実行者＝プレイヤー）
#  メインハンドとオフハンドの中身が同時に入れ替わった時だけ反応する
#  （ホットバー切替や単なる装備変更とは区別する）
#  フック中（sc.hooked）のみ、巻き取り／伸ばす操作として扱う
# ============================================================
scoreboard players set @s sc.nmh 0
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] run scoreboard players set @s sc.nmh 1
scoreboard players set @s sc.noh 0
execute if items entity @s weapon.offhand *[minecraft:custom_data~{sc_crossbow:1b}] run scoreboard players set @s sc.noh 1

scoreboard players set @s sc.swap 0
execute unless score @s sc.mh = @s sc.nmh unless score @s sc.oh = @s sc.noh if score @s sc.nmh matches 1 run scoreboard players set @s sc.swap 1
execute unless score @s sc.mh = @s sc.nmh unless score @s sc.oh = @s sc.noh if score @s sc.noh matches 1 run scoreboard players set @s sc.swap 1

execute if score @s sc.swap matches 1 if entity @s[tag=sc.hooked] if predicate sc:sneaking run function sc:hook/reel_out
execute if score @s sc.swap matches 1 if entity @s[tag=sc.hooked] unless predicate sc:sneaking run function sc:hook/reel_in

scoreboard players operation @s sc.mh = @s sc.nmh
scoreboard players operation @s sc.oh = @s sc.noh
