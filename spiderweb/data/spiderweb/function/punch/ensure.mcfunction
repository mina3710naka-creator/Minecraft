# 対応する当たり判定（Interaction）がなければ新規生成する（マクロ / 実行者＝プレイヤー）
$execute unless entity @e[type=interaction,tag=sw.puncher,scores={sw.pid=$(pid)}] run function spiderweb:punch/spawn with storage spiderweb:v
