# 右クリック / Shift+右クリック（実行者＝プレイヤー、視点はクリック時のまま）
# 通常の右クリックは周囲斬撃、Shift+右クリックは目の前の巨大爆発
execute if predicate uw:sneaking run function uw:sword/explosion/fire
execute unless predicate uw:sneaking run function uw:sword/circle/fire
