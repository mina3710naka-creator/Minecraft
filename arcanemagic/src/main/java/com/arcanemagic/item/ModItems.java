package com.arcanemagic.item;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.item.group.ModItemGroup;
import com.arcanemagic.spell.SpellRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {

	public static final Item WAND = register("wand", WandItem::new, new Item.Properties().stacksTo(1));
	public static final Item SPELLBOOK = register("spellbook", SpellbookItem::new, new Item.Properties().stacksTo(1));
	public static final Item ARCANE_CRYSTAL = register("arcane_crystal", Item::new, new Item.Properties());

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
		Item create(Item.Properties properties);
	}

	private static Item register(String path, ItemFactory factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ArcaneMagic.MOD_ID, path));
		Item item = factory.create(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static Item registerScroll(String path, Identifier spellId) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ArcaneMagic.MOD_ID, path));
		Item item = new SpellScrollItem(spellId, new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		ModItemGroup.init();
	}
}
