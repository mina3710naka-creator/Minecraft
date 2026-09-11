package com.arcanemagic.spell;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 1つの魔法を表すインターフェース。杖/魔導書の両方から共通で呼び出される。
 */
public interface Spell {

	Identifier id();

	Text displayName();

	/** レベル1の時のクールダウン(tick)。20tick = 1秒。 */
	int baseCooldownTicks();

	/** サーバー側で実際に効果を発動する。level は 1〜WandData.MAX_LEVEL。 */
	void cast(ServerWorld world, PlayerEntity caster, int level);

	/** レベルが上がるほどクールダウンが短くなる(最大で基礎値の1/3まで)。 */
	default int cooldownTicks(int level) {
		int reduction = (baseCooldownTicks() / 8) * (level - 1);
		return Math.max(baseCooldownTicks() / 3, baseCooldownTicks() - reduction);
	}
}
