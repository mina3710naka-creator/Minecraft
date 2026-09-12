package com.arcanemagic.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** 巻物: アーケインの祭壇で杖/魔導書に特定の魔法を付与するための消費アイテム。 */
public class SpellScrollItem extends Item {

	private final Identifier spellId;

	public SpellScrollItem(Identifier spellId, Properties properties) {
		super(properties.stacksTo(16));
		this.spellId = spellId;
	}

	public Identifier getSpellId() {
		return spellId;
	}
}
