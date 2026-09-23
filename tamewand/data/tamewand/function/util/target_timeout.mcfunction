# ============================================================
#  攻撃命令の目印（tw.target）の後始末（実行者＝目印の付いた実体）
#  命令から30秒（600ティック）誰にも倒されず放置されたら目印を外す
# ============================================================
scoreboard players remove @s tw.ttl 1
execute if score @s tw.ttl matches ..0 run tag @s remove tw.target
