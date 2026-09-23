# ============================================================
#  テイムの杖 / Tame Wand  -  初期化
# ============================================================

scoreboard objectives add tw.pid dummy
scoreboard objectives add tw.owner dummy
scoreboard objectives add tw.mode dummy
scoreboard objectives add tw.cd dummy
scoreboard objectives add tw.aim dummy
scoreboard objectives add tw.tid dummy
scoreboard objectives add tw.ttl dummy
scoreboard objectives add tw.life dummy
scoreboard objectives add tw.tmp dummy

# ID 採番カウンタ（reload しても既存の関係が壊れないよう、必ず add 0 で
# 「無ければ 0 から」「あればそのまま」にする。set で毎回 0 に戻さない）
scoreboard players add #next tw.pid 0
scoreboard players add #next tw.tid 0

# ※ tw.tamed が付いた実際の MOB は本物の生き物なので、hookshot の内部
#  ヘルパー実体と違い、/reload では絶対にキル・初期化しない

tellraw @a [{"text":"[テイムの杖] ","color":"light_purple"},{"text":"読み込み完了 / ","color":"gray"},{"text":"/function tamewand:give","color":"yellow"},{"text":" でアイテム入手、","color":"gray"},{"text":"/function tamewand:help","color":"yellow"},{"text":" で使い方","color":"gray"}]
