# ============================================================
#  クリック検知用インタラクションの管理（実行者＝プレイヤー）
#  最強のダイヤの剣を持っている間だけ、視点位置にインタラクション
#  エンティティを常駐させ、左右クリックの両方をここで検知する
# ============================================================
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{uw_sword:1b}] if entity @s[tag=uw.has_interact] run function uw:sword/interact/remove

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_sword:1b}] unless entity @s[tag=uw.has_interact] run function uw:sword/interact/spawn

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_sword:1b}] if entity @s[tag=uw.has_interact] anchored eyes positioned ^ ^-0.6 ^ run tp @e[type=minecraft:interaction,tag=uw.sword_interact,distance=..2,limit=1,sort=nearest] ~ ~ ~
