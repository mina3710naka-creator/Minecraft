# ============================================================
#  アンインストール
#  テイム中の仲間は全員もとの野生の状態（通常AI）に戻してから、
#  このパック用のスコアボード等を片付ける
# ============================================================
execute as @e[tag=tw.tamed] at @s run function tamewand:tame/release

tag @e remove tw.target
tag @e remove tw.ent
tag @e remove tw.counted

scoreboard objectives remove tw.pid
scoreboard objectives remove tw.owner
scoreboard objectives remove tw.mode
scoreboard objectives remove tw.cd
scoreboard objectives remove tw.aim
scoreboard objectives remove tw.tid
scoreboard objectives remove tw.ttl
scoreboard objectives remove tw.life
scoreboard objectives remove tw.tmp

tellraw @a [{"text":"[テイムの杖] ","color":"light_purple"},{"text":"アンインストール完了。データパックのフォルダを削除してください。","color":"gray"}]
