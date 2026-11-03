package com.magicmod;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 演出(パーティクル・音・画面の揺れ)をまとめたもの。すべてサーバー側で組み立てて送る。
 * 属性ごとに「色」「主粒子」「副粒子」「音」を持ち、形(輪・螺旋・竜巻・渦・柱・稲妻・魔法陣・衝撃波)を組み合わせる。
 */
public final class Vfx {

	private Vfx() {
	}

	/** 属性の配色: 主・副・光(白寄り)。 */
	public static int[] palette(@Nullable Element e) {
		if (e == null) {
			return new int[] { 0xE8E8F0, 0xA0A0B0, 0xFFFFFF };
		}
		return switch (e) {
			case WIND -> new int[] { 0x9BE8B4, 0xE6FFF0, 0xFFFFFF };
			case THUNDER -> new int[] { 0xFFE84A, 0x6EC8FF, 0xFFFFFF };
			case FIRE -> new int[] { 0xFF5A1A, 0xFFC040, 0xFFF0B0 };
			case WATER -> new int[] { 0x2F7BFF, 0x9FE0FF, 0xE8FFFF };
			case GRAVITY -> new int[] { 0x7A3CE0, 0x2A0A50, 0xD0A0FF };
			case DARK -> new int[] { 0x2A1638, 0x6A3A90, 0xB070FF };
			case LIGHT -> new int[] { 0xFFF2A0, 0xFFFFFF, 0xFFFFE0 };
			case CURSE -> new int[] { 0x8A1E6A, 0x3A0A28, 0xFF60C0 };
		};
	}

	public static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(rgb, Math.max(0.05f, Math.min(4f, size)));
	}

	public static DustColorTransitionOptions fade(int from, int to, float size) {
		return new DustColorTransitionOptions(from, to, Math.max(0.05f, Math.min(4f, size)));
	}

	public static ParticleOptions flashOpt(int rgb) {
		return ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | rgb);
	}

	/** 属性の「副」粒子(主粒子は Fx.of)。 */
	public static ParticleOptions secondary(@Nullable Element e) {
		if (e == null) {
			return ParticleTypes.CRIT;
		}
		return switch (e) {
			case WIND -> ParticleTypes.SMALL_GUST;
			case THUNDER -> ParticleTypes.WAX_OFF;
			case FIRE -> ParticleTypes.SMALL_FLAME;
			case WATER -> ParticleTypes.BUBBLE_POP;
			case GRAVITY -> ParticleTypes.PORTAL;
			case DARK -> ParticleTypes.SMOKE;
			case LIGHT -> ParticleTypes.WAX_ON;
			case CURSE -> ParticleTypes.SCULK_SOUL;
		};
	}

	// ---------------------------------------------------------------------------------------------
	// 送信

	/** 遠くからも見える(大技用)。 */
	public static void far(ServerLevel l, ParticleOptions p, double x, double y, double z, int n, double dx, double dy, double dz, double s) {
		l.sendParticles(p, true, true, x, y, z, n, dx, dy, dz, s);
	}

	public static void near(ServerLevel l, ParticleOptions p, double x, double y, double z, int n, double dx, double dy, double dz, double s) {
		l.sendParticles(p, x, y, z, n, dx, dy, dz, s);
	}

	/** 速度つきで 1 粒(count=0 のとき dx,dy,dz は速度になる)。 */
	public static void move(ServerLevel l, ParticleOptions p, Vec3 pos, Vec3 vel) {
		l.sendParticles(p, true, false, pos.x, pos.y, pos.z, 0, vel.x, vel.y, vel.z, 1.0);
	}

	public static void sound(ServerLevel l, Vec3 p, SoundEvent s, float vol, float pitch) {
		l.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	// ---------------------------------------------------------------------------------------------
	// 基本の形

	public static void ring(ServerLevel l, Vec3 c, double r, ParticleOptions p, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			near(l, p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	/** 外へ広がる輪(数 tick かけて)。 */
	public static void wave(ServerLevel l, Vec3 c, double rMax, int ticks, ParticleOptions p) {
		Tasks.repeat(0, 1, ticks, i -> {
			double r = rMax * (i + 1) / ticks;
			ring(l, c, r, p, (int) Math.max(10, r * 9));
		});
	}

	/** a から b への螺旋。 */
	public static void helix(ServerLevel l, Vec3 a, Vec3 b, double r, double turns, ParticleOptions p, ParticleOptions p2) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 0.01) {
			return;
		}
		Vec3 f = d.normalize();
		Vec3 up = Math.abs(f.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 s1 = f.cross(up).normalize();
		Vec3 s2 = f.cross(s1).normalize();
		int n = (int) Math.max(8, len * 6);
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			double ang = t * turns * Math.PI * 2;
			Vec3 base = a.add(d.scale(t));
			Vec3 o1 = s1.scale(Math.cos(ang) * r).add(s2.scale(Math.sin(ang) * r));
			near(l, p, base.x + o1.x, base.y + o1.y, base.z + o1.z, 1, 0, 0, 0, 0);
			if (p2 != null) {
				near(l, p2, base.x - o1.x, base.y - o1.y, base.z - o1.z, 1, 0, 0, 0, 0);
			}
		}
	}

	public static void line(ServerLevel l, Vec3 a, Vec3 b, ParticleOptions p, double step) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		for (double s = 0; s <= len; s += step) {
			Vec3 q = a.add(d.scale(s / Math.max(0.001, len)));
			near(l, p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
	}

	/** ギザギザの稲妻(a → b)。枝分かれもする。 */
	public static void lightningLine(ServerLevel l, Vec3 a, Vec3 b, int rgb, int depth) {
		RandomSource r = l.getRandom();
		Vec3 d = b.subtract(a);
		int segs = (int) Math.max(3, d.length() / 1.2);
		Vec3 prev = a;
		for (int i = 1; i <= segs; i++) {
			Vec3 next = i == segs ? b
					: a.add(d.scale((double) i / segs)).add(r.nextGaussian() * 0.45, r.nextGaussian() * 0.45, r.nextGaussian() * 0.45);
			line(l, prev, next, dust(rgb, 1.1f), 0.18);
			line(l, prev, next, dust(0xFFFFFF, 0.5f), 0.35);
			if (depth > 0 && r.nextFloat() < 0.25f) {
				Vec3 branch = next.add(r.nextGaussian() * 1.5, -Math.abs(r.nextGaussian()) * 1.5, r.nextGaussian() * 1.5);
				lightningLine(l, next, branch, rgb, depth - 1);
			}
			prev = next;
		}
		near(l, ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 12, 0.3, 0.3, 0.3, 0.3);
	}

	/** 地面に描く魔法陣(外円・内円・六芒星・ルーン点)。rot は回転角。 */
	public static void magicCircle(ServerLevel l, Vec3 c, double r, double rot, int rgb, int rgb2) {
		DustParticleOptions o = dust(rgb, 0.9f), o2 = dust(rgb2, 0.7f);
		double y = c.y + 0.05;
		int n = (int) Math.max(24, r * 14);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + rot;
			near(l, o, c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
			if (i % 2 == 0) {
				near(l, o2, c.x + Math.cos(-a) * r * 0.78, y, c.z + Math.sin(-a) * r * 0.78, 1, 0, 0, 0, 0);
			}
		}
		for (int k = 0; k < 6; k++) {
			double a1 = rot + k * Math.PI / 3, a2 = rot + (k + 2) * Math.PI / 3;
			Vec3 p1 = new Vec3(c.x + Math.cos(a1) * r * 0.78, y, c.z + Math.sin(a1) * r * 0.78);
			Vec3 p2 = new Vec3(c.x + Math.cos(a2) * r * 0.78, y, c.z + Math.sin(a2) * r * 0.78);
			line(l, p1, p2, o, 0.3);
			near(l, ParticleTypes.ENCHANT, p1.x, y + 0.3, p1.z, 2, 0.05, 0.2, 0.05, 0.2);
		}
	}

	public static void pillar(ServerLevel l, Vec3 base, double h, double r, ParticleOptions p, int perLayer) {
		for (double y = 0; y < h; y += 0.5) {
			for (int i = 0; i < perLayer; i++) {
				double a = l.getRandom().nextDouble() * Math.PI * 2;
				double rr = r * Math.sqrt(l.getRandom().nextDouble());
				far(l, p, base.x + Math.cos(a) * rr, base.y + y, base.z + Math.sin(a) * rr, 1, 0, 0.05, 0, 0);
			}
		}
	}

	/** 竜巻: 回転しながら上がる螺旋(tick ごとに呼ぶ)。 */
	public static void tornado(ServerLevel l, Vec3 c, double h, double r, long t, ParticleOptions p, ParticleOptions p2) {
		for (int arm = 0; arm < 3; arm++) {
			for (double y = 0; y < h; y += 0.4) {
				double rr = r * (0.35 + 0.65 * y / h);
				double a = t * 0.45 + y * 1.3 + arm * Math.PI * 2 / 3;
				near(l, (y * 3 % 2 < 1) ? p : p2, c.x + Math.cos(a) * rr, c.y + y, c.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
			}
		}
	}

	/** 渦: 外から中心へ吸い込まれる粒(tick ごとに呼ぶ)。 */
	public static void vortex(ServerLevel l, Vec3 c, double r, ParticleOptions p, int n) {
		RandomSource rnd = l.getRandom();
		for (int i = 0; i < n; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			Vec3 at = c.add(Math.cos(a) * r, rnd.nextDouble() * 1.5, Math.sin(a) * r);
			Vec3 to = c.subtract(at);
			Vec3 tangent = new Vec3(-to.z, 0, to.x).normalize().scale(0.25);
			move(l, p, at, to.scale(0.12).add(tangent));
		}
	}

	/** 中心から外へはじける粒。 */
	public static void burstOut(ServerLevel l, Vec3 c, ParticleOptions p, int n, double speed) {
		RandomSource rnd = l.getRandom();
		for (int i = 0; i < n; i++) {
			Vec3 v = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.6 + 0.2, rnd.nextGaussian()).normalize().scale(speed * (0.6 + rnd.nextDouble() * 0.6));
			move(l, p, c, v);
		}
	}

	/** 光の筋(TRAIL)を c から周りへ放射。 */
	public static void rays(ServerLevel l, Vec3 c, int rgb, int n, double len, int dur) {
		RandomSource rnd = l.getRandom();
		for (int i = 0; i < n; i++) {
			Vec3 to = c.add(new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.5 + 0.3, rnd.nextGaussian()).normalize().scale(len));
			far(l, new TrailParticleOption(to, rgb, dur), c.x, c.y, c.z, 1, 0, 0, 0, 0);
		}
	}

	/** 地面の破片(足元のブロックの欠片が飛ぶ)。 */
	public static void debris(ServerLevel l, Vec3 c, double r, int n) {
		BlockPos bp = BlockPos.containing(c.x, c.y - 0.5, c.z);
		BlockState s = l.getBlockState(bp);
		if (s.isAir()) {
			s = l.getBlockState(bp.below());
		}
		if (s.isAir() || s.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) {
			s = Blocks.STONE.defaultBlockState();
		}
		far(l, new BlockParticleOption(ParticleTypes.BLOCK, s), c.x, c.y + 0.2, c.z, n, r * 0.5, 0.3, r * 0.5, 0.3);
		far(l, new BlockParticleOption(ParticleTypes.DUST_PILLAR, s), c.x, c.y + 0.1, c.z, n / 3, r * 0.6, 0.1, r * 0.6, 0.4);
	}

	// ---------------------------------------------------------------------------------------------
	// 属性ごとの組み合わせ

	/** 詠唱した瞬間: 手元の光と足元の小さな魔法陣(ランクで大きさが変わる)。 */
	public static void cast(LivingEntity c, @Nullable Element e, String rank) {
		ServerLevel l = (ServerLevel) c.level();
		int[] pal = palette(e);
		Vec3 hand = hand(c);
		// 自分の目の前で出るので小さく控えめに(他の人から見える分は足元の魔法陣と光の筋が担当)
		burstOut(l, hand, dust(pal[0], 0.5f), 4, 0.06);
		near(l, ModParticles.of(e), hand.x, hand.y, hand.z, 1, 0.05, 0.05, 0.05, 0.01);
		double r = switch (rank) {
			case "middle" -> 1.4;
			case "upper" -> 2.2;
			case "max" -> 3.4;
			default -> 0;
		};
		if (r > 0) {
			FxDisplay.circle(l, c.position(), e, r, 18, 4f);
			near(l, ModParticles.RUNE, c.getX(), c.getY() + 0.3, c.getZ(), (int) (r * 4), r * 0.5, 0.1, r * 0.5, 0.02);
			magicCircle(l, c.position(), r, l.getGameTime() * 0.1, pal[0], pal[1]);
			far(l, ParticleTypes.ENCHANT, c.getX(), c.getY() + 1, c.getZ(), (int) (r * 10), r * 0.5, 0.6, r * 0.5, 0.6);
		}
		if ("max".equals(rank)) {
			FxDisplay.beam(l, c.position(), e, 1.6, 7, 14);
			rays(l, c.position().add(0, 1, 0), pal[2], 16, 4, 12);
			sound(l, c.position(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.6f);
			sound(l, c.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1f, 0.7f);
		} else if ("upper".equals(rank)) {
			sound(l, c.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 1.0f);
		}
	}

	/** 詠唱中(毎 tick): 回る魔法陣と集まる光。 */
	public static void channel(LivingEntity c, @Nullable Element e, float progress) {
		ServerLevel l = (ServerLevel) c.level();
		int[] pal = palette(e);
		double r = 1.0 + 1.8 * progress;
		long t = l.getGameTime();
		if (t % 2 == 0) {
			magicCircle(l, c.position(), r, t * 0.15, pal[0], pal[1]);
		}
		vortex(l, hand(c), 1.6, dust(pal[0], 0.7f), 3);
		vortex(l, c.position().add(0, 0.2, 0), r, ModParticles.RUNE, 1);
		if (t % 6 == 0) {
			sound(l, c.position(), SoundEvents.BEACON_AMBIENT, 0.5f, 1.2f + progress);
		}
	}

	public static Vec3 hand(LivingEntity c) {
		Vec3 look = c.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		return c.getEyePosition().add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.3, 0);
	}

	/** 魔法弾の軌跡(毎 tick)。 */
	public static void trail(ServerLevel l, Vec3 p, Vec3 prev, @Nullable Element e, boolean physical, long t) {
		if (physical) {
			near(l, ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
			far(l, new TrailParticleOption(prev, 0xD8D8E0, 6), p.x, p.y, p.z, 1, 0, 0, 0, 0);
			return;
		}
		int[] pal = palette(e);
		far(l, new TrailParticleOption(prev, pal[0], 8), p.x, p.y, p.z, 1, 0, 0, 0, 0);
		near(l, ModParticles.of(e), p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.01);
		near(l, fade(pal[2], pal[0], 1.4f), p.x, p.y, p.z, 2, 0.06, 0.06, 0.06, 0);
		if (e == null) {
			return;
		}
		switch (e) {
			case FIRE -> {
				near(l, ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.01);
				near(l, ParticleTypes.SMOKE, prev.x, prev.y, prev.z, 2, 0.05, 0.05, 0.05, 0.01);
				if (t % 3 == 0) {
					near(l, ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				}
			}
			case THUNDER -> {
				Vec3 j = p.add(l.getRandom().nextGaussian() * 0.3, l.getRandom().nextGaussian() * 0.3, l.getRandom().nextGaussian() * 0.3);
				line(l, prev, j, dust(pal[0], 0.6f), 0.15);
				near(l, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.15, 0.15, 0.15, 0.1);
			}
			case WATER -> {
				near(l, ParticleTypes.BUBBLE, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.02);
				near(l, ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 1, 0.1, 0.05, 0.1, 0);
				near(l, ParticleTypes.SPLASH, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0);
			}
			case WIND -> {
				double a = t * 0.9;
				near(l, ParticleTypes.CLOUD, p.x + Math.cos(a) * 0.3, p.y + Math.sin(a) * 0.3, p.z, 1, 0, 0, 0, 0);
				near(l, ParticleTypes.SMALL_GUST, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
			}
			case GRAVITY -> {
				vortex(l, p, 0.9, ParticleTypes.REVERSE_PORTAL, 2);
				near(l, dust(pal[1], 1.2f), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
			}
			case DARK -> {
				near(l, ParticleTypes.SQUID_INK, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.01);
				near(l, ParticleTypes.LARGE_SMOKE, prev.x, prev.y, prev.z, 1, 0.05, 0.05, 0.05, 0.005);
			}
			case LIGHT -> {
				near(l, ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.01);
				near(l, ParticleTypes.GLOW, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
			}
			case CURSE -> {
				near(l, ParticleTypes.WITCH, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.01);
				if (t % 2 == 0) {
					near(l, ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
				}
			}
		}
	}

	/** 着弾・命中の爆ぜ(scale 1 が普通の弾)。 */
	public static void impact(ServerLevel l, Vec3 p, @Nullable Element e, float scale) {
		int[] pal = palette(e);
		near(l, flashOpt(pal[2]), p.x, p.y, p.z, 1, 0, 0, 0, 0);
		burstOut(l, p, fade(pal[2], pal[0], 1.2f * scale), (int) (12 * scale), 0.25 * scale);
		near(l, Fx.of(e), p.x, p.y, p.z, (int) (10 * scale), 0.25 * scale, 0.25 * scale, 0.25 * scale, 0.08);
		near(l, secondary(e), p.x, p.y, p.z, (int) (6 * scale), 0.3 * scale, 0.3 * scale, 0.3 * scale, 0.05);
		burstOut(l, p, ModParticles.of(e), (int) (6 * scale) + 2, 0.18 * scale);
		if (e == null) {
			return;
		}
		switch (e) {
			case FIRE -> {
				near(l, ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, (int) (5 * scale), 0.3 * scale, 0.3 * scale, 0.3 * scale, 0.02);
				near(l, ParticleTypes.LAVA, p.x, p.y, p.z, (int) (3 * scale), 0.2, 0.1, 0.2, 0);
				sound(l, p, SoundEvents.FIRECHARGE_USE, 0.5f * scale, 1.3f);
			}
			case THUNDER -> {
				for (int i = 0; i < 2 + scale * 2; i++) {
					lightningLine(l, p, p.add(l.getRandom().nextGaussian() * scale, l.getRandom().nextGaussian() * scale,
							l.getRandom().nextGaussian() * scale), pal[1], 0);
				}
				sound(l, p, SoundEvents.TRIDENT_HIT, 0.4f, 1.6f);
			}
			case WATER -> {
				near(l, ParticleTypes.SPLASH, p.x, p.y, p.z, (int) (25 * scale), 0.4 * scale, 0.3, 0.4 * scale, 0.2);
				near(l, ParticleTypes.BUBBLE_POP, p.x, p.y, p.z, (int) (8 * scale), 0.3, 0.3, 0.3, 0.05);
				sound(l, p, SoundEvents.GENERIC_SPLASH, 0.5f * scale, 1.4f);
			}
			case WIND -> {
				near(l, ParticleTypes.GUST, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				sound(l, p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.4f * scale, 1.5f);
			}
			case GRAVITY -> {
				vortex(l, p, 1.5 * scale, ParticleTypes.REVERSE_PORTAL, (int) (12 * scale));
				sound(l, p, SoundEvents.ENDERMAN_TELEPORT, 0.4f, 0.5f);
			}
			case DARK -> {
				near(l, ParticleTypes.SQUID_INK, p.x, p.y, p.z, (int) (12 * scale), 0.3, 0.3, 0.3, 0.05);
				sound(l, p, SoundEvents.SCULK_CLICKING, 0.6f, 0.6f);
			}
			case LIGHT -> {
				rays(l, p, pal[1], (int) (6 * scale), 1.5 * scale, 8);
				sound(l, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1.6f);
			}
			case CURSE -> {
				near(l, ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, (int) (5 * scale), 0.2, 0.3, 0.2, 0.03);
				sound(l, p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.25f, 1.8f);
			}
		}
	}

	/** 範囲攻撃の衝撃波(輪が広がる + 地面の破片)。 */
	public static void shockwave(ServerLevel l, Vec3 c, double r, @Nullable Element e) {
		int[] pal = palette(e);
		FxDisplay.ring(l, c, e, r, Math.max(4, (int) (r * 1.3)));
		wave(l, c.add(0, 0.15, 0), r, Math.max(3, (int) (r * 1.2)), fade(pal[2], pal[0], 1.5f));
		wave(l, c.add(0, 0.4, 0), r * 0.8, Math.max(3, (int) r), Fx.of(e));
		debris(l, c, r, (int) (8 + r * 4));
		impact(l, c.add(0, 0.5, 0), e, (float) Math.min(2.5, 0.6 + r * 0.25));
	}

	/** 光線(a→b)。 */
	public static void beam(ServerLevel l, Vec3 a, Vec3 b, @Nullable Element e) {
		int[] pal = palette(e);
		line(l, a, b, fade(pal[2], pal[0], 1.6f), 0.22);
		helix(l, a, b, 0.35, b.distanceTo(a) / 2, dust(pal[1], 0.7f), dust(pal[0], 0.7f));
		far(l, new TrailParticleOption(b, pal[2], 10), a.x, a.y, a.z, 3, 0.05, 0.05, 0.05, 0);
		if (e == Element.THUNDER) {
			lightningLine(l, a, b, pal[0], 2);
			lightningLine(l, a, b, pal[1], 1);
		}
		impact(l, b, e, 1.2f);
	}

	/** 斬撃の弧(武器・近接魔法)。tilt は弧の傾き(縦斬り〜横薙ぎ)。 */
	public static void slash(LivingEntity c, Vec3 dir, double range, double halfAngleDeg, @Nullable Element e, double tilt) {
		ServerLevel l = (ServerLevel) c.level();
		int[] pal = palette(e);
		Vec3 base = c.position().add(0, c.getBbHeight() * 0.62, 0);
		Vec3 f = new Vec3(dir.x, 0, dir.z);
		if (f.lengthSqr() < 1.0E-4) {
			f = new Vec3(0, 0, 1);
		}
		f = f.normalize();
		int steps = (int) Math.max(8, halfAngleDeg / 6);
		for (int i = -steps; i <= steps; i++) {
			double a = Math.toRadians(halfAngleDeg) * i / steps;
			Vec3 d = Ctx.rotateY(f, a);
			double h = Math.sin(a) * tilt;
			for (double rr = range * 0.55; rr <= range; rr += 0.45) {
				Vec3 q = base.add(d.scale(rr)).add(0, h * rr * 0.3, 0);
				near(l, e == null ? dust(0xF4F4FF, 0.7f) : fade(pal[2], pal[0], 0.9f), q.x, q.y, q.z, 1, 0, 0, 0, 0);
			}
		}
		Vec3 mid = base.add(f.scale(range * 0.75));
		FxDisplay.slash(c, f, e, Math.max(1.4, range * 0.6), tilt > 0.8 ? 22f : 6f);
		near(l, e == null ? ModParticles.STEEL_FLASH : ModParticles.of(e), mid.x, mid.y, mid.z, 3, range * 0.2, 0.15, range * 0.2, 0.02);
		near(l, ParticleTypes.SWEEP_ATTACK, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
		if (e != null) {
			near(l, Fx.of(e), mid.x, mid.y, mid.z, 6, range * 0.25, 0.2, range * 0.25, 0.03);
		}
	}

	/** その場に残る範囲の見た目(毎回の tick で呼ぶ)。属性ごとに違う。 */
	public static void field(ServerLevel l, Vec3 c, double r, @Nullable Element e, long t) {
		int[] pal = palette(e);
		if (e == null) {
			ring(l, c.add(0, 0.2, 0), r, dust(pal[0], 1f), (int) (r * 10));
			return;
		}
		near(l, ModParticles.of(e), c.x, c.y + 0.6, c.z, (int) Math.max(2, r), r * 0.5, 0.5, r * 0.5, 0.02);
		switch (e) {
			case WIND -> tornado(l, c, 4 + r, r, t, ParticleTypes.CLOUD, dust(pal[0], 1.1f));
			case THUNDER -> {
				ring(l, c.add(0, 0.2, 0), r, dust(pal[0], 1f), (int) (r * 8));
				for (int i = 0; i < 2; i++) {
					double a1 = l.getRandom().nextDouble() * Math.PI * 2, a2 = l.getRandom().nextDouble() * Math.PI * 2;
					lightningLine(l, c.add(Math.cos(a1) * r, 0.3, Math.sin(a1) * r), c.add(Math.cos(a2) * r * 0.5, 1.2, Math.sin(a2) * r * 0.5),
							pal[i % 2], 0);
				}
				if (t % 10 == 0) {
					sound(l, c, SoundEvents.BEEHIVE_WORK, 0.6f, 2f);
				}
			}
			case FIRE -> {
				for (int i = 0; i < r * 6; i++) {
					double a = l.getRandom().nextDouble() * Math.PI * 2, rr = r * Math.sqrt(l.getRandom().nextDouble());
					near(l, i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, c.x + Math.cos(a) * rr, c.y + 0.1, c.z + Math.sin(a) * rr, 1,
							0.05, 0.3, 0.05, 0.02);
				}
				near(l, ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.5, c.z, 3, r * 0.4, 0.4, r * 0.4, 0.01);
				if (t % 8 == 0) {
					sound(l, c, SoundEvents.FIRE_AMBIENT, 1f, 0.8f);
				}
			}
			case WATER -> {
				vortex(l, c, r, ParticleTypes.SPLASH, (int) (r * 4));
				ring(l, c.add(0, 0.2, 0), r * (0.5 + 0.5 * Math.sin(t * 0.3)), ParticleTypes.BUBBLE_POP, (int) (r * 8));
				if (t % 10 == 0) {
					sound(l, c, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.8f, 1f);
				}
			}
			case GRAVITY -> {
				vortex(l, c.add(0, 0.5, 0), r, ParticleTypes.REVERSE_PORTAL, (int) (r * 5));
				vortex(l, c.add(0, 0.5, 0), r * 0.6, dust(pal[0], 1.2f), (int) (r * 2));
				near(l, dust(0x000000, 2.5f), c.x, c.y + 1, c.z, 4, 0.15, 0.15, 0.15, 0);
				if (t % 12 == 0) {
					sound(l, c, SoundEvents.PORTAL_AMBIENT, 0.6f, 0.5f);
				}
			}
			case DARK -> {
				near(l, ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5, c.z, (int) (r * 4), r * 0.5, 0.4, r * 0.5, 0.005);
				near(l, ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, (int) (r * 2), r * 0.5, 0.2, r * 0.5, 0.01);
				magicCircle(l, c, r, -t * 0.05, pal[1], pal[2]);
			}
			case LIGHT -> {
				magicCircle(l, c, r, t * 0.08, pal[0], pal[1]);
				pillar(l, c, 3, r * 0.6, ParticleTypes.END_ROD, 1);
			}
			case CURSE -> {
				magicCircle(l, c, r, t * 0.12, pal[0], pal[2]);
				near(l, ParticleTypes.SCULK_SOUL, c.x, c.y + 0.3, c.z, 2, r * 0.4, 0.1, r * 0.4, 0.02);
				near(l, ParticleTypes.WITCH, c.x, c.y + 0.5, c.z, (int) (r * 2), r * 0.5, 0.4, r * 0.5, 0.01);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// 画面

	/** 近くのプレイヤーの画面を揺らす(強さは距離で減る)/ 画面をその色で一瞬光らせる。 */
	public static void shake(ServerLevel l, Vec3 c, double radius, float strength, int ticks) {
		for (ServerPlayer p : l.players()) {
			double d = p.position().distanceTo(c);
			if (d < radius) {
				Net.screenFx(p, "shake", strength * (float) (1 - d / radius), ticks, 0);
			}
		}
	}

	public static void flash(ServerLevel l, Vec3 c, double radius, int rgb, int ticks) {
		for (ServerPlayer p : l.players()) {
			if (p.position().distanceTo(c) < radius) {
				Net.screenFx(p, "flash", 0.55f, ticks, rgb);
			}
		}
	}

	public static void dragonBreath(ServerLevel l, Vec3 p, int n) {
		near(l, PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), p.x, p.y, p.z, n, 0.3, 0.3, 0.3, 0.02);
	}
}
