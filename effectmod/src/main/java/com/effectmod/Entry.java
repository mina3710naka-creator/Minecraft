package com.effectmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * アイテムに付いた効果1つ分。文章(text)を Parser が解釈した結果を保存する。
 *
 * @param text    付与時に入力された文章(表示・確認用)
 * @param trigger 発動のきっかけ(Parser.Trigger の名前)
 * @param action  効果の種類(Parser.Action の名前)
 * @param arg     効果ごとの追加情報(ブロックID・ポーション効果ID など)
 * @param power   威力・レベル(1〜10)
 * @param seconds 持続秒数(0 = 既定)
 * @param chance  発動確率(1〜100)
 */
public record Entry(String text, String trigger, String action, String arg, int power, int seconds, int chance) {

	public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("text").forGetter(Entry::text),
			Codec.STRING.fieldOf("trigger").forGetter(Entry::trigger),
			Codec.STRING.fieldOf("action").forGetter(Entry::action),
			Codec.STRING.optionalFieldOf("arg", "").forGetter(Entry::arg),
			Codec.INT.optionalFieldOf("power", 1).forGetter(Entry::power),
			Codec.INT.optionalFieldOf("seconds", 0).forGetter(Entry::seconds),
			Codec.INT.optionalFieldOf("chance", 100).forGetter(Entry::chance)).apply(i, Entry::new));
}
