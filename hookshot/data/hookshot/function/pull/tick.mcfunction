# ============================================================
#  台車の毎ティック処理（実行者＝台車 / 実行位置＝台車）
#  速度（hs.spd）を毎ティック加算 → イーズインで滑らかに加速
# ============================================================
scoreboard players add @s hs.pt 1
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id

# ============================================================
#  詰まり検出：実際に座標が進んでいるかを直接比較する
#  ブロック判定（#hookshot:passable）だけに頼ると、台車の当たり判定の
#  形・大きさや、着地面との微妙な位置関係によって、こちらの判定では
#  「まだ通れる」となっているのに実際にはゲーム本体の衝突判定で
#  止まってしまう場合があり、原因を個別に特定するのは難しい。
#  そこで原因を問わず、「移動しようとしているのに実際の座標がほとんど
#  変わっていない」状態そのものを直接検出する。
# ============================================================
execute store result score #tx hs.tmp run data get entity @s Pos[0] 100
execute store result score #ty hs.tmp run data get entity @s Pos[1] 100
execute store result score #tz hs.tmp run data get entity @s Pos[2] 100
scoreboard players operation #tx hs.tmp -= @s hs.lx
scoreboard players operation #ty hs.tmp -= @s hs.ly
scoreboard players operation #tz hs.tmp -= @s hs.lz
execute store result score @s hs.lx run data get entity @s Pos[0] 100
execute store result score @s hs.ly run data get entity @s Pos[1] 100
execute store result score @s hs.lz run data get entity @s Pos[2] 100

execute unless score #tx hs.tmp matches -4..4 run scoreboard players set @s hs.stc 0
execute unless score #ty hs.tmp matches -4..4 run scoreboard players set @s hs.stc 0
execute unless score #tz hs.tmp matches -4..4 run scoreboard players set @s hs.stc 0
execute if score #tx hs.tmp matches -4..4 if score #ty hs.tmp matches -4..4 if score #tz hs.tmp matches -4..4 run scoreboard players add @s hs.stc 1

# 3 ティック（0.15 秒）連続でほぼ動けていなければ「詰まった」とみなす。
# マーカーにある程度近ければ到達扱いで打ち上げへ、遠ければ切り離す。
$execute if score @s hs.stc matches 3.. if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..3] run return run function hookshot:pull/arrive with storage hookshot:v
execute if score @s hs.stc matches 3.. run return run function hookshot:pull/detach

# 加速（+0.3 / tick）と上限（2.0 ブロック / tick、フックの飛行と同じ速さ）
scoreboard players add @s hs.spd 30
execute if score @s hs.spd matches 200.. run scoreboard players set @s hs.spd 200

function hookshot:pull/move with storage hookshot:v

# 3 秒（60 ティック）引き寄せられたら切り離す
execute if score @s hs.pt matches 60.. run function hookshot:pull/detach
