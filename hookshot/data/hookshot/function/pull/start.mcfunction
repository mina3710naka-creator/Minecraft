# ============================================================
#  引き寄せ開始（実行者＝プレイヤー）
# ============================================================
tag @s remove hs.hooking
tag @s add hs.pulling
scoreboard players set @s hs.pt 0
# 初速 0.2 ブロック / ティック（ここから徐々に加速して滑らかに動く）
scoreboard players set @s hs.spd 20

# 毎ティック tp で座標を上書きすると、tick の合間にクライアント側で
# 重力落下が予測され、次の tp で軌道に引き戻される……という綱引きが
# 起きてガクつく。slow_falling で重力の影響をほぼ消してこれを防ぐ。
effect give @s minecraft:slow_falling 1 0 true

playsound minecraft:item.lead.tied player @a ~ ~ ~ 0.8 1.3
