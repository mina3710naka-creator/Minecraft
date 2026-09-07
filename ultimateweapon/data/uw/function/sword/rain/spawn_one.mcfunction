# ランダムな1点を決めて剣（トライデント）を上空に生成する（実行者＝プレイヤー）
execute store result storage uw:v dx double 1 run random value -14..14
execute store result storage uw:v dz double 1 run random value -14..14
function uw:sword/rain/spawn_two with storage uw:v
