# ============================================================
#  OPブーツを配布
#  ※26.2 / 1.21.5 以降のコンポーネント表記（テキストは NBT 形式）
#  ・耐性V + 全ダメージ無効（tick 処理）
#  ・移動速度 +60% / ジャンプ力アップ（attribute_modifiers、装備中のみ常時有効）
#  ・不可壊
# ============================================================
give @s minecraft:netherite_boots[minecraft:custom_data={ob_boots:1b},minecraft:custom_name={text:"OPブーツ",color:"gold",bold:true,italic:false},minecraft:lore=[{text:"耐性V・全ダメージ無効",color:"aqua",italic:false},{text:"移動速度アップ",color:"aqua",italic:false},{text:"通常ジャンプで3マス上昇",color:"aqua",italic:false},{text:"しゃがみ長押し(3秒)で爆発10マスジャンプ",color:"yellow",italic:false},{text:"不可壊",color:"gray",italic:false}],minecraft:enchantment_glint_override=true,minecraft:unbreakable={},minecraft:attribute_modifiers=[{type:"minecraft:movement_speed",id:"opboots:speed_boost",amount:0.6,operation:"add_multiplied_total",slot:"feet"},{type:"minecraft:jump_strength",id:"opboots:jump_boost",amount:0.23,operation:"add_value",slot:"feet"}]] 1
