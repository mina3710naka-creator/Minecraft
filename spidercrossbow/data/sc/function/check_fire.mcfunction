# ============================================================
#  自分の近くに未処理の矢がいないか確認する（実行者＝プレイヤー）
#  地面に刺さって止まっている古い矢（inGround）は、たまたま近づいた
#  だけで誤検知しないよう対象から除外する
# ============================================================
execute unless entity @e[type=minecraft:arrow,tag=!sc.seen,nbt=!{inGround:1b},distance=..2] run return 0

tag @e[type=minecraft:arrow,tag=!sc.seen,nbt=!{inGround:1b},distance=..2,limit=1,sort=nearest] add sc.seen
function sc:on_shot
