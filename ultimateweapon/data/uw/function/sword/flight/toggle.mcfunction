# 飛行モードの切替（実行者＝プレイヤー）
execute if entity @s[tag=uw.flying] run function uw:sword/flight/off
execute unless entity @s[tag=uw.flying] run function uw:sword/flight/on
