# 手元から対象（フック・アンカー・巣づくり弾・引き寄せ中MOB）へ向けてパーティクルの線を引く
$execute if entity @e[tag=sw.tip,scores={sw.id=$(id)},limit=1] anchored eyes positioned ^0.3 ^-0.3 ^0.45 anchored feet facing entity @e[tag=sw.tip,scores={sw.id=$(id)},limit=1] feet run function spiderweb:rope/step with storage spiderweb:v
