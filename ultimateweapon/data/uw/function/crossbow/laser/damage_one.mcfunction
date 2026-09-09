# 実際のダメージ処理（実行者＝命中したMOB）
# ※ バニラの被ダメージ無敵時間（約10tick）中に同じダメージ量で
#   再度当てると無効化されてしまうため、被弾対象ごとにクールダウンを
#   持たせ、無敵時間より長い間隔（12tick）でのみダメージを通す
scoreboard players set @s uw.cool 12
damage @s 6 minecraft:generic
