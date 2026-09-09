# ============================================================
#  クリック検知用インタラクションの管理（実行者＝プレイヤー）
#  インタラクションエンティティが視界にあるとクロスボウの本物の
#  「構える」動作を横取りしてしまう（右クリックが一切効かなくなる）ため、
#  常駐はさせず、Shiftを押している間だけ視点位置に配置する。
#  特別な球は Shift+左クリック で発射する
# ============================================================
execute unless predicate sc:sneaking if entity @s[tag=sc.has_interact] run function sc:click/remove
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] unless items entity @s weapon.offhand *[minecraft:custom_data~{sc_crossbow:1b}] if entity @s[tag=sc.has_interact] run function sc:click/remove

execute if predicate sc:sneaking if items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] unless entity @s[tag=sc.has_interact] run function sc:click/spawn
execute if predicate sc:sneaking unless items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] if items entity @s weapon.offhand *[minecraft:custom_data~{sc_crossbow:1b}] unless entity @s[tag=sc.has_interact] run function sc:click/spawn

execute if entity @s[tag=sc.has_interact] anchored eyes positioned ^ ^-0.6 ^ run tp @e[type=minecraft:interaction,tag=sc.interact,distance=..2,limit=1,sort=nearest] ~ ~ ~
