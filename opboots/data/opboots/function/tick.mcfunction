# ============================================================
#  毎ティック処理
# ============================================================

# --- 0) 足に「最強のブーツ」を装備しているプレイヤーへ共通タグを付与 ---
tag @a remove ob.wearing
execute as @a at @s if items entity @s armor.feet *[minecraft:custom_data~{opboots:1b}] run tag @s add ob.wearing

# --- 1) 常時効果（耐性V・跳躍強化IV・移動速度上昇・炎耐性） ---
execute as @a[tag=ob.wearing] at @s run function opboots:effects

# --- 2) 被ダメージ無効化の保険（耐性Vで防ぎきれない特殊ダメージ用） ---
execute as @a[tag=ob.wearing] at @s run function opboots:heal_check

# --- 3) シフト保持時間の計測（脱いだら即リセット） ---
execute as @a[tag=ob.wearing] at @s run function opboots:sneak_track
scoreboard players set @a[tag=!ob.wearing] ob.sneak 0

# --- 4) ジャンプ検知（シフト+ジャンプ／シフト3秒溜め+ジャンプ） ---
execute as @a[tag=ob.wearing,scores={ob.jump=1..}] at @s run function opboots:jump/detect
scoreboard players set @a ob.jump 0

# --- 5) 10マスジャンプ中の処理（レビテーションの管理） ---
execute as @a[tag=ob.leaping] at @s run function opboots:jump/tick
