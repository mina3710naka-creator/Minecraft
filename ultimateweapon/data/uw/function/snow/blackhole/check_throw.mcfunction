# 統計で雪玉の使用を検知済み（実行者＝プレイヤー）。実物の雪玉があれば消し、発動する
kill @e[type=minecraft:snowball,distance=..3,limit=1,sort=nearest]
function uw:snow/blackhole/launch
