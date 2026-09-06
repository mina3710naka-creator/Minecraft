# ============================================================
#  ジャンプ演出の共通処理（実行者＝プレイヤー）
#  地上ジャンプ・空中ジャンプ共通のタグ管理と見た目・音の演出だけを
#  行う。実際のノックバックは呼び出し元（jump/leap・jump/air）で
#  それぞれの強さ分の damage コマンドを実行する
# ============================================================

# 次回の溜めのためにシフト保持カウンターをリセット
scoreboard players set @s ob.sneak 0

tag @s add ob.leaping
scoreboard players set @s ob.leapt 0

# ウィンドチャージらしい見た目・音を演出として出す
particle minecraft:gust ~ ~ ~ 0.3 0.3 0.3 0.1 15 normal @a
playsound minecraft:entity.wind_charge.wind_burst player @a ~ ~ ~ 1 1
