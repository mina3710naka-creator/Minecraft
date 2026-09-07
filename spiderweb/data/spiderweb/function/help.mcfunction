# ============================================================
#  操作方法の表示（実行者＝プレイヤー）
# ============================================================
tellraw @s [{"text":"===== ウェブシューター 操作方法 =====","color":"aqua","bold":true}]
tellraw @s [{"text":"右クリック: ","color":"yellow"},{"text":"クモの糸を発射（使用中にもう一度で解除）","color":"white"}]
tellraw @s [{"text":"左クリック（MOB攻撃時）: ","color":"yellow"},{"text":"通常の攻撃に加えて巣除去弾を発射。命中したクモの巣を塊ごと除去する","color":"white"}]
tellraw @s [{"text":"Shift: ","color":"yellow"},{"text":"モード切り替え（未使用時のみ）","color":"white"}]
tellraw @s [{"text":"  モード1 ","color":"gray"},{"text":"フックショット","color":"green"},{"text":" - 命中地点へ引き寄せられる。MOBに命中したらMOBを自分側へ引き寄せる","color":"white"}]
tellraw @s [{"text":"  モード2 ","color":"gray"},{"text":"巣づくり","color":"green"},{"text":" - 命中地点（MOBかブロック）に3x3x3のクモの巣を設置","color":"white"}]
tellraw @s [{"text":"--- モード1でブロックに着弾して張り付いている間 ---","color":"aqua"}]
tellraw @s [{"text":"R: ","color":"yellow"},{"text":"糸を巻き取る（アンカーへ登る）","color":"white"}]
tellraw @s [{"text":"Shift+R: ","color":"yellow"},{"text":"糸を伸ばす（アンカーから降りる）","color":"white"}]
tellraw @s [{"text":"Space: ","color":"yellow"},{"text":"張り付きを解除して5マスジャンプ","color":"white"}]
tellraw @s [{"text":"※ Rキーはコントロール設定の「利き手アイテムの交換」をRキーに割り当ててください","color":"dark_gray"}]
tellraw @s [{"text":"/function spiderweb:give","color":"yellow"},{"text":" でアイテム入手","color":"gray"}]
