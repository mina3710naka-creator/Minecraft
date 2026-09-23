# ============================================================
#  毎ティック処理
#  （攻撃の検知自体は advancement 経由の tamewand:on_hit で行うので
#  ここには含まない）
# ============================================================

# --- 1) テイム済みの仲間の行動（追従 / 攻撃）を更新 ---
execute as @e[tag=tw.tamed] at @s run function tamewand:tame/tick

# --- 2) 攻撃命令の目印（tw.target）のタイムアウト処理 ---
execute as @e[tag=tw.target] at @s run function tamewand:util/target_timeout
