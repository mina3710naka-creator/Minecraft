# ============================================================
#  Shift+右クリック: 目の前に巨大な爆発を起こす（実行者＝プレイヤー）
#  敵にダメージを与えるが、ブロックは一切破壊しない
#  （実際に /summon tnt 等で爆発を起こすのではなく、パーティクルと
#    damage コマンドだけで再現しているため、地形やブロックには
#    一切影響しない）
# ============================================================
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 1.5 0.8
title @s actionbar {"text":"大爆発！","color":"gold","bold":true}

execute anchored eyes positioned ^ ^ ^4 run function uw:sword/explosion/burst
