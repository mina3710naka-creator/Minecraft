# ============================================================
#  台車の1ステップ移動＋着弾判定（マクロ / 実行者＝台車）
# ============================================================

# 足元・頭上がどちらも空いていれば、そのまま通常の高さで前進
$execute if block ^ ^ ^$(step) #spiderweb:passable if block ^ ^1 ^$(step) #spiderweb:passable run return run function spiderweb:pull/step_move with storage spiderweb:v

# 足元は塞がっていても、1段上とさらにもう1段上が空いていれば段差を乗り越える
$execute if block ^ ^1 ^$(step) #spiderweb:passable if block ^ ^2 ^$(step) #spiderweb:passable run return run function spiderweb:pull/step_move_up with storage spiderweb:v

# 1段上げても塞がっている＝大きな壁。マーカーに十分近ければ到達とみなす
$execute if entity @e[tag=sw.anchor,scores={sw.id=$(id)},distance=..3] run return run function spiderweb:pull/arrive with storage spiderweb:v
execute run return run function spiderweb:pull/detach with storage spiderweb:v
