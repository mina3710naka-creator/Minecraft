package com.arcanemagic.item;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

/** 巻物: アーケインの祭壇で杖/魔導書に特定の魔法を付与するための消費アイテム。 */
public class SpellScrollItem extends Item {

	private final Identifier spellId;

	public SpellScrollItem(Identifier spellId, Settings settings) {
		super(settings.maxCount(16));
		this.spellId = spellId;
	}

	public Identifier getSpellId() {
		return spellId;
	}
}
