# ============================================================
#  台車を 1 ティック分、tp で動かす（マクロ / 実行者＝台車 / 実行位置＝台車）
#  ※ Motion（速度）を直接書き換える方式は、乗られている（ride されている）
#    モブでは実際には動かないことが確認できたため、フックの飛行
#    （hook/step.mcfunction）と同じ、実績のある tp ステップ方式に変更した。
#    台車自体は見えないので、tp で座標・回転を書き換えても違和感はない。
#    プレイヤーは ride で乗っているだけなので、台車の動きにゲーム側の
#    乗り物追従処理でスムーズに追従する（プレイヤー自身を直接 tp する
#    わけではないので、視点はカクつかない）。
# ============================================================

# 支点が消えていたら切り離す
$execute unless entity @e[tag=hs.anchor,scores={hs.id=$(id)}] run return run function hookshot:pull/detach

# 到達したら打ち上げ処理へ
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..1.6] run return run function hookshot:pull/arrive with storage hookshot:v

# 着弾点に近づくほど減速する（急停止を防ぐイーズアウト）
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=1.6..4] if score @s hs.spd matches 61.. run scoreboard players set @s hs.spd 60
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=4..8] if score @s hs.spd matches 121.. run scoreboard players set @s hs.spd 120

particle minecraft:glow ~ ~ ~ 0.1 0.15 0.1 0 1 normal @a

# 台車自身の向きを実際にマーカーの方へ回転させる（台車は見えないので
# 回転を変えても違和感はない。これで以降のステップは、hook/step と
# 同じように「台車自身の向き」を基準にした ^ ^ ^ 移動で安全に進められる）
$execute facing entity @e[tag=hs.anchor,scores={hs.id=$(id)},limit=1] feet run tp @s ~ ~ ~ ~ ~

# 1 tick 分の移動を 8 分割し、フックの飛行と全く同じ 0.25 ブロック
# ステップごとに着弾判定をしながら進む（最高速度 2.0 ブロック / tick でも
# 1 ステップ最大 0.25 ブロックの精度で衝突判定できる）
execute store result storage hookshot:v step double 0.00125 run scoreboard players get @s hs.spd
scoreboard players set @s hs.sub 8
function hookshot:pull/step with storage hookshot:v
