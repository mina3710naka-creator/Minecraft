# プレイヤー側の安全装置（発射から 6 秒で強制解除）
scoreboard players add @s hs.t 1
execute if score @s hs.t matches 120.. run function hookshot:release
