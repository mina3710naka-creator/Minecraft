# ============================================================
#  台車の 1 ステップ移動＋着弾判定（マクロ / 実行者＝台車）
#  hook/step.mcfunction と同じ考え方：進む先を確認してから 1 歩ずつ進む。
#  進む先の足元が塞がっていても、1 段上（さらにもう 1 段上まで）が
#  空いていれば、ブロック 1 個分程度の段差として乗り越える（生垣・
#  柵・少しの高低差程度で毎回止まってしまうのを防ぐため）。
#  1 段上げても塞がっている場合だけ、大きな壁とみなす。
# ============================================================

# 足元・頭上がどちらも空いていれば、そのまま通常の高さで前進
$execute if block ^ ^ ^$(step) #hookshot:passable if block ^ ^1 ^$(step) #hookshot:passable run return run function hookshot:pull/step_move with storage hookshot:v

# 足元は塞がっていても、1 段上とさらにもう 1 段上が空いていれば、
# 段差として乗り越える
$execute if block ^ ^1 ^$(step) #hookshot:passable if block ^ ^2 ^$(step) #hookshot:passable run return run function hookshot:pull/step_move_up with storage hookshot:v

# 1 段上げても塞がっている＝大きな壁。台車自身にも当たり判定があるため、
# マーカーが埋め込まれた壁のすぐ手前で「進めない」と判定され、到達
# しきい値まで詰め切る前に激突扱いで切り離されてしまうことがある。
# 塞がっていた地点がマーカーに十分近ければ、想定外の障害物ではなく
# 到達とみなして打ち上げに進める。
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..3] run return run function hookshot:pull/arrive with storage hookshot:v
execute run return run function hookshot:pull/detach with storage hookshot:v
