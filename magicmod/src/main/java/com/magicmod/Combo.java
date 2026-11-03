package com.magicmod;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * コンボ: 魔法・物理わざ・近接・元素反応を当て続けると数が増え、威力が上がる。物理と魔法を交互につなぐと +1 ボーナス。
 * 被弾すると途切れる(「避けてつなぐ」ための仕組み)。
 */
public final class Combo {

	public static final int WINDOW = 80;
	private static final int[] TIERS = { 10, 25, 50, 100 };
	private static final float[] BONUS = { 0.05f, 0.10f, 0.15f, 0.25f };

	private Combo() {
	}

	/** 当てた。physical は物理かどうか、n は増える数。 */
	public static void hit(ServerPlayer p, boolean physical, int n) {
		PlayerMagic pm = PlayerMagic.of(p);
		long now = p.level().getGameTime();
		if (now > pm.comboUntil) {
			pm.combo = 0;
			pm.comboTier = 0;
		}
		boolean bridge = pm.combo > 0 && pm.lastHitPhysical != physical;
		pm.combo += n + (bridge ? 1 : 0);
		pm.lastHitPhysical = physical;
		pm.comboUntil = now + WINDOW;
		pm.dirty = true;
		int tier = tierOf(pm.combo);
		if (tier > pm.comboTier) {
			pm.comboTier = tier;
			tierUp(p, tier);
		}
	}

	public static int tierOf(int combo) {
		int t = 0;
		for (int i = 0; i < TIERS.length; i++) {
			if (combo >= TIERS[i]) {
				t = i + 1;
			}
		}
		return t;
	}

	public static void reset(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (pm.combo > 0) {
			pm.combo = 0;
			pm.comboTier = 0;
			pm.dirty = true;
		}
	}

	/** 威力にかかる倍率(コンボ + ジャスト回避)。 */
	public static float mult(PlayerMagic pm, long now) {
		float m = 1f;
		if (pm.combo > 0 && now <= pm.comboUntil && pm.comboTier > 0) {
			m += BONUS[pm.comboTier - 1];
		}
		if (now < pm.justDodgeUntil) {
			m += 0.25f;
		}
		return m;
	}

	private static void tierUp(ServerPlayer p, int tier) {
		ServerLevel l = (ServerLevel) p.level();
		Vec3 c = p.position();
		int[] col = { 0xFFE080, 0xFFA040, 0xFF6030, 0xFF40A0 };
		FxDisplay.ring(l, c, null, 2.5 + tier, 8);
		Vfx.burstOut(l, c.add(0, 1, 0), Vfx.fade(0xFFFFFF, col[tier - 1], 1.6f), 20 + tier * 8, 0.35);
		Vfx.near(l, ModParticles.STEEL_FLASH, c.x, c.y + 1, c.z, 6, 0.6, 0.6, 0.6, 0.02);
		l.playSound(null, p.blockPosition(), ModSounds.COMBO_UP, p.getSoundSource(), 1f, 0.8f + tier * 0.12f);
		Casting.msg(p, "COMBO " + TIERS[tier - 1] + "!  威力 +" + Math.round(BONUS[tier - 1] * 100) + "%", ChatFormatting.GOLD);
	}
}
