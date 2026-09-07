# ============================================================
#  飛行中の毎ティック処理（実行者＝プレイヤー）
#  常時スローフォールで落下ダメージを無効化しつつ、上を向いていれば
#  微量のレビテーションで上昇する（見た目通りに操作できる簡易飛行）
# ============================================================
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{uw_sword:1b}] run function uw:sword/flight/off

execute store result score @s uw.pitch run data get entity @s Rotation[1] 100
effect give @s minecraft:slow_falling 4 0 true
execute if score @s uw.pitch matches ..-1500 run effect give @s minecraft:levitation 1 2 true
