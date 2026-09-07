# ============================================================
#  Shiftキーでのモード切り替え（実行者＝プレイヤー）
#  ※ 使用中（発射・引き寄せ・張り付き・MOB引き寄せ）は切り替えない。
#    Shift+R（張り付き中の「伸ばす」）と衝突しないよう、
#    未使用時にShiftを「押した瞬間」だけ反応させる。
# ============================================================

# 途中参加したプレイヤー用に、未設定ならスコアを0で初期化しておく
# （addは既存の値があればそのまま。未設定時のみ0が作られる）
scoreboard players add @s sw.mode 0
scoreboard players add @s sw.sneakprev 0
scoreboard players add @s sw.len 0

execute unless entity @s[tag=sw.active] if predicate spiderweb:sneaking if score @s sw.sneakprev matches 0 if items entity @s weapon.mainhand *[minecraft:custom_data~{spiderweb:{tool:"shooter"}}] run function spiderweb:mode_toggle_apply

execute if predicate spiderweb:sneaking run scoreboard players set @s sw.sneakprev 1
execute unless predicate spiderweb:sneaking run scoreboard players set @s sw.sneakprev 0
