# マクロ: dx, dzだけ離れた上空12ブロックから本物のつらら（鍾乳石）を落とす
$execute positioned ~$(dx) ~12 ~$(dz) summon minecraft:falling_block ~ ~ ~ {BlockState:{Name:"minecraft:pointed_dripstone",Properties:{thickness:"tip",vertical_direction:"down",waterlogged:"false"}},Time:1,DropItem:0b,Tags:["uw.ent"]}
