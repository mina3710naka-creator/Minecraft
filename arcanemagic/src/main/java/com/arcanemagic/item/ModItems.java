package com.arcanemagic.item;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.item.group.ModItemGroup;
import com.arcanemagic.spell.SpellRegistry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModItems {

	public static final Item WAND = register("wand", WandItem::new, new Item.Settings().maxCount(1));
	public static final Item SPELLBOOK = register("spellbook", SpellbookItem::new, new Item.Settings().maxCount(1));
	public static final Item ARCANE_CRYSTAL = register("arcane_crystal", Item::new, new Item.Settings());

	public static final Item FIREBALL_SCROLL = registerScroll("fireball_scroll", SpellRegistry.FIREBALL.id());
	public static final Item ICE_SHARD_SCROLL = registerScroll("ice_shard_scroll", SpellRegistry.ICE_SHARD.id());
	public static final Item HEAL_SCROLL = registerScroll("heal_scroll", SpellRegistry.HEAL.id());
	public static final Item LIGHTNING_SCROLL = registerScroll("lightning_scroll", SpellRegistry.LIGHTNING.id());
	public static final Item BLINK_SCROLL = registerScroll("blink_scroll", SpellRegistry.BLINK.id());
	public static final Item PUSH_SCROLL = registerScroll("push_scroll", SpellRegistry.PUSH.id());

	private ModItems() {
	}

	@FunctionalInterface
	private interface ItemFactory {
		Item create(Item.Settings settings);
	}

	private static Item register(String path, ItemFactory factory, Item.Settings settings) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(ArcaneMagic.MOD_ID, path));
		Item item = factory.create(settings.registryKey(key));
		return Registry.register(Registries.ITEM, key, item);
	}

	private static Item registerScroll(String path, Identifier spellId) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(ArcaneMagic.MOD_ID, path));
		Item item = new SpellScrollItem(spellId, new Item.Settings().registryKey(key));
		return Registry.register(Registries.ITEM, key, item);
	}

	public static void init() {
		ModItemGroup.init();
	}
}
