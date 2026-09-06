# ============================================================
#  10マスジャンプ 開始（実行者＝プレイヤー）
#  tpもエンティティのsummonも使わず、`/damage <対象> <量> <種別> at <座標>`
#  （着弾を伴わない爆発扱いのダメージ・ノックバック）で真上へ打ち上げる。
#  実際にブロックへ着弾させる必要がないので地形は一切壊れず、地面の
#  有無にも左右されない（地上でも空中でも同じように動作する）。
#  ダメージ自体はブーツの耐性・被ダメージ無効化の保険で実質無効になる
# ============================================================

# 次回の溜めのためにシフト保持カウンターをリセット
scoreboard players set @s ob.sneak 0

tag @s add ob.leaping
scoreboard players set @s ob.leapt 0

# ウィンドチャージらしい見た目・音を演出として出す
particle minecraft:gust ~ ~ ~ 0.3 0.3 0.3 0.1 15 normal @a
playsound minecraft:entity.wind_charge.wind_burst player @a ~ ~ ~ 1 1

damage @s 10 minecraft:explosion at ~ ~-1 ~
