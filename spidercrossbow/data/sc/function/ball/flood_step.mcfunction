# ============================================================
#  クモの巣のフラッドフィル除去（実行位置＝クモの巣であることが
#  確認済みのブロック）。設置済みのブロックは air になるため、
#  訪問済みかどうかは「今もクモの巣か」で自然に判定できる。
#  #ball sc.ballc が上限（240個）に達したら打ち切る安全装置つき
# ============================================================
setblock ~ ~ ~ air destroy
scoreboard players add #ball sc.ballc 1

execute if score #ball sc.ballc matches ..240 positioned ~1 ~ ~ if block ~ ~ ~ minecraft:cobweb run function sc:ball/flood_step
execute if score #ball sc.ballc matches ..240 positioned ~-1 ~ ~ if block ~ ~ ~ minecraft:cobweb run function sc:ball/flood_step
execute if score #ball sc.ballc matches ..240 positioned ~ ~1 ~ if block ~ ~ ~ minecraft:cobweb run function sc:ball/flood_step
execute if score #ball sc.ballc matches ..240 positioned ~ ~-1 ~ if block ~ ~ ~ minecraft:cobweb run function sc:ball/flood_step
execute if score #ball sc.ballc matches ..240 positioned ~ ~ ~1 if block ~ ~ ~ minecraft:cobweb run function sc:ball/flood_step
execute if score #ball sc.ballc matches ..240 positioned ~ ~ ~-1 if block ~ ~ ~ minecraft:cobweb run function sc:ball/flood_step
