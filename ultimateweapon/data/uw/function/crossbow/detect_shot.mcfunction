# ============================================================
#  クロスボウの発射を検知（実行者＝プレイヤー / 実行位置＝プレイヤー）
#  クロスボウの「構える→離す」の統計は不安定なため使わず、実際に
#  自分の近くに新しい矢が出現したことそのものを発射の合図にする。
#  ※以前は execute on owner で判定していたが、実機で反応しない
#    ケースが確認されたため、モード切替と同じ「自分の手持ち
#    アイテムを毎tick直接確認する」方式に統一した。
# ============================================================
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{uw_crossbow:1b}] run function uw:crossbow/check_fire
execute if items entity @s weapon.offhand *[minecraft:custom_data~{uw_crossbow:1b}] run function uw:crossbow/check_fire
