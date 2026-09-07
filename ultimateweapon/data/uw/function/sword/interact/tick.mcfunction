# ============================================================
#  クリック検知（実行者＝インタラクションエンティティ）
#  attack（左クリック）/ interaction（右クリック）のtickが前回から
#  変化していたら、所有者（一番近いプレイヤー）としてアビリティを発動する
# ============================================================
execute store result score @s uw.natk run data get entity @s attack.tick 1
execute store result score @s uw.ninter run data get entity @s interaction.tick 1

execute unless score @s uw.natk = @s uw.atk as @p[distance=..2,sort=nearest] at @s rotated as @s run function uw:sword/dispatch_attack
execute unless score @s uw.ninter = @s uw.inter as @p[distance=..2,sort=nearest] at @s rotated as @s run function uw:sword/dispatch_interact

scoreboard players operation @s uw.atk = @s uw.natk
scoreboard players operation @s uw.inter = @s uw.ninter
