# ============================================================
#  テイム済みの仲間の毎ティック処理・続き
#  （マクロ / 実行者＝その仲間、tame/tick.mcfunction から
#  $(pid) を積んだストレージ付きで呼ばれる）
# ============================================================

# オーナーがオフラインになったら元の野生状態に戻す
$execute unless entity @a[scores={tw.pid=$(pid)}] run return run function tamewand:tame/release

# モードに応じて行動
execute if score @s tw.mode matches 0 run function tamewand:tame/follow_step
execute if score @s tw.mode matches 1 run function tamewand:tame/attack_step
