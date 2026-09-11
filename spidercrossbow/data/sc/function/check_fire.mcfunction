# ============================================================
#  自分の近くに未処理の矢がいないか確認する（実行者＝プレイヤー）
#  地面に刺さって止まっている古い矢（inGround）は、たまたま近づいた
#  だけで誤検知しないよう対象から除外する。
#  見つけた本物の矢はその場でキルし（見た目だけの矢のため、位置や
#  向きには一切頼らない）、実際の発射物は on_shot 側で自分の視点方向
#  から新しく生成する
# ============================================================
execute unless entity @e[type=minecraft:arrow,tag=!sc.seen,nbt=!{inGround:1b},distance=..2] run return 0

tag @e[type=minecraft:arrow,tag=!sc.seen,nbt=!{inGround:1b},distance=..2,limit=1,sort=nearest] add sc.seen
kill @e[type=minecraft:arrow,tag=sc.seen,distance=..2,limit=1,sort=nearest]
function sc:on_shot
