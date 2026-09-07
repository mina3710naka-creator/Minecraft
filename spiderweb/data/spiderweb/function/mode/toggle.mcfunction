# ============================================================
#  Shiftキーでのモード切り替え（実行者＝プレイヤー）
#  ウェブシューターを持ち、未使用中（発射・引き寄せ・張り付き・MOB
#  引き寄せのいずれでもない）に、Shiftを「押した瞬間」だけ反応する。
#  張り付き中のShift+R（伸ばす）と衝突しないよう、押しっぱなしでは
#  何度も反応しないように立ち上がりエッジだけを見る。
#  ウェブシューターを持っていない・構えていない周囲のプレイヤーが
#  ただスニークしただけで反応しないよう、アイテム所持も条件にする。
# ============================================================

# 途中参加者用に、未設定ならスコアを0で初期化しておく
scoreboard players add @s sw.mode 0
scoreboard players add @s sw.sneakp 0
scoreboard players add @s sw.len 0

execute unless entity @s[tag=sw.active] if score @s sw.sneakp matches 0 if predicate spiderweb:sneaking if items entity @s weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run function spiderweb:mode/apply

execute if predicate spiderweb:sneaking run scoreboard players set @s sw.sneakp 1
execute unless predicate spiderweb:sneaking run scoreboard players set @s sw.sneakp 0
