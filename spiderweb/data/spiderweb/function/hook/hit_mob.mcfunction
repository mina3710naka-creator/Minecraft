# ============================================================
#  MOBに着弾した場合のタグ付け（マクロ）
#  実行者＝刺さったMOB / 実行位置＝そのMOBの現在地
#  killせず本物のMOBに直接タグを付けて、以降のティックでプレイヤー側へ
#  tpステップで引き寄せる（yank）。持ち主側は sw.yanking に遷移する。
# ============================================================
$scoreboard players set @s sw.id $(id)
tag @s add sw.yankmob
tag @s add sw.tip

$execute as @a[tag=sw.hooking,scores={sw.id=$(id)},limit=1] at @s run function spiderweb:yank/start with storage spiderweb:v
