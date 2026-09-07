# ============================================================
#  クモの巣クラスタの再帰的な除去（実行位置＝調べる座標）
#  そのマスがクモの巣であれば除去してから隣接6方向へ再帰する。
#  除去済みのマスは次の判定で弾かれるため、同じ場所をぐるぐる
#  回ったり無限ループしたりしない。念のため上限も設けてある。
# ============================================================
execute unless block ~ ~ ~ minecraft:cobweb run return 0

# 暴走防止：一度の発射で処理するブロック数の上限
scoreboard players add #wc sw.wc 1
execute if score #wc sw.wc matches 401.. run return 0

setblock ~ ~ ~ air destroy
particle minecraft:cloud ~ ~ ~ 0.25 0.25 0.25 0.01 2 normal @a

execute positioned ~1 ~ ~ run function spiderweb:webclear/flood
execute positioned ~-1 ~ ~ run function spiderweb:webclear/flood
execute positioned ~ ~1 ~ run function spiderweb:webclear/flood
execute positioned ~ ~-1 ~ run function spiderweb:webclear/flood
execute positioned ~ ~ ~1 run function spiderweb:webclear/flood
execute positioned ~ ~ ~-1 run function spiderweb:webclear/flood
