# ============================================================
#  自動追尾弾の毎ティック処理（実行者＝マーカー / 実行位置＝マーカー）
#  発射時にロックオンした対象（uw.homingtgt）だけを追い続ける。
#  最大寿命 100 tick（5秒）、1 tick に 4 ブロック進む
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 100.. run function uw:crossbow/homing/expire

# ロックオン対象がまだいれば、そちらへ向きを合わせる（死亡等で消えていればそのまま直進）
execute if entity @e[tag=uw.homingtgt] facing entity @e[tag=uw.homingtgt,limit=1,sort=nearest] feet run tp @s ~ ~ ~ ~ ~

scoreboard players set @s uw.sub 4
function uw:crossbow/homing/step
