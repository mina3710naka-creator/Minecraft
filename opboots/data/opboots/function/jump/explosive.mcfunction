# ============================================================
#  爆発10マスジャンプ 開始（実行者＝プレイヤー、シフト3秒溜め後のジャンプ）
# ============================================================

# [DEBUG] 爆発ジャンプ側が実行されたかの確認用（不要になったら削除可）
tellraw @s {"text":"[DEBUG] jump/explosive 実行（爆発ジャンプ）","color":"red","bold":true}

playsound minecraft:entity.generic.explode player @a ~ ~ ~ 1.0 1.0
particle minecraft:explosion_emitter ~ ~0.2 ~ 0 0 0 0 1
particle minecraft:explosion ~ ~0.4 ~ 0.6 0.4 0.6 0 25

# 周囲のモンスターなどを吹き飛ばす（本人はブーツの効果で無傷）
# ブロックは壊さない（damage コマンドのみを使用）
damage @e[type=!player,distance=..4] 4 minecraft:explosion at ~ ~ ~

function opboots:jump/leap
