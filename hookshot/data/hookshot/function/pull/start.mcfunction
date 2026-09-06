# ============================================================
#  引き寄せ開始（マクロ / 実行者＝プレイヤー / 実行位置＝プレイヤー）
#  見えない台車（コウモリ）を出し、プレイヤーをその上に ride で乗せる。
#  台車には本物の Motion（速度）を毎ティック与えて動かすので、
#  tp の座標上書きに頼らない、本物の物理挙動での移動になる。
#  ※ 防具立ては外力（ノックバック等）を受け付けない特殊仕様があり
#    Motion を書き込んでも動かないため、通常のモブ（コウモリ）を使う。
# ============================================================
tag @s remove hs.hooking
tag @s add hs.pulling

execute summon minecraft:bat run function hookshot:pull/carrier_init with storage hookshot:v

playsound minecraft:item.lead.tied player @a ~ ~ ~ 0.8 1.3
