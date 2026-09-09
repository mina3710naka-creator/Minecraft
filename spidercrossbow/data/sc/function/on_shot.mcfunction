# ============================================================
#  スパイダークロスボウを実際に撃った瞬間の処理（実行者＝プレイヤー）
#  本物の矢はすでに check_fire.mcfunction でキル済み。現在のモードに
#  応じて、自分の視点方向から新しくフック弾／巣づくり弾を生成する。
#  すでにフック中に発射（＝再度撃った）場合は解除の合図として扱う
# ============================================================
execute if entity @s[tag=sc.hooked] run return run function sc:hook/release

execute if score @s sc.mode matches 0 unless entity @s[tag=sc.hookflying] unless entity @s[tag=sc.pulling] run function sc:hook/fire
execute if score @s sc.mode matches 1 unless entity @s[tag=sc.webflying] run function sc:web/fire
