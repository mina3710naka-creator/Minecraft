# ============================================================
#  クリック検知（実行者＝インタラクションエンティティ）
#  attack（左クリック）/ interaction（右クリック）のtickが前回から
#  変化していたら、所有者（一番近いプレイヤー）としてアビリティを発動する
# ============================================================
execute store result score @s sc.natk run data get entity @s attack.tick 1
execute store result score @s sc.ninter run data get entity @s interaction.tick 1

execute unless score @s sc.natk = @s sc.atk as @p[distance=..2,sort=nearest] at @s rotated as @s run function sc:click/dispatch_attack
execute unless score @s sc.ninter = @s sc.inter as @p[distance=..2,sort=nearest] at @s rotated as @s run function sc:click/dispatch_use

scoreboard players operation @s sc.atk = @s sc.natk
scoreboard players operation @s sc.inter = @s sc.ninter
