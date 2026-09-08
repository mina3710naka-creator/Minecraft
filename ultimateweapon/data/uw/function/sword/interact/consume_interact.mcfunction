# 右クリックを1回分消費してアビリティを発動（実行者＝インタラクション）
data remove entity @s interaction
execute as @p[distance=..2,sort=nearest] at @s rotated as @s run function uw:sword/dispatch_interact
