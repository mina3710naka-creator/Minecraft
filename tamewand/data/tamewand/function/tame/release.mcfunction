# ============================================================
#  テイム解除（実行者＝仲間だったMOB）
#  時間切れ・オーナーのログアウト・アンインストール時に呼ばれる。
#  NoAI 等を外すだけで、通常の野生のMOBに戻る
# ============================================================
data remove entity @s NoAI
data remove entity @s NoGravity
data remove entity @s PersistenceRequired
tag @s remove tw.tamed
tag @s remove tw.ent
scoreboard players reset @s tw.owner
scoreboard players reset @s tw.mode
scoreboard players reset @s tw.cd
scoreboard players reset @s tw.aim
scoreboard players reset @s tw.life

particle minecraft:smoke ~ ~1 ~ 0.2 0.3 0.2 0.02 10 normal @a
playsound minecraft:entity.wolf.whine player @a ~ ~ ~ 0.6 1.0
