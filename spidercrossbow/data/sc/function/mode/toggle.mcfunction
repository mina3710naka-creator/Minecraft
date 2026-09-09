# ============================================================
#  Shiftキーでのモード切替 / フック中はShiftでいつでも解除
#  （実行者＝プレイヤー）
#  立ち上がりエッジ（押した瞬間）で反応するが、即座には切り替えず
#  「保留」状態にする。Shift+左クリック（特別な球）と同じキーの
#  組み合わせなので、保留中にクリックがあればモード切替は取り消され、
#  クリックが無いまま少し経つ（またはShiftを離す）とモードが切り替わる
#  （mode/pend_tick.mcfunction / click/dispatch_attack.mcfunction）
# ============================================================
scoreboard players add @s sc.sneakp 0

execute if entity @s[tag=sc.hooked] if predicate sc:sneaking run return run function sc:hook/release

execute unless score @s sc.sneakp matches 1.. if predicate sc:sneaking run function sc:mode/pend_start
execute unless predicate sc:sneaking run scoreboard players set @s sc.sneakp 0
