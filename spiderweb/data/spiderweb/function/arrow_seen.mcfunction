# ============================================================
#  新しく出現した矢を1回だけチェックする（実行者＝矢 / 実行位置＝矢）
#  持ち主が今まさにウェブシューターを構えていれば、それを
#  「発射された」合図として扱う。矢自身のNBTは一切見ない
#  （版によって構造が変わりやすく不安定なため）。
#  一度チェックした矢には sw.seen を付けて、二度と処理しない。
# ============================================================
tag @s add sw.seen
execute on owner at @s if items entity @s weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run function spiderweb:use
