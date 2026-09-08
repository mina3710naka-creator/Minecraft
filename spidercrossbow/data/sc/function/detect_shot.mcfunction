# ============================================================
#  クロスボウの発射を検知（実行者＝プレイヤー / 実行位置＝プレイヤー）
#  クロスボウの「構える→離す」の統計は不安定、`execute on owner`は
#  実機で反応しないことがあるため使わず、モード切替と同じ「専用
#  アイテムを持っているプレイヤー自身を毎tick直接確認し、自分の近く
#  （2ブロック以内）に未処理の矢が出現していないか調べる」方式を使う
#  （ultimateweaponパックで実機検証済みの手法）
# ============================================================
execute if items entity @s weapon.mainhand *[minecraft:custom_data~{sc_crossbow:1b}] run function sc:check_fire
execute if items entity @s weapon.offhand *[minecraft:custom_data~{sc_crossbow:1b}] run function sc:check_fire
