# ============================================================
#  クリック検知（実行者＝インタラクションエンティティ）
#  attack（左クリック）のtickが前回から変化していたら、所有者
#  （一番近いプレイヤー）として特別な球を発射する
#  （右クリックはここでは扱わない。本物のクロスボウの構えを
#  横取りしないよう、このエンティティはShiftを押している間だけ存在する）
# ============================================================
execute store result score @s sc.natk run data get entity @s attack.tick 1

execute unless score @s sc.natk = @s sc.atk as @p[distance=..2,sort=nearest] at @s rotated as @s run function sc:click/dispatch_attack

scoreboard players operation @s sc.atk = @s sc.natk
