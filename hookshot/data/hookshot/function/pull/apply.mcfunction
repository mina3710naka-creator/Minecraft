# ============================================================
#  移動先の安全確認と実際の移動（実行位置＝移動先）
#  壁にめり込みそうならその場で解除する
# ============================================================
execute unless block ~ ~ ~ #hookshot:passable run return run function hookshot:release
execute unless block ~ ~1 ~ #hookshot:passable run return run function hookshot:release

# 視点はそのまま、座標だけ更新（回転を書き換えないのでカクつかない）
tp @s ~ ~ ~

particle minecraft:end_rod ~ ~0.9 ~ 0.15 0.25 0.15 0.005 2 normal @a
