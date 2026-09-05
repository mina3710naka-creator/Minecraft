# ============================================================
#  毎ティック処理
# ============================================================

# --- 1) 装備状態の検知 ---
execute as @a[tag=!ob.worn] if items entity @s armor.feet *[minecraft:custom_data~{ob_boots:1b}] run function opboots:equip/on_equip
execute as @a[tag=ob.worn] unless items entity @s armor.feet *[minecraft:custom_data~{ob_boots:1b}] run function opboots:equip/on_unequip

# --- 2) 装備中: 全ダメージ無効化（体力を毎ティック満タンに戻す） ---
execute as @a[tag=ob.worn] at @s run function opboots:util/full_heal

# --- 3) 装備中: しゃがみ長押しで爆発ジャンプをチャージ ---
execute as @a[tag=ob.worn] at @s run function opboots:jump/charge

# --- 4) 爆発ジャンプ中の放物線移動 ---
execute as @a[tag=ob.jumping] at @s run function opboots:jump/tick
