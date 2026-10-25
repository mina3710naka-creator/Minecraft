# ============================================================
#  テキストブロック - 建築（拡大サイズ指定）
#  使用例: /function textblock:build_scaled {text:"HI",block:"minecraft:stone",scale:2}
#  scale はピクセル1マスあたりのブロック数（1以上の整数）
# ============================================================

$function textblock:run {text:"$(text)",block:"$(block)",scale:$(scale)}
