package com.magicmod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * 回避ステップ: スタミナを使って素早く移動し、短い間だけ無敵になる。
 * 無敵の間に攻撃が来ていたら「ジャスト回避」: スタミナを返し、MPが回復し、数秒間 威力 +25%。
 */
public final class Dodge {

	public static final int COST = 20;
	public static final int IFRAMES = 10;
	public static final int COOLDOWN = 16;
	public static final int JUST_WINDOW = 60;

	private Dodge() {
	}

	/** dirBits: 1=前 2=後 4=左 8=右(無ければ後ろへ)。 */
	public static void use(ServerPlayer p, int dirBits) {
		PlayerMagic pm = PlayerMagic.of(p);
		long now = p.level().getGameTime();
		if (now < pm.dodgeReadyAt || p.hasEffect(ModEffects.STUN) || p.hasEffect(ModEffects.PARALYSIS) || p.hasEffect(ModEffects.BIND)
				|| p.hasEffect(ModEffects.KNOCKDOWN) || p.isSpectator()) {
			return;
		}
		if (!p.isCreative() && pm.stamina < COST) {
			Casting.msg(p, "スタミナが足りません", ChatFormatting.YELLOW);
			return;
		}
		if (!p.isCreative()) {
			pm.stamina -= COST;
		}
		pm.dodgeReadyAt = now + COOLDOWN;
		pm.invulnUntil = now + IFRAMES;
		pm.dodgeRewarded = false;
		pm.castingSpell = "";
		Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		if (look.lengthSqr() < 1.0E-4) {
			look = new Vec3(0, 0, 1);
		}
		look = look.normalize();
		Vec3 right = new Vec3(-look.z, 0, look.x);
		Vec3 d = Vec3.ZERO;
		if ((dirBits & 1) != 0) {
			d = d.add(look);
		}
		if ((dirBits & 2) != 0) {
			d = d.subtract(look);
		}
		if ((dirBits & 4) != 0) {
			d = d.subtract(right);
		}
		if ((dirBits & 8) != 0) {
			d = d.add(right);
		}
		if (d.lengthSqr() < 1.0E-4) {
			d = look.scale(-1);
		}
		d = d.normalize();
		Vec3 from = p.position();
		p.setDeltaMovement(d.x * 1.15, p.onGround() ? 0.12 : 0.0, d.z * 1.15);
		p.syncVelocity = true;
		p.fallDistance = 0;
		ServerLevel l = (ServerLevel) p.level();
		// 残像
		for (int i = 0; i < 5; i++) {
			Vec3 at = from.add(d.scale(-0.3 * i));
			Vfx.near(l, Vfx.fade(0xE8F0FF, 0x6080C0, 1.4f - i * 0.2f), at.x, at.y + 1, at.z, 4, 0.25, 0.5, 0.25, 0);
		}
		Vfx.near(l, ParticleTypes.CLOUD, from.x, from.y + 0.1, from.z, 6, 0.3, 0.05, 0.3, 0.03);
		l.playSound(null, p.blockPosition(), SoundEvents.BREEZE_SLIDE, p.getSoundSource(), 0.8f, 1.4f);
		pm.dirty = true;
	}

	/** 無敵の間に攻撃を受けそうになった。受けなかったことにして true を返す。 */
	public static boolean tryAvoid(ServerPlayer p, DamageSource src) {
		PlayerMagic pm = PlayerMagic.of(p);
		long now = p.level().getGameTime();
		if (now >= pm.invulnUntil || src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (!pm.dodgeRewarded && (src.getEntity() != null || src.getDirectEntity() != null)) {
			pm.dodgeRewarded = true;
			pm.justDodgeUntil = now + JUST_WINDOW;
			pm.stamina = Math.min(Calc.maxStamina(pm), pm.stamina + COST + 10);
			Combat.addMp(p, 15);
			pm.dodgeReadyAt = now;
			ServerLevel l = (ServerLevel) p.level();
			Vec3 c = p.position();
			FxDisplay.ring(l, c, Element.LIGHT, 3, 6);
			Vfx.burstOut(l, c.add(0, 1, 0), Vfx.fade(0xFFFFFF, 0x80C0FF, 1.5f), 24, 0.3);
			Vfx.near(l, ModParticles.HOLY_STAR, c.x, c.y + 1, c.z, 8, 0.5, 0.6, 0.5, 0.02);
			l.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, p.getSoundSource(), 1.2f, 1.6f);
			Casting.msg(p, "ジャスト回避!  威力 +25%(3秒)", ChatFormatting.AQUA);
			pm.dirty = true;
		}
		return true;
	}
}
