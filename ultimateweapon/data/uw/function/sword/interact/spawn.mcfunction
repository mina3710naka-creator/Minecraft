# クリック検知エンティティを生成（実行者＝プレイヤー）
tag @s add uw.has_interact
execute anchored eyes positioned ~ ~-0.6 ~ summon minecraft:interaction run function uw:sword/interact/init
