package com.magicmod.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/** MOD 独自の粒。種類ごとの動き(重さ・寿命・大きさの変化・回転・光るか)を Style で決める。 */
public class MagicParticle extends SingleQuadParticle {

	/** 動きの設定。 */
	public record Style(int lifeMin, int lifeMax, float size, float endScale, float gravity, float friction, float spin, boolean glow,
			boolean animate, float rise) {
	}

	private final SpriteSet sprites;
	private final Style style;
	private final float baseSize;
	private final float spin;

	protected MagicParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Style s) {
		super(level, x, y, z, xd, yd, zd, sprites.first());
		this.sprites = sprites;
		this.style = s;
		this.xd = xd;
		this.yd = yd + s.rise();
		this.zd = zd;
		this.lifetime = s.lifeMin() + this.random.nextInt(Math.max(1, s.lifeMax() - s.lifeMin() + 1));
		this.baseSize = s.size() * (0.75f + this.random.nextFloat() * 0.5f);
		this.quadSize = baseSize;
		this.gravity = s.gravity();
		this.friction = s.friction();
		this.hasPhysics = false;
		this.spin = s.spin() * (this.random.nextBoolean() ? 1 : -1);
		this.roll = this.random.nextFloat() * (float) Math.PI * 2;
		this.oRoll = this.roll;
		if (s.animate()) {
			setSpriteFromAge(sprites);
		} else {
			setSprite(sprites.get(this.random));
		}
	}

	@Override
	public void tick() {
		super.tick();
		this.oRoll = this.roll;
		this.roll += spin;
		float t = (float) this.age / Math.max(1, this.lifetime);
		this.quadSize = baseSize * (1 + (style.endScale() - 1) * t);
		this.setAlpha(t < 0.7f ? 1f : Math.max(0, 1 - (t - 0.7f) / 0.3f));
		if (style.animate()) {
			setSpriteFromAge(sprites);
		}
	}

	@Override
	protected int getLightCoords(float a) {
		return style.glow() ? LightCoordsUtil.withBlock(super.getLightCoords(a), 15) : super.getLightCoords(a);
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Style s) {
		return (SimpleParticleType o, ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
				RandomSource random) -> new MagicParticle(level, x, y, z, xd, yd, zd, sprites, s);
	}
}
