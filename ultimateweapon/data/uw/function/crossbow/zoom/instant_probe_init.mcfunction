# 即時ロックオン用こうもりの初期化（実行者＝生成されたこうもり）
# ※ tp @s ~ ~ ~ ~ ~ で summon 時の実行コンテキスト（プレイヤーの視点）の
#   回転を明示的に引き継がせる。これが無いとこうもりはバニラの
#   デフォルト回転のまま出現し、以後の ^ ^ ^1 の前進が全く見当違いの
#   方向へ飛んでいってしまう（レイキャストがプレイヤーの視線と無関係な
#   方向に飛ぶ不具合の直接の原因だった）
tp @s ~ ~ ~ ~ ~
data merge entity @s {NoAI:1b,Invisible:1b,Invulnerable:1b,Silent:1b,NoGravity:1b,Health:20.0f}
effect give @s minecraft:invisibility 1000000 0 true
tag @s add uw.ent
scoreboard players set @s uw.sub 100
function uw:crossbow/zoom/instant_probe_step
