# ============================================================
#  Shift中の解除チェック（実行者＝プレイヤー）
#  フック中にShiftを押している間、いつでも解除できる。
#  release.mcfunction 自体が「フック中でなければ何もしない」ガードを
#  持っているので、押しっぱなしでも安全（毎tick呼んでも問題ない）
#  ※ モード切替は input/swap_detect.mcfunction（持ち替えキー）へ移動した。
#    Shiftは特別な球（Shift+左クリック）の修飾キーとしても使うため、
#    Shiftの押下だけでモードが切り替わってしまうと干渉するのを避けるため
# ============================================================
execute if predicate sc:sneaking if entity @s[tag=sc.hooked] run function sc:hook/release
