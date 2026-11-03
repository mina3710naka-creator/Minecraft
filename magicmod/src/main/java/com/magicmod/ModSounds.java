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

	static {
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

	public static void init() {
	}
}
