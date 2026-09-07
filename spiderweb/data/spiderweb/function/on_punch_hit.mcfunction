# ============================================================
#  左クリック（MOBへの攻撃）を検知（実行者＝ダメージを与えたプレイヤー）
#  アドバンスメント（minecraft:player_hurt_entity）の報酬として呼ばれる。
#  右クリック（アイテム使用）とは完全に独立した仕組みなので、
#  クロスボウの発射を邪魔することはない。
#  ウェブシューターを持っている時だけ、通常のダメージに加えて
#  クモの巣除去弾も発射する（通常の攻撃自体は無効化しない）。
# ============================================================
advancement revoke @s only spiderweb:punch_hit
advancement grant @s only spiderweb:punch_hit

execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run return 0

function spiderweb:fire_clear
