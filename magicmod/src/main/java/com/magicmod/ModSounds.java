package com.magicmod;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** MOD 独自の効果音(tools/gen_sounds.py で合成した ogg)。 */
public final class ModSounds {

	private static final Map<Element, SoundEvent> CAST = new EnumMap<>(Element.class);
	private static final Map<Element, SoundEvent> IMPACT = new EnumMap<>(Element.class);
	public static final SoundEvent MAX_CHARGE = reg("max_charge");
	public static final SoundEvent MAX_RELEASE = reg("max_release");
	public static final SoundEvent TELEGRAPH = reg("telegraph");
	public static final SoundEvent DODGE = reg("dodge");
	public static final SoundEvent JUST_DODGE = reg("just_dodge");
	public static final SoundEvent REACTION = reg("reaction");
	public static final SoundEvent COMBO_UP = reg("combo_up");
	public static final SoundEvent BOSS_ROAR = reg("boss_roar");
	public static final SoundEvent EVOLVE = reg("evolve");
	public static final SoundEvent SKILL_USE = reg("skill_use");
	public static final SoundEvent QUEST_TAKE = reg("quest_take");
	public static final SoundEvent QUEST_DONE = reg("quest_done");
	public static final SoundEvent LIFE_USE = reg("life_use");
	public static final SoundEvent RIDE_DASH = reg("ride_dash");
	public static final SoundEvent FUSION_CHARGE = reg("fusion_charge");
	public static final SoundEvent TRIPLE_FINISH = reg("triple_finish");

	public static final Map<String, SoundEvent> OTHER = new java.util.HashMap<>();

	static {
		for (String k : new String[] { "swing_blade", "swing_spear", "swing_dual", "swing_whirl", "swing_heavy", "hit_slash", "hit_blunt",
				"hit_finisher" }) {
			OTHER.put(k, reg(k));
		}
		for (String kind : new String[] { "beast", "bat", "slime", "spirit", "humanoid", "golem", "dragon", "deep" }) {
			for (String cat : new String[] { "idle", "hurt", "death" }) {
				OTHER.put("voice_" + kind + "_" + cat, reg("voice_" + kind + "_" + cat));
			}
		}
		for (Element e : Element.values()) {
			CAST.put(e, reg("cast_" + e.id));
			IMPACT.put(e, reg("impact_" + e.id));
		}
	}

	private ModSounds() {
	}

	private static SoundEvent reg(String id) {
		Identifier i = Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, id);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, i, SoundEvent.createVariableRangeEvent(i));
	}

	public static SoundEvent cast(@Nullable Element e) {
		return e == null ? Fx.castSound(null) : CAST.get(e);
	}

	public static SoundEvent impact(@Nullable Element e) {
		return e == null ? net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT : IMPACT.get(e);
	}

	/** 敵・使い魔の id → 鳴き声の種類。 */
	private static final Map<String, String> KIND = new java.util.HashMap<>();

	static {
		put("beast", "familiar_pegasus", "familiar_behemoth", "familiar_wolf", "volt_rat", "magma_hound", "thunder_wolf_alpha", "morodis");
		put("bat", "familiar_bird", "tornado_bat", "void_bat", "nightmare_bat", "gale_harpy", "zepharion");
		put("slime", "familiar_mud_slime", "spark_jelly", "salamander_king");
		put("spirit", "familiar_fairy", "familiar_elemental", "familiar_guardian", "wind_sprite", "lumin_sprite", "jinx_moth", "gravity_mimic");
		put("humanoid", "flame_imp", "tideling", "shadow_stalker", "cursed_puppet", "curse_witch", "holy_knight_remnant", "abyss_executor",
				"gardonius", "necrovant", "seraphiel", "melusine");
		put("golem", "familiar_golem", "holy_guardian", "orbit_golem", "omnis");
		put("dragon", "familiar_ancient_dragon", "igdrum");
		put("deep", "kraken_spawn", "leviathan_juvenile", "singularis");
	}

	private static void put(String kind, String... ids) {
		for (String id : ids) {
			KIND.put(id, kind);
		}
	}

	/** この敵・使い魔の鳴き声(vanilla の音を置き換える)。独自の敵でなければ null。cat は idle / hurt / death。 */
	public static @Nullable SoundEvent voice(net.minecraft.world.entity.Entity e, String cat) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
		if (!MagicMod.MOD_ID.equals(id.getNamespace())) {
			return null;
		}
		String kind = KIND.get(id.getPath());
		return kind == null ? null : OTHER.get("voice_" + kind + "_" + cat);
	}

	public static SoundEvent weapon(String key) {
		return OTHER.get(key);
	}

	public static void init() {
	}
}
