# ============================================================
#  左クリック検知用の当たり判定の初期化（マクロ / 実行者＝生成されたInteraction）
#  プレイヤーの足元を基準に、目の高さを確実に含む大きさの当たり判定にする。
#  これにより、視線の先に何があってもプレイヤー自身の判定が最優先で
#  ヒットするため、左クリック（攻撃）そのものを検知できる。
#  ※ ダメージ・ノックバック・見た目は一切発生しない（Interactionは
#    NBTに攻撃記録が残るだけの当たり判定専用エンティティ）。
# ============================================================
data merge entity @s {width:1.6f,height:2.4f,response:0b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
tag @s add sw.puncher
$scoreboard players set @s sw.pid $(pid)
scoreboard players set @s sw.t 0
