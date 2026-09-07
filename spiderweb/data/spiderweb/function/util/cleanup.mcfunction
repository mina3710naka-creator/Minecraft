# ============================================================
#  指定IDのフック・巣づくり弾・マーカー・ロープ・台車・
#  引き寄せ中MOBのタグを片付ける（マクロ）
# ============================================================
$execute as @e[tag=sw.rope,scores={sw.id=$(id)}] at @s run function spiderweb:util/drop_rope
$execute as @e[tag=sw.carrier,scores={sw.id=$(id)}] at @s run function spiderweb:util/quiet_kill
$execute as @e[tag=sw.yankmob,scores={sw.id=$(id)}] run function spiderweb:yank/cleanup

$kill @e[tag=sw.hook,scores={sw.id=$(id)}]
$kill @e[tag=sw.web,scores={sw.id=$(id)}]
$kill @e[tag=sw.anchor,scores={sw.id=$(id)}]
