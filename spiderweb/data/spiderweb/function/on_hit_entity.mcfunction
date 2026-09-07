# ============================================================
#  左クリック（MOBへの攻撃）を検知（実行者＝ダメージを与えたプレイヤー）
#  data/spiderweb/advancement/on_hit.json の報酬として呼ばれる
#  （minecraft:player_hurt_entity ＝ 実際に何らかのMOBへダメージを
#  与えた瞬間に発火する、バニラ標準のトリガー）。
#  右クリック（アイテム使用）とは完全に別の経路なので、クロスボウの
#  発射やその他の操作を一切妨げない。
#
#  アドバンスメントは一度達成すると同じ条件では二度と発火しないため、
#  ここで即座に revoke して「未達成」に戻す。次に実際にMOBへダメージを
#  与えた瞬間、ゲーム側が自動でまた達成→この関数を再び呼び出す、という
#  形でいつでも再トリガーできる。
#  ※ ここで advancement grant を呼び返すと、その grant がまたこの
#    報酬関数を呼び出してしまい無限ループになる。revoke だけに留める。
# ============================================================
advancement revoke @s only spiderweb:on_hit

# ウェブシューターを持っている時だけ、通常の攻撃に加えて巣除去弾も発射する
# （通常の攻撃自体はここでは一切妨げない）
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run return 0

function spiderweb:fire_ball
