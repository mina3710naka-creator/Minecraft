# 手元からフック（またはマーカー）へ向けてパーティクルの線を引く
$execute if entity @e[tag=hs.tip,scores={hs.id=$(id)},limit=1] anchored eyes positioned ^0.3 ^-0.3 ^0.45 anchored feet facing entity @e[tag=hs.tip,scores={hs.id=$(id)},limit=1] feet run function hookshot:rope/step with storage hookshot:v
