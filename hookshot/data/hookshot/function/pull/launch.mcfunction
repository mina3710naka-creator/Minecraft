# ============================================================
#  レビテーション（浮遊効果）で上に打ち上げる（マクロ / 実行者＝プレイヤー）
#  opboots の 10マスジャンプ（jump/leap.mcfunction）と同じ手法：
#  ダメージを伴わない純粋な効果による移動なので、地形やノックバック
#  無効化系の影響を受けず確実に働く。ダメージ+ノックバック方式
#  （damage ... at ...）は、体力を一時的に底上げしてから本物の
#  ダメージを通す一時しのぎが必要な上に不安定だったため、この方式に
#  切り替えた。
# ============================================================

# プレイヤーを台車から降ろし、台車はここで即座に片付ける
# （死亡演出・リード等は出ないよう util/quiet_kill 経由で消す）
ride @s dismount
$execute as @e[tag=hs.carrier,scores={hs.id=$(id)}] at @s run function hookshot:util/quiet_kill

# 真上に約5マス打ち上げる。opboots と同じ amplifier（23）を使い、
# 4tick 後に pull/launch_tick.mcfunction で効果を切って、あとは重力に
# 任せて落とす（opboots は同じ amplifier を 9tick 効かせて約10マスに
# なるよう調整済みなので、その半分弱の tick 数で約5マスを狙っている。
# 見た目に合わせて tick 数は調整可能）。
tag @s add hs.launching
scoreboard players set @s hs.lt 0
effect give @s minecraft:levitation 4 23 true

particle minecraft:cloud ~ ~0.1 ~ 0.3 0.1 0.3 0.01 15
playsound minecraft:entity.ender_dragon.flap player @a ~ ~ ~ 0.6 1.6

function hookshot:release
