package com.magicmod;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** MOD 独自のパーティクル(絵は tools/gen_assets.py が描く)。属性ごとの「しるし」になる粒。 */
public final class ModParticles {

	/** id → コマ数(アニメーションの枚数)。 */
	public static final Map<String, Integer> FRAMES = new LinkedHashMap<>();

	public static final SimpleParticleType EMBER = reg("ember", 4);
	public static final SimpleParticleType SPARK = reg("spark", 4);
	public static final SimpleParticleType DROPLET = reg("droplet", 3);
	public static final SimpleParticleType GALE = reg("gale", 4);
	public static final SimpleParticleType VOID_MOTE = reg("void_mote", 4);
	public static final SimpleParticleType SHADOW_WISP = reg("shadow_wisp", 4);
	public static final SimpleParticleType HOLY_STAR = reg("holy_star", 4);
	public static final SimpleParticleType CURSE_MOTE = reg("curse_mote", 4);
	public static final SimpleParticleType RUNE = reg("rune", 8);
	public static final SimpleParticleType STEEL_FLASH = reg("steel_flash", 3);

	private ModParticles() {
	}

	private static SimpleParticleType reg(String id, int frames) {
		FRAMES.put(id, frames);
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, id), FabricParticleTypes.simple(true));
	}

	/** 属性のしるしの粒(無属性=武器の閃き)。 */
	public static SimpleParticleType of(@Nullable Element e) {
		if (e == null) {
			return STEEL_FLASH;
		}
		return switch (e) {
			case FIRE -> EMBER;
			case THUNDER -> SPARK;
			case WATER -> DROPLET;
			case WIND -> GALE;
			case GRAVITY -> VOID_MOTE;
			case DARK -> SHADOW_WISP;
			case LIGHT -> HOLY_STAR;
			case CURSE -> CURSE_MOTE;
		};
	}

	public static void init() {
	}
}
