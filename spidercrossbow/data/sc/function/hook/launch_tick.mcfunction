# 解除後の打ち上げ（レビテーション）管理（実行者＝上昇中のプレイヤー）
scoreboard players add @s sc.t 1
execute if score @s sc.t matches 7.. run effect clear @s minecraft:levitation
execute if score @s sc.t matches 7.. run tag @s remove sc.launching
