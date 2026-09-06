# ============================================================
#  爆発10マスジャンプ 開始（実行者＝プレイヤー、シフト3秒溜め後のジャンプ）
# ============================================================

playsound minecraft:entity.generic.explode player @a ~ ~ ~ 1.0 1.0
particle minecraft:explosion_emitter ~ ~0.2 ~ 0 0 0 0 1
particle minecraft:explosion ~ ~0.4 ~ 0.6 0.4 0.6 0 25

# 周囲のモンスターなどを吹き飛ばす（本人はブーツの効果で無傷）
# ブロックは壊さない（damage コマンドのみを使用）
damage @e[type=!player,distance=..4] 4 minecraft:explosion at ~ ~ ~

function opboots:jump/leap
