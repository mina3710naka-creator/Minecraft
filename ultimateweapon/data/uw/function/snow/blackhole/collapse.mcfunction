# ブラックホールの崩壊（実行者＝中心マーカー / 実行位置＝中心）
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 1.5 0.6
particle minecraft:explosion_emitter ~ ~ ~ 0 0 0 0 1 normal @a
particle minecraft:flash ~ ~ ~ 0 0 0 0 1 normal @a

damage @e[tag=!uw.ent,distance=..3] 10 minecraft:generic

function uw:util/quiet_kill
