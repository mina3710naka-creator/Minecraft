# ============================================================
#  10マスジャンプ 開始（実行者＝プレイヤー、地上）
#  tpもエンティティのsummonも使わず、フックショットの打ち上げ演出
#  （pull/launch.mcfunction）で実際に検証済みの
#  `damage @s 6 minecraft:wind_charge at ~ ~-2 ~` を2回（1回分の2倍）
#  重ねて真上へ打ち上げる。
#  実際にブロックへ着弾させる必要がないので地形は一切壊れず、
#  ダメージ自体はブーツの耐性・被ダメージ無効化の保険で実質無効になる
# ============================================================

function opboots:jump/start

damage @s 6 minecraft:wind_charge at ~ ~-2 ~
damage @s 6 minecraft:wind_charge at ~ ~-2 ~
