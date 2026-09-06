# ============================================================
#  引き寄せ開始（マクロ / 実行者＝プレイヤー / 実行位置＝プレイヤー）
#  見えない台車（コウモリ）を出し、プレイヤーをその上に ride で乗せる。
#  プレイヤー自身を直接 tp する（＝視点がカクつく）のではなく、
#  台車の方を tp で動かし、プレイヤーはそれに乗って追従するだけにする
#  ことで、ゲーム側の乗り物追従処理によるスムーズな移動になる。
#  ※ 台車には防具立てではなくコウモリを使っている（防具立ては外力を
#    受け付けない特殊仕様があるため）。
# ============================================================
tag @s remove hs.hooking
tag @s add hs.pulling

execute summon minecraft:bat run function hookshot:pull/carrier_init with storage hookshot:v

playsound minecraft:item.lead.tied player @a ~ ~ ~ 0.8 1.3
