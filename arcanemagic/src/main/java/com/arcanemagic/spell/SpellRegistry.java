package com.arcanemagic.spell;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.arcanemagic.spell.spells.BlinkSpell;
import com.arcanemagic.spell.spells.FireballSpell;
import com.arcanemagic.spell.spells.HealSpell;
import com.arcanemagic.spell.spells.IceShardSpell;
import com.arcanemagic.spell.spells.LightningSpell;
import com.arcanemagic.spell.spells.PushSpell;

import net.minecraft.resources.Identifier;

/**
 * MOD内蔵の6つの魔法を保持する簡易レジストリ。
 * ゲームのRegistryは使わず、静的なMapで十分な単純さで管理する。
 */
public final class SpellRegistry {

	private static final Map<Identifier, Spell> SPELLS = new LinkedHashMap<>();

	public static final Spell FIREBALL = register(new FireballSpell());
	public static final Spell ICE_SHARD = register(new IceShardSpell());
	public static final Spell HEAL = register(new HealSpell());
	public static final Spell LIGHTNING = register(new LightningSpell());
	public static final Spell BLINK = register(new BlinkSpell());
	public static final Spell PUSH = register(new PushSpell());

	private SpellRegistry() {
	}

	private static Spell register(Spell spell) {
		SPELLS.put(spell.id(), spell);
		return spell;
	}

	public static Optional<Spell> get(Identifier id) {
		return Optional.ofNullable(SPELLS.get(id));
	}

	public static Collection<Spell> all() {
		return SPELLS.values();
	}

	public static void init() {
		// staticフィールド初期化をトリガーするために呼び出すだけでよい。
	}
}
