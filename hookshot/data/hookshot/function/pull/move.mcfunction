# ============================================================
#  マーカーへ向かって移動（マクロ / 実行者＝プレイヤー）
# ============================================================

# 支点が消えていたら解除
$execute unless entity @e[tag=hs.anchor,scores={hs.id=$(id)}] run function hookshot:release

# 到達したら解除
$execute if entity @s[tag=hs.pulling] if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..1.4] run function hookshot:release

# マーカー方向へ hs.spd ブロックぶん進んだ地点を求めて移動
$execute if entity @s[tag=hs.pulling] facing entity @e[tag=hs.anchor,scores={hs.id=$(id)},limit=1] feet positioned ^ ^ ^$(spd) run function hookshot:pull/apply
