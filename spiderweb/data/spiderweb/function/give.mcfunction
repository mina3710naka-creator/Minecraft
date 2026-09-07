# ============================================================
#  ウェブシューター（クロスボウ）とクモの糸（矢）を配布
#  ※26.2 / 1.21.5 以降のコンポーネント表記（テキストはNBT形式）
#  1.21.4以前ではspiderweb:give_oldを使ってください
# ============================================================
give @s minecraft:crossbow[minecraft:custom_data={spiderweb:{tool:"shooter"}},minecraft:custom_name={text:"ウェブシューター",color:"aqua",italic:false},minecraft:lore=[{text:"右クリックでクモの糸を発射",color:"gray",italic:false},{text:"Shift: モード切り替え（未使用時）",color:"gray",italic:false},{text:"モード1 フックショット / モード2 巣づくり",color:"dark_gray",italic:false},{text:"R:巻き取り Shift+R:伸ばす Space:解除+ジャンプ",color:"gray",italic:false}],minecraft:enchantments={levels:{"minecraft:quick_charge":3}},minecraft:unbreakable={}] 1

give @s minecraft:arrow[minecraft:custom_data={spiderweb:{tool:"thread"}},minecraft:custom_name={text:"クモの糸",color:"white",italic:false},minecraft:lore=[{text:"ウェブシューターの弾",color:"gray",italic:false}]] 64
