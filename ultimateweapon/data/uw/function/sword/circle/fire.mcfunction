# ============================================================
#  右クリック: 周囲斬撃を2連発（実行者＝プレイヤー）
#  1発目は即座に、2発目は6tick後に同じ地点で発生する
# ============================================================
function uw:sword/circle/burst
execute positioned ~ ~ ~ summon minecraft:marker run function uw:sword/circle/init2
