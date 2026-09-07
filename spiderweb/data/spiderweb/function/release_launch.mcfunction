# ============================================================
#  Spaceキー: 張り付き解除 + 5マスジャンプ（実行者＝プレイヤー）
#  hookshotの到達時打ち上げと同じレビテーション方式を流用
#  （ダメージやノックバック無効化系の影響を受けず確実に働く）
# ============================================================
execute unless entity @s[tag=sw.attached] run return 0

particle minecraft:cloud ~ ~0.1 ~ 0.3 0.1 0.3 0.01 15
playsound minecraft:entity.ender_dragon.flap player @a ~ ~ ~ 0.6 1.6

tag @s add sw.launching
scoreboard players set @s sw.lt 0
effect give @s minecraft:levitation 4 23 true

function spiderweb:release
