# ============================================================
#  スパイダークロスボウを実際に撃った瞬間の処理（実行者＝プレイヤー）
#  見た目だけの矢（無限エンチャントで消費されない実物）を消し、
#  現在のモードに応じたアビリティを発動する。
#  すでにフック中に発射（＝再度撃った）場合は解除の合図として扱う
# ============================================================
kill @e[type=minecraft:arrow,tag=sc.seen,distance=..1.5,limit=1,sort=nearest]

execute if entity @s[tag=sc.hooked] run return run function sc:hook/release

execute if score @s sc.mode matches 0 unless entity @s[tag=sc.hookflying] unless entity @s[tag=sc.pulling] run function sc:hook/fire
execute if score @s sc.mode matches 1 unless entity @s[tag=sc.webflying] run function sc:web/fire
