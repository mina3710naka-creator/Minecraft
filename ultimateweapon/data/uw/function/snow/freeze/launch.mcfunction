# 雪玉（凍結）の発射（実行者＝投げたプレイヤー）
playsound minecraft:entity.snowball.throw player @a ~ ~ ~ 1 1.2
execute anchored eyes positioned ^ ^ ^0.5 summon minecraft:marker run function uw:snow/freeze/proj_init
