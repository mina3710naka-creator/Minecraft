# ============================================================
#  クリック検知用インタラクションの管理（実行者＝プレイヤー）
#  最強のダイヤの剣を持っている間だけ、視点位置にインタラクション
#  エンティティを常駐させ、左右クリックの両方をここで検知する
#  ※ has_interact タグはあるのに実体が消えている（何らかの理由で
#    kill された等）と右クリックも左クリックも一切反応しなくなる
#    ため、毎tick実体の生存を確認し、いなければタグを外して
#    再生成させる保険を入れている
# ============================================================
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{uw_sword:1b}] if entity @s[tag=uw.has_interact] run function uw:sword/interact/remove

execute if entity @s[tag=uw.has_interact] unless entity @e[type=minecraft:interaction,tag=uw.sword_interact,distance=..3] run tag @s remove uw.has_interact

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_sword:1b}] unless entity @s[tag=uw.has_interact] run function uw:sword/interact/spawn

execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_sword:1b}] if entity @s[tag=uw.has_interact] anchored eyes positioned ^ ^-0.6 ^ run tp @e[type=minecraft:interaction,tag=uw.sword_interact,distance=..2,limit=1,sort=nearest] ~ ~ ~
