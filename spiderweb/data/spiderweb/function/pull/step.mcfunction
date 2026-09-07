# ============================================================
#  台車の1ステップ移動＋衝突判定（マクロ / 実行者＝台車）
#  足元・頭上が両方通り抜けられればそのまま進み、足元だけ塞がって
#  いても1段上まで空いていれば段差として乗り越える。それでも塞がって
#  いる場合は大きな壁とみなすが、支点に十分近ければ到達扱いにする
#  （台車自身の当たり判定で、支点直前の壁にめり込む前に止まって
#  しまい、到達しきい値に届かないまま激突扱いになるのを防ぐため）。
# ============================================================
$execute if block ^ ^ ^$(step) #spiderweb:passable if block ^ ^1 ^$(step) #spiderweb:passable run return run function spiderweb:pull/step_flat with storage spiderweb:v

$execute if block ^ ^1 ^$(step) #spiderweb:passable if block ^ ^2 ^$(step) #spiderweb:passable run return run function spiderweb:pull/step_up with storage spiderweb:v

$execute if entity @e[tag=sw.anchor,scores={sw.id=$(id)},distance=..3] run return run function spiderweb:pull/arrive with storage spiderweb:v
execute run return run function spiderweb:pull/detach with storage spiderweb:v
