# ============================================================
#  通常攻撃（近接攻撃）でMOBに命中したときの検知（実行者＝攻撃したプレイヤー）
#  data/tamewand/advancement/hit.json の報酬として呼ばれる
#  （minecraft:player_hurt_entity は「プレイヤー自身の近接攻撃」で
#  ダメージを与えた瞬間だけ発火する。投げたトライデントの着弾では
#  発火しないので、普通に投げて使う分には一切干渉しない）
#
#  アドバンスメントは一度達成すると同じ条件では二度と発火しないため、
#  ここで即座に revoke して「未達成」に戻す。次に実際に何かへダメージを
#  与えた瞬間、ゲーム側が自動でまた達成→この関数を再び呼び出す
# ============================================================
advancement revoke @s only tamewand:hit

# テイムの杖を持っている時だけ反応（通常の攻撃自体は一切妨げない）
execute unless items entity @s weapon.mainhand *[minecraft:custom_data~{tamewand:1b}] run return 0

# スニーク中は「命令」、そうでなければ「テイム」
execute if predicate tamewand:sneaking run function tamewand:command/dispatch
execute unless predicate tamewand:sneaking run function tamewand:tame/dispatch
