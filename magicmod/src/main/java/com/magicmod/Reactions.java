package com.magicmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 元素反応: 魔法で敵に属性の「しるし」が付き、数秒以内に別の属性を当てると反応が起きる(設計書 11.1 の属性相性を技にしたもの)。
 * 反応は威力の倍率で追加ダメージ + 範囲効果 + 状態異常 + 専用の演出。同じ相手には 1.5 秒の間隔をあける。
 */
public final class Reactions {

	private record Mark(Element e, long until) {
	}

	private static final Map<UUID, Mark> MARKS = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
	public static final int MARK_TICKS = 120;
	static void clearCooldown(LivingEntity t) {
		COOLDOWN.remove(t.getUUID());
	}

	/** 自己テスト用: 最後に起きた反応の名前。 */
	public static String lastName = "";

	private Reactions() {
	}

	private static boolean pair(Element a, Element b, Element x, Element y) {
		return a == x && b == y || a == y && b == x;
	}

	private static @Nullable String name(Element a, Element b) {
		if (pair(a, b, Element.FIRE, Element.WATER)) {
			return "蒸発";
		}
		if (pair(a, b, Element.THUNDER, Element.WATER)) {
			return "拡散感電";
		}
		if (pair(a, b, Element.WIND, Element.FIRE)) {
			return "火炎旋風";
		}
		if (pair(a, b, Element.GRAVITY, Element.THUNDER)) {
			return "磁力爆縮";
		}
		if (pair(a, b, Element.LIGHT, Element.DARK)) {
			return "対消滅";
		}
		if (pair(a, b, Element.CURSE, Element.DARK)) {
			return "侵食";
		}
		if (pair(a, b, Element.WIND, Element.WATER)) {
			return "凍結";
		}
		if (pair(a, b, Element.FIRE, Element.THUNDER)) {
			return "過負荷";
		}
		if (pair(a, b, Element.WIND, Element.THUNDER)) {
			return "雷嵐";
		}
		if (pair(a, b, Element.GRAVITY, Element.WATER)) {
			return "渦潮";
		}
		return null;
	}

	/** 反応の一覧(手引き用)。 */
	public static List<String> guide() {
		return List.of("炎+水=蒸発(大ダメージ・視界を奪う)", "雷+水=拡散感電(周りの敵へ連鎖・麻痺)", "風+炎=火炎旋風(範囲・打ち上げ・炎上)",
				"重力+雷=磁力爆縮(引き寄せて爆発)", "光+闇=対消滅(特大ダメージ・強化を消す)", "呪い+闇=侵食(防御低下・衰弱・毒)", "風+水=凍結(足止め・水面が凍る)",
				"炎+雷=過負荷(爆発・吹き飛ばし)", "風+雷=雷嵐(落雷)", "重力+水=渦潮(引き込み・溺れ)");
	}

	/** 魔法が当たった(ダメージ処理のあと)。 */
	static void onHit(Ctx c, LivingEntity t) {
		if (c.physical || c.element() == null || c.depth > 0 || !t.isAlive()) {
			return;
		}
		Element e = c.element();
		ServerLevel l = c.level;
		long now = l.getGameTime();
		Mark m = MARKS.get(t.getUUID());
		if (m != null && now < m.until() && m.e() != e) {
			String n = name(m.e(), e);
			long cd = COOLDOWN.getOrDefault(t.getUUID(), 0L);
			if (n != null && now >= cd) {
				MARKS.remove(t.getUUID());
				COOLDOWN.put(t.getUUID(), now + 30);
				trigger(c, t, n, m.e(), e);
				return;
			}
		}
		MARKS.put(t.getUUID(), new Mark(e, now + MARK_TICKS));
		Vfx.near(l, ModParticles.of(e), t.getX(), t.getY() + t.getBbHeight() + 0.5, t.getZ(), 1, 0.1, 0.05, 0.1, 0);
	}

	/** しるしの属性(ステータス画面や表示用)。 */
	public static @Nullable Element markOf(LivingEntity t) {
		Mark m = MARKS.get(t.getUUID());
		return m != null && t.level().getGameTime() < m.until() ? m.e() : null;
	}

	private static void hurt(Ctx c, LivingEntity t, float mult) {
		if (!t.isAlive()) {
			return;
		}
		Combat.CURRENT.set(c);
		try {
			Combat.hurtClean(t, c.level, c.source(null), c.power * mult);
		} finally {
			Combat.CURRENT.remove();
		}
	}

	private static void trigger(Ctx c, LivingEntity t, String n, Element a, Element b) {
		ServerLevel l = c.level;
		lastName = n;
		Vec3 p = t.position().add(0, t.getBbHeight() * 0.5, 0);
		c.depth++;
		try {
			switch (n) {
				case "蒸発" -> {
					hurt(c, t, 1.6f);
					ModEffects.apply(t, MobEffects.BLINDNESS, 50, 0);
					Vfx.near(l, ParticleTypes.CLOUD, p.x, p.y, p.z, 40, 1.0, 0.8, 1.0, 0.12);
					Vfx.near(l, ParticleTypes.WHITE_SMOKE, p.x, p.y + 0.5, p.z, 25, 0.8, 0.8, 0.8, 0.06);
					Vfx.sound(l, p, SoundEvents.LAVA_EXTINGUISH, 1.2f, 1.2f);
				}
				case "拡散感電" -> {
					hurt(c, t, 0.5f);
					ModEffects.apply(t, ModEffects.PARALYSIS, 25, 0);
					LivingEntity from = t;
					List<LivingEntity> done = new java.util.ArrayList<>(List.of(t));
					for (int i = 0; i < 4; i++) {
						List<LivingEntity> near = c.enemiesNear(from.position(), 6);
						near.removeAll(done);
						if (near.isEmpty()) {
							break;
						}
						LivingEntity nx = near.get(0);
						Vfx.lightningLine(l, from.position().add(0, 1, 0), nx.position().add(0, 1, 0), 0x6EC8FF, 1);
						hurt(c, nx, 0.7f);
						ModEffects.apply(nx, ModEffects.PARALYSIS, 20, 0);
						ModEffects.apply(nx, ModEffects.WET, 100, 0);
						done.add(nx);
						from = nx;
					}
					Vfx.sound(l, p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
				}
				case "火炎旋風" -> {
					for (LivingEntity x : c.enemiesNear(p, 4)) {
						hurt(c, x, 0.9f);
						x.igniteForTicks(100);
						x.setDeltaMovement(x.getDeltaMovement().add(0, 0.7, 0));
						x.syncVelocity = true;
					}
					Tasks.repeat(0, 2, 8, i -> Vfx.tornado(l, p.subtract(0, 1, 0), 5, 2.2, l.getGameTime(), ParticleTypes.FLAME, ParticleTypes.LAVA));
					FxDisplay.circle(l, t.position(), Element.FIRE, 4, 24, 14f);
					Vfx.sound(l, p, SoundEvents.FIRECHARGE_USE, 1.5f, 0.6f);
				}
				case "磁力爆縮" -> {
					for (LivingEntity x : c.enemiesNear(p, 6)) {
						Ctx.pull(x, p, 1.2);
					}
					Vfx.vortex(l, p, 5, ParticleTypes.ELECTRIC_SPARK, 30);
					Tasks.later(8, () -> {
						for (LivingEntity x : c.enemiesNear(p, 3.5)) {
							hurt(c, x, 1.3f);
						}
						Vfx.shockwave(l, p.subtract(0, 1, 0), 4, Element.THUNDER);
						Vfx.sound(l, p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.2f);
					});
				}
				case "対消滅" -> {
					hurt(c, t, 2.4f);
					for (var inst : List.copyOf(t.getActiveEffects())) {
						if (inst.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) {
							t.removeEffect(inst.getEffect());
						}
					}
					Vfx.near(l, Vfx.dust(0xFFFFFF, 2f), p.x, p.y, p.z, 20, 0.2, 0.2, 0.2, 0);
					Vfx.burstOut(l, p, Vfx.fade(0xFFFFFF, 0x6020A0, 2f), 50, 0.7);
					FxDisplay.orb(l, p, false, 2.5, 2, 6);
					Vfx.flash(l, p, 32, 0xFFFFFF, 6);
					Vfx.sound(l, p, SoundEvents.BEACON_DEACTIVATE, 1.5f, 1.4f);
				}
				case "侵食" -> {
					hurt(c, t, 0.8f);
					ModEffects.apply(t, ModEffects.ARMOR_BREAK, 160, 1);
					ModEffects.apply(t, MobEffects.WEAKNESS, 160, 1);
					ModEffects.apply(t, MobEffects.WITHER, 100, 1);
					Vfx.near(l, ModParticles.SHADOW_WISP, p.x, p.y, p.z, 20, 0.5, 0.7, 0.5, 0.03);
					Vfx.near(l, ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, 10, 0.4, 0.6, 0.4, 0.04);
					Vfx.sound(l, p, SoundEvents.WITHER_AMBIENT, 0.7f, 1.6f);
				}
				case "凍結" -> {
					hurt(c, t, 0.7f);
					t.setTicksFrozen(t.getTicksRequiredToFreeze() + 120);
					ModEffects.apply(t, MobEffects.SLOWNESS, 80, 3);
					Terrain.freeze(l, t.position(), 3);
					Vfx.near(l, ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 40, 0.7, 0.8, 0.7, 0.05);
					Vfx.near(l, Vfx.dust(0xC8F0FF, 1.6f), p.x, p.y, p.z, 20, 0.5, 0.7, 0.5, 0);
					Vfx.sound(l, p, SoundEvents.GLASS_BREAK, 1f, 1.5f);
				}
				case "過負荷" -> {
					for (LivingEntity x : c.enemiesNear(p, 3.5)) {
						hurt(c, x, 1.0f);
						Ctx.push(x, p, 1.4, 0.4);
					}
					Vfx.shockwave(l, p.subtract(0, 1, 0), 3.5, Element.FIRE);
					Vfx.far(l, ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
					Vfx.shake(l, p, 16, 0.5f, 5);
					Vfx.sound(l, p, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
				}
				case "雷嵐" -> {
					hurt(c, t, 1.1f);
					c.lightning(t.position());
					ModEffects.apply(t, ModEffects.SHOCK, 80, 1);
				}
				case "渦潮" -> {
					hurt(c, t, 0.6f);
					for (LivingEntity x : c.enemiesNear(p, 4)) {
						Ctx.pull(x, p, 0.9);
						ModEffects.apply(x, ModEffects.DROWN, 60, 0);
					}
					Tasks.repeat(0, 2, 6, i -> Vfx.vortex(l, p.subtract(0, 0.5, 0), 3.5, ParticleTypes.SPLASH, 14));
					FxDisplay.circle(l, t.position(), Element.WATER, 3.5, 20, -12f);
					Vfx.sound(l, p, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.2f, 1f);
				}
				default -> {
				}
			}
		} finally {
			c.depth--;
		}
		Vfx.sound(l, p, ModSounds.REACTION, 1.1f, 1f);
		DamageNumbers.text(t, Component.literal(n + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 1.5f);
		Vfx.near(l, ModParticles.RUNE, p.x, p.y + 1, p.z, 10, 0.4, 0.4, 0.4, 0.05);
		if (c.caster instanceof ServerPlayer sp) {
			Combo.hit(sp, false, 3);
			Casting.msg(sp, "元素反応: " + n + "(" + a.ja + "+" + b.ja + ")", ChatFormatting.GOLD);
		}
	}
}
