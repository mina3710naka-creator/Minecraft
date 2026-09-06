# ============================================================
#  爆発ジャンプの追加演出（実行者＝プレイヤー、シフト3秒溜め後のジャンプ）
#  10マスジャンプ本体は呼び出し元(jump/detect)が別途必ず実行するため、
#  ここでは爆発の音・パーティクル・周囲への吹き飛ばしだけを行う
#  (ファイル名/関数名は元々jump/explosiveだったが、"explosive"という
#   語が一部環境で引っかかり「不明な関数」になる事例が疑われたため
#   jump/burstに変更した)
# ============================================================

playsound minecraft:entity.generic.explode player @a ~ ~ ~ 1.0 1.0
particle minecraft:explosion_emitter ~ ~0.2 ~ 0 0 0 0 1
particle minecraft:explosion ~ ~0.4 ~ 0.6 0.4 0.6 0 25

# 周囲のモンスターなどを吹き飛ばす（本人はブーツの効果で無傷）
# ブロックは壊さない（damage コマンドのみを使用）
damage @e[type=!player,distance=..4] 4 minecraft:explosion at ~ ~ ~
