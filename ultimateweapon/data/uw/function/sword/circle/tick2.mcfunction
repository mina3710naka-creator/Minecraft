# ============================================================
#  2発目の待機カウント（実行者＝マーカー / 実行位置＝マーカー）
#  ※以前は6tick後に2発目を出していたが、バニラの被ダメージ無敵時間
#  （約10tick）の間に同じダメージ量で2発目を当てると無効化されて
#  しまうため、無敵時間より長い12tickに変更した
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 12.. run function uw:sword/circle/burst
execute if score @s uw.t matches 12.. run function uw:util/quiet_kill
