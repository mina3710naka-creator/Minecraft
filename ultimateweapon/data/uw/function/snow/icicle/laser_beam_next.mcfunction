# 今向けていた対象のタグを外し、次の対象へビーム描画を進める（実行者＝プレイヤー）
tag @e[tag=uw.icetgt,limit=1,sort=nearest] remove uw.icetgt
function uw:snow/icicle/laser_beams
