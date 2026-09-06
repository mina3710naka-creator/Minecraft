# ============================================================
#  ジャンプ検知（実行者＝ブーツ装備プレイヤー、ジャンプした直後のtick）
# ============================================================

# 演出中（滞空中）の多重発動を防ぐ
execute if entity @s[tag=ob.leaping] run return 0

# シフトを押していない通常のジャンプは何もしない
execute unless predicate opboots:sneaking run return 0

# シフトを3秒（60tick）以上押し続けてからのジャンプには爆発演出を追加する。
# 10マスジャンプ本体(jump/leap)は爆発演出の成否に関係なく必ず実行する
# (以前は"function opboots:jump/explosive"の中で10マスジャンプまで
#  呼んでいたが、当時そのファイル内のdamageコマンドが複数ヒットしうる
#  範囲セレクターをそのまま渡していて構文違反になっており、ファイル
#  全体が読み込めず「不明な関数」になっていた。10マスジャンプまで
#  巻き込まれて「ただのジャンプ」になってしまっていたため両者を分離し、
#  damageコマンドの修正と合わせて関数名もjump/burstに変更した)
execute if score @s ob.sneak matches 60.. run function opboots:jump/burst

function opboots:jump/leap
