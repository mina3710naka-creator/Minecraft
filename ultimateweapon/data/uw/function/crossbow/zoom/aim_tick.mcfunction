# ============================================================
#  ズーム中の毎ティック処理（実行者＝プレイヤー）
#  クロスボウの発射（=右クリックを離した瞬間）ではなく、スパイグラスを
#  実際に右クリック長押しし始めた瞬間から本当のズームを開始する。
#  （以前はここでいきなり離し判定をしていたため、切り替え直後の
#  1〜2tickでまだ何も押していないのに「離した」と誤判定し、
#  ズームする間もなく即発射／キャンセルされてしまっていた）
# ============================================================
execute unless entity @s[tag=uw.zoomactive] run function uw:crossbow/zoom/wait_start
execute if entity @s[tag=uw.zoomactive] run function uw:crossbow/zoom/active_tick
