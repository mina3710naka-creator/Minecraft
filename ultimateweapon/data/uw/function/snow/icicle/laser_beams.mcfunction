# ============================================================
#  氷結レーザーの見た目のビーム描画(実行者＝プレイヤー)
#  uw.icetgt タグの付いた対象を1体ずつ選び、視点をそちらへ向けて
#  ビームを描画し終えたらタグを外して次の対象へ進む
# ============================================================
execute unless entity @e[tag=uw.icetgt] run return 0

scoreboard players set @s uw.sub 20
execute anchored eyes facing entity @e[tag=uw.icetgt,limit=1,sort=nearest] feet positioned ^ ^ ^0 run function uw:snow/icicle/laser_beam_step
