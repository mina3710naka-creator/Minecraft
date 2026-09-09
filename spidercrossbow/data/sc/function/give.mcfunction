# ============================================================
#  スパイダークロスボウ一式を配布（実行者＝プレイヤー）
#  クイックチャージV・無限エンチャント付きなので、矢を1本持たせておけば
#  自動的にずっと装填され続ける（消費されない）
# ============================================================
loot give @s loot sc:give/crossbow
give @s minecraft:arrow 1
give @s minecraft:string 64
give @s minecraft:snowball[minecraft:custom_data={sc_ball:1b},minecraft:custom_name={text:"クモの巣クリアボール",color:"white",italic:false},minecraft:lore=[{text:"投げると、命中したクモの巣を",color:"gray",italic:false},{text:"繋がっている塊ごと除去する",color:"gray",italic:false}]] 16
