# ============================================================
#  新しく出現した矢を1回だけ確認する（実行者＝矢 / 実行位置＝矢）
#  「構える→離す」の2段階操作であるクロスボウは、使用回数の統計
#  （minecraft.used:minecraft.crossbow）がどちらの操作で増えるか
#  版によって不安定なため使わず、実際に矢が出現したことそのものを
#  発射の合図にする。矢自身のNBTには依存しない（矢の持ち主が、
#  出現した瞬間にウェブシューターを構えていたかどうかだけを見る）。
#  一度確認した矢には sw.seen を付けて二度と処理しない。
# ============================================================
tag @s add sw.seen
execute on owner at @s if items entity @s weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run function spiderweb:on_shot
