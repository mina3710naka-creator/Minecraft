# ============================================================
#  モード2: 巣づくりの発射（実行者＝プレイヤー）
#  糸を1個消費する。/clear の戻り値（消費できた個数）で判定する
# ============================================================
execute store result score @s sc.d run clear @s minecraft:string 1
execute if score @s sc.d matches 0 run function sc:web/no_string
execute if score @s sc.d matches 1.. run function sc:web/fire_do
