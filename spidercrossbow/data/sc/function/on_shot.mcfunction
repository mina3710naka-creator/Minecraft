# ============================================================
#  スパイダークロスボウを実際に撃った瞬間の処理（実行者＝プレイヤー）
#  近くの矢（check_fire.mcfunctionが sc.seen を付けた1本）を、現在の
#  モードに応じてフック弾／巣づくり弾として乗っ取る。
#  すでにフック中に発射（＝再度撃った）場合は解除の合図として扱う
# ============================================================
execute if entity @s[tag=sc.hooked] run return run function sc:hook/release

execute if score @s sc.mode matches 0 unless entity @s[tag=sc.hookflying] unless entity @s[tag=sc.pulling] run function sc:hook/fire
execute if score @s sc.mode matches 1 unless entity @s[tag=sc.webflying] run function sc:web/fire
