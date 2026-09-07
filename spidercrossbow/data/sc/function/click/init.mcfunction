# ============================================================
#  クリック検知エンティティの初期化（実行者＝生成されたインタラクション）
#  ※ sc.ent は付けない（クロスボウを持っている限り消えないようにする
#    ため。片付けは remove.mcfunction が行う）
# ============================================================
tag @s add sc.interact
data merge entity @s {width:1.2f,height:1.2f,response:1b}
scoreboard players set @s sc.atk 0
scoreboard players set @s sc.inter 0
