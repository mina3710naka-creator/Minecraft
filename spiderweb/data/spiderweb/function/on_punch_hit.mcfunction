# ============================================================
#  左クリック（MOBへの攻撃）を検知（実行者＝ダメージを与えたプレイヤー）
#  アドバンスメント（minecraft:player_hurt_entity）の報酬として呼ばれる。
#  右クリック（アイテム使用）とは完全に独立した仕組みなので、
#  クロスボウの発射を邪魔することはない。
#
#  達成済みのアドバンスメントは同じ条件では二度と発火しないため、
#  ここで即座に revoke して「未達成」に戻す。次に実際にMOBへ
#  ダメージを与えた瞬間、ゲーム側が自動でまた達成 → この関数を
#  再度呼び出す、という形で毎回トリガーできる。
#  ※ ここで advancement grant を呼び返すと、その grant 自体が
#    再度この報酬関数を呼び出してしまい無限ループになるため、
#    絶対に revoke だけに留めること。
# ============================================================
advancement revoke @s only spiderweb:punch_hit

# ウェブシューターを持っている時だけ、通常の攻撃に加えて巣除去弾も発射する
# （通常の攻撃自体はここでは一切妨げない）
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run return 0

function spiderweb:fire_clear
