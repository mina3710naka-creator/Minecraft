# ============================================================
#  モード切替保留中の毎tick処理（実行者＝プレイヤー）
#  Shiftを離したら即座に、離さなくても4tick(0.2秒)経ったら確定させる
#  （Shift+左クリックがあった場合は click/dispatch_attack.mcfunction が
#    先に sc.modepend タグを外すので、ここには来ない＝切り替わらない）
# ============================================================
scoreboard players add @s sc.modependt 1

execute unless predicate sc:sneaking run function sc:mode/pend_commit
execute if score @s sc.modependt matches 4.. run function sc:mode/pend_commit
