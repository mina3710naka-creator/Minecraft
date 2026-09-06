# ============================================================
#  ブーツの常時効果（実行者＝装備しているプレイヤー）
#  効果時間は1秒（20tick）とごく短くし、装備している間は毎tick
#  かけ直し続けることで「常時効果」にしている（脱いだ瞬間にすぐ
#  効果が切れるようにするため、あえて長時間を指定しない）
# ============================================================

effect give @s minecraft:resistance 1 10 true
effect give @s minecraft:jump_boost 1 4 true
effect give @s minecraft:speed 1 15 true
effect give @s minecraft:fire_resistance 1 10 true
