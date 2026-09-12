package com.arcanemagic.spell;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * 1つの魔法を表すインターフェース。杖/魔導書の両方から共通で呼び出される。
 */
public interface Spell {

	Identifier id();

	Component displayName();

	/** レベル1の時のクールダウン(tick)。20tick = 1秒。 */
	int baseCooldownTicks();

	/** サーバー側で実際に効果を発動する。spellLevel は 1〜WandData.MAX_LEVEL。 */
	void cast(ServerLevel serverLevel, Player caster, int spellLevel);

	/** レベルが上がるほどクールダウンが短くなる(最大で基礎値の1/3まで)。 */
	default int cooldownTicks(int spellLevel) {
		int reduction = (baseCooldownTicks() / 8) * (spellLevel - 1);
		return Math.max(baseCooldownTicks() / 3, baseCooldownTicks() - reduction);
	}
}
