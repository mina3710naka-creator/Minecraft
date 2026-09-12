package com.arcanemagic.component;

import java.util.Optional;

import net.minecraft.resources.Identifier;

/**
 * 杖(Wand)アイテムに付与される魔法1つと強化レベルを保持するデータ。
 * 実際の保存先は ItemStack の CustomData(NBT)コンポーネント(WandDataHelper参照)。
 */
public record WandData(Optional<Identifier> spellId, int level) {

	public static final int MAX_LEVEL = 5;

	public static final WandData EMPTY = new WandData(Optional.empty(), 1);

	public boolean hasSpell() {
		return spellId.isPresent();
	}

	public WandData withSpell(Identifier newSpellId) {
		return new WandData(Optional.of(newSpellId), this.level);
	}

	public WandData withLevel(int newLevel) {
		return new WandData(this.spellId, Math.min(MAX_LEVEL, Math.max(1, newLevel)));
	}

	public boolean isMaxLevel() {
		return level >= MAX_LEVEL;
	}
}
