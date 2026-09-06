# ============================================================
#  爆発ジャンプの追加演出（実行者＝プレイヤー、シフト3秒溜め後のジャンプ）
#  10マスジャンプ本体は呼び出し元(jump/detect)が別途必ず実行するため、
#  ここでは爆発の音・パーティクル・周囲への吹き飛ばしだけを行う
#  (ファイル名/関数名は元々jump/explosiveだったが、以前このファイル内の
#   damageコマンドが複数ヒットしうる範囲セレクターをそのまま渡していて
#   構文違反となり、ファイル全体が読み込めず「不明な関数」になっていた。
#   その修正と合わせてjump/burstに改名した)
# ============================================================

playsound minecraft:entity.generic.explode player @a ~ ~ ~ 1.0 1.0
particle minecraft:explosion_emitter ~ ~0.2 ~ 0 0 0 0 1
particle minecraft:explosion ~ ~0.4 ~ 0.6 0.4 0.6 0 25

# 周囲のモンスターなどを吹き飛ばす（本人はブーツの効果で無傷）
# ブロックは壊さない（damage コマンドのみを使用）
# ※ /damage の target 引数は複数ヒットする範囲セレクターを渡すと
#   「不明な関数」としてこの関数自体が読み込めなくなる(1体のみ許可)。
#   execute as で1体ずつ@sに割り当てて回すことで対応する
execute as @e[type=!player,distance=..4] run damage @s 4 minecraft:explosion at ~ ~ ~
