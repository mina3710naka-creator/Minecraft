# ============================================================
#  爆風ノックバックで上に打ち上げる（マクロ / 実行者＝プレイヤー）
#  実体のウィンドチャージを飛ばして着弾させる方式は、近くに床があるか
#  どうかという地形次第で着弾地点がプレイヤーから離れてしまい、
#  「起爆はするが本人には当たらない」ことがあった。
#  `/damage <対象> <量> <種別> at <座標>` は、実際にブロックへ着弾させる
#  必要がなく、指定した座標を発生源とした本物の爆発ノックバックを
#  地形に関係なく確実に与えられる（このために用意されたコマンド）。
# ============================================================

# プレイヤーを台車から降ろし、台車はここで即座に片付ける
# （死亡演出・リード等は出ないよう util/quiet_kill 経由で消す）
ride @s dismount
$execute as @e[tag=hs.carrier,scores={hs.id=$(id)}] at @s run function hookshot:util/quiet_kill

# ウィンドチャージの見た目・音だけを演出として出す
particle minecraft:gust ~ ~ ~ 0.3 0.3 0.3 0.1 15 normal @a
playsound minecraft:entity.wind_charge.wind_burst player @a ~ ~ ~ 1 1

# 実際のノックバックは、足元の少し下を発生源にした爆発扱いのダメージで
# 与える（地形やコリジョンに依存しないので確実）。ダメージそのものは
# absorption で先に吸収させておき、実質ノーダメージで済ませる
# （体力が少ないときでも、これが原因で死ぬことはない）。
effect give @s minecraft:absorption 1 19 true
damage @s 6 minecraft:wind_charge at ~ ~-2 ~

function hookshot:release
