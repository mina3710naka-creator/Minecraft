# ============================================================
#  クリック検知エンティティの初期化（実行者＝生成されたインタラクション）
#  ※ uw.ent は付けない（20秒の自動掃除対象から外し、剣を持っている
#    限り消えないようにするため。片付けは remove.mcfunction が行う）
#  ※ 以前あった response:1b は正しいフィールドか未確認だったため削除。
#    当たり判定を少し大きめにして、右クリックの取りこぼしを減らす
# ============================================================
tag @s add uw.sword_interact
data merge entity @s {width:1.6f,height:1.6f}
data remove entity @s attack
data remove entity @s interaction
