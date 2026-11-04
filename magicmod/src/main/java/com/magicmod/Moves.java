package com.magicmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 物理攻撃の専用モーション。武器カテゴリごとに「連続で当てるほど技が変わる」4 段の連携。
 * 1〜3 段目はそれぞれ別の振り(横薙ぎ・逆袈裟・切り上げ…)で、4 段目が必ずフィニッシャー。
 * 間が空くと 1 段目に戻る。連携が進むほど追加ダメージも大きくなる。
 */
public final class Moves {

	private static final class Chain {
		int step;
		long until;
	}

	private static final Map<UUID, Chain> CHAINS = new HashMap<>();

	private static final Map<String, String[]> NAMES = Map.of(
			"blade", new String[] { "横薙ぎ", "逆袈裟", "切り上げ", "回転斬り" },
			"polearm", new String[] { "突き", "二段突き", "薙ぎ払い", "貫通突進" },
			"dual", new String[] { "右の一閃", "左の一閃", "X字斬り", "乱舞" },
			"swing", new String[] { "回し打ち", "大回転", "引き寄せ", "遠心撃" },
			"heavy", new String[] { "叩きつけ", "薙ぎ倒し", "地割れ", "大震撃" });

	private Moves() {
	}

	public static void forget(UUID id) {
		CHAINS.remove(id);
	}

	public static int stepOf(ServerPlayer p) {
		Chain ch = CHAINS.get(p.getUUID());
		return ch == null ? 0 : ch.step;
	}

	public static boolean supports(String cat) {
		return NAMES.containsKey(cat);
	}

	/** 通常攻撃が当たったときに呼ぶ。taken は今の 1 発で与えたダメージ。 */
	public static void onHit(ServerPlayer p, LivingEntity t, float taken, String cat) {
		String[] names = NAMES.get(cat);
		if (names == null || !t.isAlive() && taken <= 0) {
			return;
		}
		long now = p.level().getGameTime();
		Chain ch = CHAINS.computeIfAbsent(p.getUUID(), k -> new Chain());
		if (now > ch.until || ch.step >= 4) {
			ch.step = 0;
		}
		ch.step++;
		ch.until = now + ("heavy".equals(cat) ? 40 : "swing".equals(cat) ? 30 : 24);
		int step = ch.step;
		Casting.msg(p, "⚔ " + names[step - 1] + " " + "①②③④".charAt(step - 1), step == 4 ? ChatFormatting.GOLD : ChatFormatting.WHITE);

		Ctx c = new Ctx(p, null, Math.max(1f, taken));
		c.physical = true;
		// 向きは「攻撃した相手の方」に合わせる(モーションが必ず相手に向かう)
		Vec3 to = t.position().subtract(p.position());
		if (to.lengthSqr() > 0.01) {
			c.dir = new Vec3(to.x, 0, to.z).normalize();
		}
		followUp(p, cat, step);
		Ctx hs = new Ctx(p, null, 0);
		hs.sound(ModSounds.weapon(step == 4 ? "hit_finisher" : "polearm".equals(cat) || "blade".equals(cat) || "dual".equals(cat) ? "hit_slash" : "hit_blunt"),
				step == 4 ? 1.1f : 0.8f, 0.9f + step * 0.06f);
		switch (cat) {
			case "blade" -> blade(c, t, step);
			case "polearm" -> polearm(c, t, step);
			case "dual" -> dual(c, t, step);
			case "swing" -> swing(c, t, step);
			case "heavy" -> heavy(c, t, step);
			default -> {
			}
		}
	}

	private static final Map<String, String> SWING_SOUND = Map.of("blade", "swing_blade", "polearm", "swing_spear", "dual", "swing_dual",
			"swing", "swing_whirl", "heavy", "swing_heavy");

	private static void swingAgain(ServerPlayer p, String cat, int delay, SwingAnimationType type, int duration) {
		Tasks.later(delay, () -> {
			if (p.isAlive()) {
				p.swing(InteractionHand.MAIN_HAND, new SwingAnimation(type, duration), true);
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.weapon(SWING_SOUND.getOrDefault(cat, "swing_blade")), p.getSoundSource(), 0.7f,
						0.95f + p.getRandom().nextFloat() * 0.15f);
			}
		});
	}

	/** 段ごとの「振り直し」: 最初の 1 発のあとに、型の違う振りをつなげて見せる(二段突き・X字・フィニッシュの大振りなど)。 */
	private static void followUp(ServerPlayer p, String cat, int step) {
		switch (cat) {
			case "blade" -> {
				if (step == 3) {
					swingAgain(p, cat, 3, SwingAnimationType.WHACK, 6);
				} else if (step == 4) {
					swingAgain(p, cat, 3, SwingAnimationType.WHACK, 6);
					swingAgain(p, cat, 7, SwingAnimationType.WHACK, 9);
				}
			}
			case "polearm" -> {
				if (step == 2) {
					swingAgain(p, cat, 3, SwingAnimationType.STAB, 6);
				} else if (step == 4) {
					swingAgain(p, cat, 2, SwingAnimationType.STAB, 12);
				}
			}
			case "dual" -> {
				if (step == 3) {
					swingAgain(p, cat, 2, SwingAnimationType.WHACK, 4);
				} else if (step == 4) {
					for (int i = 1; i <= 4; i++) {
						swingAgain(p, cat, i * 3, i % 2 == 0 ? SwingAnimationType.WHACK : SwingAnimationType.STAB, 4);
					}
				}
			}
			case "swing" -> {
				if (step == 4) {
					swingAgain(p, cat, 5, SwingAnimationType.WHACK, 12);
				}
			}
			case "heavy" -> {
				if (step == 4) {
					swingAgain(p, cat, 8, SwingAnimationType.WHACK, 16);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------------------------------------

	private static void slash(Ctx c, double range, double ang, double tilt) {
		Vfx.slash(c.caster, c.dir, range, ang, null, tilt);
	}

	private static void blade(Ctx c, LivingEntity t, int step) {
		switch (step) {
			case 1 -> {
				slash(c, 3.2, 60, 0.0);
				c.cone(3.2, 60, 0.3f);
				c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
			}
			case 2 -> {
				slash(c, 3.2, 60, -1.3);
				c.cone(3.2, 60, 0.4f);
				c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.5f);
			}
			case 3 -> {
				slash(c, 3.0, 45, 2.2);
				c.hit(t, 0.5f);
				t.setDeltaMovement(t.getDeltaMovement().add(0, 0.55, 0));
				t.syncVelocity = true;
				c.particles(ParticleTypes.CRIT, t.getX(), t.getY() + 0.5, t.getZ(), 14, 0.3, 0.8, 0.3, 0.3);
				c.sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.3f);
			}
			default -> {
				c.dash(0.6, 0.1);
				for (int i = 0; i < 4; i++) {
					int k = i;
					c.later(i * 2, () -> Vfx.slash(c.caster, Ctx.rotateY(c.dir, Math.PI / 2 * k), 3.6, 70, null, k % 2 == 0 ? 0.6 : -0.6));
				}
				c.areaSelf(3.8, 0.8f).forEach(e -> {
					Ctx.push(e, c.caster.position(), 0.7, 0.25);
					ModEffects.apply(e, ModEffects.STAGGER, 40, 0);
				});
				Vfx.shake(c.level, c.caster.position(), 14, 0.5f, 6);
				c.sound(SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.9f);
			}
		}
	}

	private static void polearm(Ctx c, LivingEntity t, int step) {
		Vec3 o = c.caster.position().add(0, c.caster.getBbHeight() * 0.6, 0);
		switch (step) {
			case 1 -> {
				Vfx.line(c.level, o, o.add(c.dir.scale(4.6)), ParticleTypes.CRIT, 0.35);
				c.beam(4.6, 0.9, 0.3f);
				c.sound(SoundEvents.TRIDENT_THROW.value(), 0.5f, 1.8f);
			}
			case 2 -> {
				for (int i = 0; i < 2; i++) {
					c.later(i * 3, () -> {
						Vfx.line(c.level, o, o.add(c.dir.scale(4.6)), ParticleTypes.CRIT, 0.3);
						c.beam(4.6, 0.9, 0.22f);
						c.sound(SoundEvents.TRIDENT_THROW.value(), 0.5f, 1.9f);
					});
				}
			}
			case 3 -> {
				slash(c, 4.6, 100, 0.0);
				c.cone(4.6, 100, 0.45f).forEach(e -> Ctx.push(e, c.caster.position(), 0.8, 0.2));
				c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.9f);
			}
			default -> {
				c.dash(1.5, 0.1);
				c.later(2, () -> {
					Vec3 o2 = c.caster.position().add(0, 1, 0);
					Vfx.line(c.level, o2, o2.add(c.dir.scale(7)), ParticleTypes.END_ROD, 0.3);
					Vfx.line(c.level, o2.add(0, 0.2, 0), o2.add(c.dir.scale(7)).add(0, 0.2, 0), ParticleTypes.CRIT, 0.3);
					c.beam(7, 1.4, 0.9f).forEach(e -> ModEffects.apply(e, ModEffects.STAGGER, 40, 0));
					c.sound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.8f, 1.4f);
					Vfx.shake(c.level, o2, 12, 0.4f, 5);
				});
			}
		}
	}

	private static void dual(Ctx c, LivingEntity t, int step) {
		switch (step) {
			case 1, 2 -> {
				slash(c, 2.7, 55, step == 1 ? 0.3 : -0.3);
				c.cone(2.7, 55, 0.2f);
				c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, step == 1 ? 1.7f : 1.9f);
			}
			case 3 -> {
				slash(c, 3.1, 55, 1.6);
				c.later(2, () -> slash(c, 3.1, 55, -1.6));
				c.cone(3.1, 60, 0.5f);
				c.sound(SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.5f);
			}
			default -> {
				// 乱舞: 相手の周りを駆け抜けて連続で斬る
				Vec3 center = t.position();
				Tasks.repeat(0, 2, 6, i -> {
					if (!t.isAlive()) {
						return;
					}
					double a = i * 1.1;
					Vec3 from = center.add(Math.cos(a) * 2, 1, Math.sin(a) * 2);
					Vfx.line(c.level, from, center.add(0, 1, 0), ParticleTypes.CRIT, 0.25);
					Vfx.slash(c.caster, center.subtract(c.caster.position()), 2.5, 50, null, i % 2 == 0 ? 1.2 : -1.2);
					c.hit(t, i == 5 ? 0.45f : 0.18f);
					c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.6f + i * 0.06f);
				});
				ModEffects.apply(t, ModEffects.STAGGER, 40, 0);
			}
		}
	}

	private static void swing(Ctx c, LivingEntity t, int step) {
		switch (step) {
			case 1 -> {
				c.ring(c.caster.position(), 3, ParticleTypes.SWEEP_ATTACK);
				c.areaSelf(3, 0.3f);
				c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
			}
			case 2 -> {
				c.ring(c.caster.position(), 4, ParticleTypes.CRIT);
				c.areaSelf(4, 0.45f).forEach(e -> Ctx.push(e, c.caster.position(), 0.6, 0.15));
				c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.7f);
			}
			case 3 -> {
				slash(c, 6, 40, 0.0);
				List<LivingEntity> l = c.cone(6, 40, 0.35f);
				l.forEach(e -> Ctx.pull(e, c.caster.position(), 1.1));
				c.sound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
			}
			default -> {
				Vec3 p = c.caster.position();
				Tasks.repeat(0, 1, 8, i -> c.ring(p, 1.5 + i * 0.5, ParticleTypes.CRIT));
				c.areaSelf(5.5, 0.85f).forEach(e -> Ctx.push(e, p, 1.3, 0.35));
				c.sound(SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.7f);
			}
		}
	}

	private static void heavy(Ctx c, LivingEntity t, int step) {
		switch (step) {
			case 1 -> {
				c.cone(3, 50, 0.35f);
				Vfx.shake(c.level, t.position(), 10, 0.3f, 4);
				c.sound(SoundEvents.MACE_SMASH_GROUND, 0.5f, 1.2f);
			}
			case 2 -> {
				slash(c, 3.8, 90, 0.0);
				c.cone(3.8, 90, 0.5f).forEach(e -> Ctx.push(e, c.caster.position(), 1.0, 0.2));
				c.sound(SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.7f);
			}
			case 3 -> {
				Vec3 o = c.caster.position();
				Tasks.repeat(0, 2, 5, i -> {
					Vec3 p = o.add(c.dir.scale(2 + i * 1.5));
					Vfx.debris(c.level, p, 1.5, 12);
					c.areaAt(p, 1.8, 0.3f);
					Vfx.sound(c.level, p, SoundEvents.MACE_SMASH_GROUND, 0.6f, 0.9f + i * 0.1f);
				});
			}
			default -> {
				Vec3 p = t.position();
				c.areaAt(p, 5, 1.0f).forEach(e -> ModEffects.apply(e, ModEffects.KNOCKDOWN, 40, 0));
				Vfx.debris(c.level, p, 4, 50);
				FxDisplay.ring(c.level, p, null, 5, 8);
				Vfx.shake(c.level, p, 24, 0.9f, 10);
				Vfx.flash(c.level, p, 20, 0xFFE0A0, 3);
				c.sound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2f, 0.8f);
			}
		}
	}
}
