# ============================================================
#  当たり判定（Interaction）の追従・後始末・攻撃検知
#  （マクロ / 実行者＝Interaction）
# ============================================================

# 対応するプレイヤーがいなくなっていたら片付ける
$execute unless entity @a[scores={sw.pid=$(pid)}] run return run function spiderweb:punch/despawn

# ウェブシューターを手放していたら片付ける
$execute unless items entity @a[scores={sw.pid=$(pid)},limit=1] weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run return run function spiderweb:punch/despawn

# プレイヤーの足元へ追従（widthとheightで目の高さまで含む判定になっている）
$execute at @a[scores={sw.pid=$(pid)},limit=1] run tp @s ~ ~ ~

# 攻撃（左クリック）を検知：Interactionのattack.timestampが「今」と一致するか調べる
execute store result storage spiderweb:v now int 1 run time query gametime
$execute if entity @s[nbt={attack:{timestamp:$(now)}}] run function spiderweb:punch/fire with storage spiderweb:v
