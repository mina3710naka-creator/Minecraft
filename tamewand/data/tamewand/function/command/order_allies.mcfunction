# ============================================================
#  自分の仲間全員に攻撃を命令する
#  （マクロ / 実行者＝命令したプレイヤー / 実行位置＝プレイヤー、
#  command/dispatch.mcfunction から $(pid) と $(tid) を積んだ
#  ストレージ付きで呼ばれる）
# ============================================================
$execute as @e[tag=tw.tamed,distance=..16,scores={tw.owner=$(pid)}] at @s run function tamewand:command/order_one with storage tamewand:v
