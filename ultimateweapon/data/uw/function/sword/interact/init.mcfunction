# ============================================================
#  クリック検知エンティティの初期化（実行者＝生成されたインタラクション）
#  ※ uw.ent は付けない（20秒の自動掃除対象から外し、剣を持っている
#    限り消えないようにするため。片付けは remove.mcfunction が行う）
# ============================================================
tag @s add uw.sword_interact
data merge entity @s {width:1.2f,height:1.2f,response:1b}
data remove entity @s attack
data remove entity @s interaction
