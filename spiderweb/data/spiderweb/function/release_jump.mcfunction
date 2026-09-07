# ============================================================
#  Spaceキー: 張り付き解除 + 約5マスジャンプ（実行者＝プレイヤー）
#  ダメージやノックバック無効化系の影響を受けない、レビテーション
#  （浮遊効果）だけによる純粋な移動で打ち上げる。amplifier 23を
#  約7tickだけ効かせてから切り、あとは通常の重力に落下を任せる。
# ============================================================
execute unless entity @s[tag=sw.attached] run return 0

particle minecraft:cloud ~ ~0.1 ~ 0.3 0.1 0.3 0.01 15
playsound minecraft:entity.ender_dragon.flap player @a ~ ~ ~ 0.6 1.6

tag @s add sw.launching
scoreboard players set @s sw.lt 0
effect give @s minecraft:levitation 4 23 true

function spiderweb:release
