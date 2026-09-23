# ============================================================
#  攻撃モード・実際の突撃／攻撃（マクロ / 実行者＝仲間 / 実行位置＝仲間）
# ============================================================

# 目標が消えて（倒された等）いたら追従モードに戻る
$execute unless entity @e[tag=tw.target,scores={tw.tid=$(tid)}] run return run function tamewand:tame/mode_follow

# 十分近ければ攻撃、遠ければ向きを合わせて突撃
$execute if entity @e[tag=tw.target,scores={tw.tid=$(tid)},distance=..2.5] run function tamewand:tame/attack_hit with storage tamewand:v
$execute unless entity @e[tag=tw.target,scores={tw.tid=$(tid)},distance=..2.5] facing entity @e[tag=tw.target,scores={tw.tid=$(tid)},limit=1] feet run tp @s ~ ~ ~ ~ ~
$execute unless entity @e[tag=tw.target,scores={tw.tid=$(tid)},distance=..2.5] run tp @s ^ ^ ^0.6

effect give @s minecraft:fire_resistance 1 5 true
