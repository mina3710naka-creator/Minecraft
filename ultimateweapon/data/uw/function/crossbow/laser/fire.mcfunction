# ============================================================
#  モード2: 持続レーザーの発射開始（実行者＝プレイヤー）
#  視点方向へこうもりを瞬時に100ブロック先まで飛ばし、その位置を
#  終点として5秒間（100tick）固定されたレーザーを設置する
#  ※ anchored eyes だけでは実行位置（position）自体は視点まで
#    移動しない（^^^による計算にのみ影響する）ため、必ず
#    positioned ^ ^ ^0 を続けて実際に視点位置へ移動させること。
#    これを忘れていたため、こうもりが足元付近に出現し、直後に
#    地面へ衝突してレーザーが全く伸びない不具合が起きていた。
# ============================================================
tag @s add uw.laser
scoreboard players set @s uw.t 100
playsound minecraft:entity.blaze.shoot player @a ~ ~ ~ 1.2 0.5
playsound minecraft:item.trident.riptide_1 player @a ~ ~ ~ 1 2

execute anchored eyes positioned ^ ^ ^0 summon minecraft:bat run function uw:crossbow/laser/probe_init
