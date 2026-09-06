# ============================================================
#  ウィンドチャージの爆風で上に打ち上げる（マクロ / 実行者＝プレイヤー）
#  防具立てや Motion の直接書き換え、台車を tp で持ち上げる方式と違い、
#  本物の爆風ノックバック（プレイヤーにもきちんと働く数少ない「本物の
#  速度」を与える手段）で押し上げるので、動きが不自然にならない。
# ============================================================

# プレイヤーを台車から降ろし、台車はここで即座に片付ける
# （死亡演出・リード等は出ないよう util/quiet_kill 経由で消す）
ride @s dismount
$execute as @e[tag=hs.carrier,scores={hs.id=$(id)}] at @s run function hookshot:util/quiet_kill

# ウィンドチャージで上向きの本物のノックバックを与える（ブロック破壊
# なし）。静止したまま出すと当たり判定が働かず起爆しないことがあるため、
# 体の中心あたりに出してから下向きの速度を与え、自分の体を貫通させる
# 形で確実に接触・起爆させる（2 発重ねて打ち上げを強める）
execute positioned ~ ~1 ~ summon minecraft:wind_charge run data modify entity @s Motion set value [0.0,-0.8,0.0]
execute positioned ~ ~1 ~ summon minecraft:wind_charge run data modify entity @s Motion set value [0.0,-0.8,0.0]

function hookshot:release
