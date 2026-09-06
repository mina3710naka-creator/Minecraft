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
# 与える（地形やコリジョンに依存しないので確実）。
# absorption で吸収させて「実質ノーダメージ」にする方式は、ノックバック
# 自体も一緒に無効化されてしまった（反動処理は「本当にダメージを
# 受けたか」に連動するらしい）ため、代わりに一時的に体力を大きく
# 底上げしてから本物のダメージを通し、直後に元の体力へ戻す方式にする。
# ノックバックはそのまま働きつつ、体力は結果的に変化しない。
execute store result storage hookshot:v hp float 1 run data get entity @s Health
data modify entity @s Health set value 100.0
damage @s 6 minecraft:wind_charge at ~ ~-2 ~
# ここで $(hp) マクロを使うと、この関数を呼び出した時点のストレージの
# スナップショットを見てしまい、直前で書き込んだ hp を拾えず体力の
# 復元に失敗する（マクロ引数は呼び出し時点で固定され、関数内で書き換えても
# 追従しない）。"set from storage" ならストレージを即時に読むので問題ない。
data modify entity @s Health set from storage hookshot:v hp

function hookshot:release
