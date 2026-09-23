# ============================================================
#  通常攻撃でMOBに命中したときの処理（実行者＝プレイヤー）
#  tamewand:on_hit から、スニークしていない場合に呼ばれる
# ============================================================

# プレイヤーにまだテイム用のIDが割り当てられていなければ発行
# （すでに割り当て済みの場合は #next を無駄に1つ進めるだけで実害はない。
#  古いIDを使い回そうとして重複させる方が危険なので、あえて単純に
#  「増え続けるだけ」の採番にしている）
execute unless score @s tw.pid = @s tw.pid run scoreboard players operation @s tw.pid = #next tw.pid
scoreboard players add #next tw.pid 1

# 有効な相手（未テイムで、かつ対象外の実体ではない）が近くにいなければ何もしない
execute unless entity @e[tag=!tw.tamed,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:trident,type=!minecraft:item_frame,type=!minecraft:glow_item_frame,type=!minecraft:painting,type=!minecraft:armor_stand,type=!minecraft:marker,type=!minecraft:leash_knot,type=!minecraft:boat,type=!minecraft:minecart,type=!minecraft:end_crystal,type=!minecraft:falling_block,type=!minecraft:ender_dragon,type=!minecraft:wither,distance=..4,limit=1,sort=nearest] run return 0

# 以降はマクロ（上限チェック・実際のテイム）が必要なので、IDを
# ストレージに積んで tame/dispatch2 に渡す
execute store result storage tamewand:v pid int 1 run scoreboard players get @s tw.pid
function tamewand:tame/dispatch2 with storage tamewand:v
