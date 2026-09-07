# ============================================================
#  ウェブシューター / Spiderweb  -  初期化
# ============================================================

# クロスボウを撃った回数（使用検知に利用）
scoreboard objectives add sw.use minecraft.used:minecraft.crossbow
# 「利き手アイテムの交換」キー（Rキーに割り当てて使う想定。巻き取り/伸ばし用）
scoreboard objectives add sw.rkey minecraft.custom:minecraft.swap_hand
# ジャンプキー（Space。糸に張り付いている間だけ解除トリガーとして使う）
scoreboard objectives add sw.jump minecraft.custom:minecraft.jump

# 内部用スコア
scoreboard objectives add sw.id dummy
scoreboard objectives add sw.t dummy
scoreboard objectives add sw.pt dummy
scoreboard objectives add sw.spd dummy
scoreboard objectives add sw.range dummy
scoreboard objectives add sw.sub dummy
# 到達時の打ち上げ（レビテーション）用ティックカウンタ
scoreboard objectives add sw.lt dummy
# 張り付いている間の糸の長さ（0.1ブロック単位）
scoreboard objectives add sw.len dummy
# モード（0:フックショット / 1:巣づくり）
scoreboard objectives add sw.mode dummy
# 直前ティックのスニーク状態（Shiftの押した瞬間＝立ち上がりエッジ検出用）
scoreboard objectives add sw.sneakprev dummy
# 左クリック検知用の当たり判定（Interaction）に紐付ける、プレイヤーごとの恒久ID
scoreboard objectives add sw.pid dummy
# クモの巣クラスタ除去（連鎖処理）の暴走防止カウンタ
scoreboard objectives add sw.wc dummy

# ID採番カウンタ
scoreboard players add #next sw.id 0
scoreboard players set #nextp sw.pid 0

# 前回のワールド終了時に残っていたものを掃除
kill @e[tag=sw.ent]
kill @e[tag=sw.sweep]
# 左クリック検知用の当たり判定（Interaction）も作り直す
kill @e[tag=sw.puncher]
# 引き寄せ中だったMOBに付いた一時タグも掃除（本物のMOBなのでkillはしない）
tag @e[tag=sw.yankmob] remove sw.tip
tag @e[tag=sw.yankmob] remove sw.yankmob

tag @a remove sw.active
tag @a remove sw.hooking
tag @a remove sw.webbing
tag @a remove sw.pulling
tag @a remove sw.attached
tag @a remove sw.yanking
tag @a remove sw.launching
tag @a remove sw.self
tag @a remove sw.caster

scoreboard players set @a sw.use 0
scoreboard players set @a sw.rkey 0
scoreboard players set @a sw.jump 0
# 未設定のプレイヤーにだけ初期値を入れる（add 0 は未設定なら0で作成される）
scoreboard players add @a sw.mode 0
scoreboard players add @a sw.sneakprev 0
scoreboard players add @a sw.len 0

scoreboard players reset @a sw.pid

tellraw @a [{"text":"[ウェブシューター] ","color":"aqua"},{"text":"読み込み完了 / ","color":"gray"},{"text":"/function spiderweb:give","color":"yellow"},{"text":" でアイテム入手","color":"gray"}]
tellraw @a [{"text":"[ウェブシューター] ","color":"aqua"},{"text":"操作方法は ","color":"gray"},{"text":"/function spiderweb:help","color":"yellow"},{"text":" で確認できます","color":"gray"}]
