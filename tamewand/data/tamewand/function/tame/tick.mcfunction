# ============================================================
#  テイム済みの仲間の毎ティック処理（実行者＝その仲間 / 実行位置＝仲間）
# ============================================================

# 残り時間を減らし、切れたら元の野生状態に戻す
scoreboard players remove @s tw.life 1
execute if score @s tw.life matches ..0 run return run function tamewand:tame/release

# オーナーがまだログイン中かの確認や、行動の分岐にはIDが要るのでストレージへ
execute store result storage tamewand:v pid int 1 run scoreboard players get @s tw.owner
function tamewand:tame/tick2 with storage tamewand:v
