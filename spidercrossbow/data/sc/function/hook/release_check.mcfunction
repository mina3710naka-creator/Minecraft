# ============================================================
#  フック中の解除条件チェック（実行者＝フック中のプレイヤー）
#  ・ジャンプ（統計 minecraft.custom:minecraft.jump が増加＝接地中のみ判定可能）
#  ・30秒（600tick）経過の安全装置
#  ※ Shiftタップでの解除は mode/edge.mcfunction 側で処理する
#    （空中にいる間もジャンプキーは判定できないバニラの仕様上の制約のため、
#    こちらが実質的な主な解除操作になる）
# ============================================================
scoreboard players add @s sc.hookt 1
execute if score @s sc.hookt matches 600.. run function sc:hook/release
execute if score @s sc.jump matches 1.. run function sc:hook/release
