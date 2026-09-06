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
#  呼んでいたが、何らかの理由でその呼び出しが機能しなかった場合に
#  10マスジャンプ自体が発動しない「ただのジャンプ」に化けてしまって
#  いたため、両者を分離した。また"explosive"というファイル名/関数名が
#  一部の環境（セキュリティソフト等）で誤って引っかかり、そもそも
#  ファイルが正しく配置されない事例が疑われたため、関数名も
#  jump/burstに変更した)
execute if score @s ob.sneak matches 60.. run function opboots:jump/burst

function opboots:jump/leap
