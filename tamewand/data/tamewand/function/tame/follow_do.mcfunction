# ============================================================
#  追従モード・実際の移動（マクロ / 実行者＝仲間 / 実行位置＝仲間）
#  hookshot の台車と同じ「facing entity で向きを合わせてから
#  ローカル座標 ^ ^ ^ で前進」という実績のある方式で、オーナーへ
#  ふわふわ飛んで近づく
# ============================================================

# 3ブロックより離れていたら、オーナーの方を向いて少しずつ近づく
$execute unless entity @a[scores={tw.pid=$(pid)},distance=..3] facing entity @a[scores={tw.pid=$(pid)},limit=1] feet run tp @s ~ ~ ~ ~ ~
$execute unless entity @a[scores={tw.pid=$(pid)},distance=..3] run tp @s ^ ^ ^0.5

# 日中の自然発火などを防ぎ、仲間らしく安定させる
effect give @s minecraft:fire_resistance 1 5 true
