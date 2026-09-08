# ============================================================
#  クリック検知用インタラクションの管理（実行者＝プレイヤー）
#  スパイダークロスボウを持っている間だけ、視点位置にインタラクション
#  エンティティを常駐させ、左クリック（特別な球）をここで検知する
# ============================================================
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] unless items entity @s weapon.offhand *[minecraft:custom_data~{sc_crossbow:1b}] if entity @s[tag=sc.has_interact] run function sc:click/remove

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] unless entity @s[tag=sc.has_interact] run function sc:click/spawn
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] if items entity @s weapon.offhand *[minecraft:custom_data~{sc_crossbow:1b}] unless entity @s[tag=sc.has_interact] run function sc:click/spawn

execute if entity @s[tag=sc.has_interact] anchored eyes positioned ^ ^-0.6 ^ run tp @e[type=minecraft:interaction,tag=sc.interact,distance=..2,limit=1,sort=nearest] ~ ~ ~
