# 左クリック（実行者＝プレイヤー、視点はクリック時のまま）
execute if predicate uw:sneaking run function uw:sword/shockwave/fire
execute unless predicate uw:sneaking run function uw:sword/slash/fire
