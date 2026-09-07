# ============================================================
#  テキストブロック - フォントデータ初期化
#  3x5 ピクセルフォント（英数字・記号）を storage に展開する
#  px の座標は文字内でのオフセット（x:0-2 列 / y:0-4 行、0が上端）
# ============================================================

data modify storage textblock:font glyphs set value []

# ---- 数字 ----
data modify storage textblock:font glyphs append value {c:"0",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"1",px:[{x:1,y:0},{x:0,y:1},{x:1,y:1},{x:1,y:2},{x:1,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"2",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:0,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"3",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:2,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"4",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:2,y:3},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"5",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:2,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"6",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"7",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:2,y:1},{x:2,y:2},{x:2,y:3},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"8",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"9",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:2,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}

# ---- アルファベット（大文字） ----
data modify storage textblock:font glyphs append value {c:"A",px:[{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"B",px:[{x:0,y:0},{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"C",px:[{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:0,y:3},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"D",px:[{x:0,y:0},{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"E",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"F",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:0,y:4}]}
data modify storage textblock:font glyphs append value {c:"G",px:[{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"H",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"I",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:1,y:1},{x:1,y:2},{x:1,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"J",px:[{x:2,y:0},{x:2,y:1},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"K",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"L",px:[{x:0,y:0},{x:0,y:1},{x:0,y:2},{x:0,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"M",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:1,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"N",px:[{x:0,y:0},{x:0,y:1},{x:1,y:1},{x:0,y:2},{x:2,y:2},{x:1,y:3},{x:2,y:3},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"O",px:[{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"P",px:[{x:0,y:0},{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:0,y:4}]}
data modify storage textblock:font glyphs append value {c:"Q",px:[{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:1,y:3},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"R",px:[{x:0,y:0},{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:2,y:3},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"S",px:[{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:1,y:2},{x:2,y:3},{x:0,y:4},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"T",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:1,y:1},{x:1,y:2},{x:1,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"U",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"V",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:1,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"W",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:1,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"X",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:1,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"Y",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:1,y:2},{x:1,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"Z",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:2,y:1},{x:1,y:2},{x:0,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}

# ---- アルファベット（小文字。大文字と同じ形で表示） ----
data modify storage textblock:font glyphs append value {c:"a",px:[{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"b",px:[{x:0,y:0},{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"c",px:[{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:0,y:3},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"d",px:[{x:0,y:0},{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"e",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"f",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:0,y:4}]}
data modify storage textblock:font glyphs append value {c:"g",px:[{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"h",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"i",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:1,y:1},{x:1,y:2},{x:1,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"j",px:[{x:2,y:0},{x:2,y:1},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"k",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"l",px:[{x:0,y:0},{x:0,y:1},{x:0,y:2},{x:0,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"m",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:1,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"n",px:[{x:0,y:0},{x:0,y:1},{x:1,y:1},{x:0,y:2},{x:2,y:2},{x:1,y:3},{x:2,y:3},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"o",px:[{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"p",px:[{x:0,y:0},{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:0,y:4}]}
data modify storage textblock:font glyphs append value {c:"q",px:[{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:1,y:3},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"r",px:[{x:0,y:0},{x:1,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:1,y:2},{x:0,y:3},{x:2,y:3},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"s",px:[{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:1,y:2},{x:2,y:3},{x:0,y:4},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"t",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:1,y:1},{x:1,y:2},{x:1,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"u",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:2,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"v",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:1,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"w",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:0,y:2},{x:2,y:2},{x:0,y:3},{x:1,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"x",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:1,y:2},{x:0,y:3},{x:2,y:3},{x:0,y:4},{x:2,y:4}]}
data modify storage textblock:font glyphs append value {c:"y",px:[{x:0,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:1,y:2},{x:1,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"z",px:[{x:0,y:0},{x:1,y:0},{x:2,y:0},{x:2,y:1},{x:1,y:2},{x:0,y:3},{x:0,y:4},{x:1,y:4},{x:2,y:4}]}

# ---- 記号・空白 ----
data modify storage textblock:font glyphs append value {c:" ",px:[]}
data modify storage textblock:font glyphs append value {c:"!",px:[{x:1,y:0},{x:1,y:1},{x:1,y:2},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:"?",px:[{x:1,y:0},{x:2,y:0},{x:0,y:1},{x:2,y:1},{x:2,y:2},{x:1,y:3},{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:".",px:[{x:1,y:4}]}
data modify storage textblock:font glyphs append value {c:",",px:[{x:1,y:3},{x:0,y:4}]}
data modify storage textblock:font glyphs append value {c:"-",px:[{x:0,y:2},{x:1,y:2},{x:2,y:2}]}
data modify storage textblock:font glyphs append value {c:":",px:[{x:1,y:1},{x:1,y:3}]}
data modify storage textblock:font glyphs append value {c:"'",px:[{x:1,y:0},{x:1,y:1}]}
