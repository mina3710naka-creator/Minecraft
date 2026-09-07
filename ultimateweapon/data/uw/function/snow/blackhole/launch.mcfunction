# 雪玉（ブラックホール）の発射（実行者＝投げたプレイヤー）
playsound minecraft:entity.snowball.throw player @a ~ ~ ~ 1 0.8
execute anchored eyes positioned ^ ^ ^0.5 summon minecraft:marker run function uw:snow/blackhole/proj_init
