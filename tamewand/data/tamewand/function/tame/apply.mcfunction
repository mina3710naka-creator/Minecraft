# ============================================================
#  実際にテイムする（マクロ / 実行者＝テイムされるMOB / 実行位置＝そのMOB）
# ============================================================
$scoreboard players set @s tw.owner $(pid)
scoreboard players set @s tw.mode 0
scoreboard players set @s tw.cd 0
# 約3分（3600ティック）で自然に解ける。長さを変えたい場合はここを調整
scoreboard players set @s tw.life 3600
tag @s add tw.tamed
tag @s add tw.ent

# NoAI で元のAI（プレイヤーを襲う等）を完全に止め、以降はこのデータ
# パックが tick.mcfunction 経由で毎ティック動きを直接制御する。
# NoGravity は「浮遊する仲間」として障害物を気にせず追従・突撃させるため
data merge entity @s {NoAI:1b,NoGravity:1b,PersistenceRequired:1b}

# 攻撃で削れた分も含めて全回復（テイムの合図として）
execute store result entity @s Health float 1 run attribute @s minecraft:max_health get

particle minecraft:heart ~ ~1 ~ 0.3 0.4 0.3 0 12 force @a
particle minecraft:happy_villager ~ ~1 ~ 0.3 0.4 0.3 0 8 force @a
playsound minecraft:entity.player.levelup player @a ~ ~ ~ 1 1.4

$tellraw @a[scores={tw.pid=$(pid)},limit=1] [{"text":"[テイムの杖] ","color":"light_purple"},{"selector":"@s"},{"text":" を仲間にした！（約3分間）","color":"gray"}]
