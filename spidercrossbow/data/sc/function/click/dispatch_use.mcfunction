# ============================================================
#  右クリック（実行者＝プレイヤー、視点はクリック時のまま）
#  すでにフック中なら解除（バックアップ操作）、それ以外は現在の
#  モードで発射する
# ============================================================
execute if entity @s[tag=sc.hooked] run return run function sc:hook/release

execute if score @s sc.mode matches 0 unless entity @s[tag=sc.hookflying] unless entity @s[tag=sc.hooked] unless entity @s[tag=sc.pulling] run function sc:hook/fire
execute if score @s sc.mode matches 1 unless entity @s[tag=sc.webflying] run function sc:web/fire
