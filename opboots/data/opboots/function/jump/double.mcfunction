# ============================================================
#  二段ジャンプ検知（実行者＝プレイヤー / 装備中のみ呼び出される）
#  空中でもう一度ジャンプ入力すると、着地するまで1回だけ追加ジャンプできる
# ============================================================
execute if entity @s[nbt={OnGround:1b}] run tag @s remove ob.dj_used
execute unless entity @s[tag=ob.jumping] unless entity @s[tag=ob.dj_used] unless entity @s[nbt={OnGround:1b}] if score @s ob.jump matches 1.. run function opboots:jump/double_boost
