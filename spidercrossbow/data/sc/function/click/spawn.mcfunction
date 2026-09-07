# クリック検知エンティティを生成（実行者＝プレイヤー）
tag @s add sc.has_interact
execute anchored eyes positioned ~ ~-0.6 ~ summon minecraft:interaction run function sc:click/init
