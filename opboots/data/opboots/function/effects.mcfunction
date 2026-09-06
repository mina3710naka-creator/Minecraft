# ============================================================
#  ブーツの常時効果（実行者＝装備しているプレイヤー）
#  効果時間は0.1秒（2tick）とごく短くし、装備している間は毎tick
#  かけ直し続けることで「常時効果」にしている（脱いだ瞬間にすぐ
#  効果が切れるようにするため、あえて長時間を指定しない）
# ============================================================

effect give @s minecraft:resistance 0.1 4 true
effect give @s minecraft:jump_boost 0.1 3 true
effect give @s minecraft:speed 0.1 1 true
effect give @s minecraft:fire_resistance 0.1 0 true
