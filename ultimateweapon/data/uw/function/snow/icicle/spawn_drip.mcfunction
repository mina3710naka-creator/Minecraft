# つらら1本の落下位置を決める（実行者＝中心マーカー）
execute store result storage uw:v dx double 1 run random value -10..10
execute store result storage uw:v dz double 1 run random value -10..10
function uw:snow/icicle/spawn_drip2 with storage uw:v
