# ============================================================
#  指定 ID のフック・マーカー・ロープを片付ける（マクロ）
# ============================================================

# ロープ・台車用のコウモリは kill すると死亡音やリードのドロップが
# 出るので、奈落へ飛ばして静かに消滅させる
$execute as @e[tag=hs.rope,scores={hs.id=$(id)}] at @s run function hookshot:util/drop_rope
$execute as @e[tag=hs.carrier,scores={hs.id=$(id)}] at @s run function hookshot:util/quiet_kill

$kill @e[tag=hs.hook,scores={hs.id=$(id)}]
$kill @e[tag=hs.anchor,scores={hs.id=$(id)}]
