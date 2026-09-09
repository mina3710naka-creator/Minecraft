# ============================================================
#  レーザーの毎ティック処理（実行者＝プレイヤー）
#  5秒間（100tick）、自分の現在地とこうもり（固定終点）の間に
#  ビームを描画し続ける
# ============================================================
scoreboard players remove @s uw.t 1
execute if score @s uw.t matches ..0 run function uw:crossbow/laser/end
execute unless entity @s[tag=uw.laser] run return 0

execute unless entity @e[tag=uw.laserbat,distance=..105] run return 0

tag @e[tag=uw.laserhit] remove uw.laserhit
scoreboard players set @s uw.sub 60
execute anchored eyes positioned ^ ^ ^0 facing entity @e[tag=uw.laserbat,limit=1,sort=nearest] feet run function uw:crossbow/laser/beam_step
