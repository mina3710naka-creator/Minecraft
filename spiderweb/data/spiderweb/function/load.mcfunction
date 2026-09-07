# ============================================================
#  ウェブシューター / Spiderweb  -  初期化
# ============================================================

# キー入力の代用に使うバニラ統計
scoreboard objectives add sw.rkey minecraft.custom:minecraft.swap_hand
scoreboard objectives add sw.jump minecraft.custom:minecraft.jump

# 内部処理用スコア
scoreboard objectives add sw.id dummy
scoreboard objectives add sw.t dummy
scoreboard objectives add sw.pt dummy
scoreboard objectives add sw.spd dummy
scoreboard objectives add sw.range dummy
scoreboard objectives add sw.sub dummy
scoreboard objectives add sw.lt dummy
scoreboard objectives add sw.len dummy
scoreboard objectives add sw.mode dummy
scoreboard objectives add sw.sneakp dummy
scoreboard objectives add sw.wc dummy

scoreboard players add #next sw.id 0

# 前回のワールド終了時の残骸を掃除
kill @e[tag=sw.ent]
kill @e[tag=sw.sweep]
# 引き寄せ中だったMOB本体は実在の生物なのでkillはせず、タグだけ剥がす
tag @e[tag=sw.yankmob] remove sw.yankmob
tag @e[tag=sw.yankmob] remove sw.tip

tag @a remove sw.active
tag @a remove sw.hooking
tag @a remove sw.webbing
tag @a remove sw.pulling
tag @a remove sw.attached
tag @a remove sw.yanking
tag @a remove sw.launching
tag @a remove sw.caster

scoreboard players set @a sw.rkey 0
scoreboard players set @a sw.jump 0
# 途中参加者にも初期値0を入れる（addは未設定のときだけ0で作成される）
scoreboard players add @a sw.mode 0
scoreboard players add @a sw.sneakp 0
scoreboard players add @a sw.len 0

tellraw @a [{"text":"[ウェブシューター] ","color":"aqua"},{"text":"読み込み完了 / ","color":"gray"},{"text":"/function spiderweb:give","color":"yellow"},{"text":" でアイテム入手","color":"gray"}]
tellraw @a [{"text":"[ウェブシューター] ","color":"aqua"},{"text":"操作方法は ","color":"gray"},{"text":"/function spiderweb:help","color":"yellow"},{"text":" で確認できます","color":"gray"}]
