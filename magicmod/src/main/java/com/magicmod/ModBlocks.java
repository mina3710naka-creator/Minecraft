package com.magicmod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * ブロック登録。block("id", "日本語名", "見た目の種類", 作り方) の形で書く(tools/gen_assets.py が読む)。
 */
public final class ModBlocks {

	public static final Map<String, Block> ALL = new LinkedHashMap<>();
	public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
	public static final Map<String, String> NAMES = new LinkedHashMap<>();

	public static final Block MAGIC_TABLE = block("magic_table", "魔法テーブル", "station:magic_table",
			p -> new StationBlock(p.strength(2.5f, 1200f).sound(SoundType.STONE).lightLevel(s -> 7).requiresCorrectToolForDrops(), "magic_table"));
	public static final Block FORGE_TABLE = block("forge_table", "鍛冶テーブル", "station:forge_table",
			p -> new StationBlock(p.strength(3.5f).sound(SoundType.ANVIL).requiresCorrectToolForDrops(), "forge_table"));
	public static final Block SUMMONING_ALTAR = block("summoning_altar", "召喚祭壇", "station:summoning_altar",
			p -> new StationBlock(p.strength(3f).sound(SoundType.STONE).lightLevel(s -> 5).requiresCorrectToolForDrops(), "summoning_altar"));
	public static final Block ARCANE_ALTAR = block("arcane_altar", "秘術の祭壇", "station:arcane_altar",
			p -> new StationBlock(p.strength(3f).sound(SoundType.STONE).lightLevel(s -> 5).requiresCorrectToolForDrops(), "arcane_altar"));
	public static final Block ARCHETYPE_ALTAR = block("archetype_altar", "選定の祭壇", "station:archetype_altar",
			p -> new StationBlock(p.strength(3f).sound(SoundType.STONE).lightLevel(s -> 9).requiresCorrectToolForDrops(), "archetype_altar"));
	public static final Block SEAL_ALTAR = block("seal_altar", "封印の祭壇", "station:seal_altar",
			p -> new StationBlock(p.strength(5f, 1200f).sound(SoundType.STONE).lightLevel(s -> 4).requiresCorrectToolForDrops(), "seal_altar"));
	public static final Block TRANSFORM_ALTAR = block("transform_altar", "変身の祭壇", "station:transform_altar",
			p -> new StationBlock(p.strength(5f, 1200f).sound(SoundType.STONE).lightLevel(s -> 8).requiresCorrectToolForDrops(), "transform_altar"));
	public static final Block END_ALTAR = block("end_altar", "終焉の祭壇", "station:end_altar",
			p -> new StationBlock(p.strength(5f, 1200f).sound(SoundType.STONE).lightLevel(s -> 8).requiresCorrectToolForDrops(), "end_altar"));
	public static final Block GUILD_BOARD = block("guild_board", "ギルド掲示板", "station:guild_board",
			p -> new StationBlock(p.strength(2f).sound(SoundType.WOOD).lightLevel(s -> 6), "guild"));
	public static final Block CRYSTAL_ORE = block("crystal_ore", "水晶鉱石", "ore:stone:a8e8ff",
			p -> new DropExperienceBlock(UniformInt.of(1, 4), p.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(3f, 3f).lightLevel(s -> 3)));
	public static final Block DEEPSLATE_CRYSTAL_ORE = block("deepslate_crystal_ore", "深層水晶鉱石", "ore:deepslate:a8e8ff",
			p -> new DropExperienceBlock(UniformInt.of(1, 4), p.mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(4.5f, 3f).sound(SoundType.DEEPSLATE).lightLevel(s -> 3)));
	public static final Block SILVER_ORE = block("silver_ore", "銀鉱石", "ore:stone:d8dce8",
			p -> new Block(p.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3f, 3f)));
	public static final Block DEEPSLATE_SILVER_ORE = block("deepslate_silver_ore", "深層銀鉱石", "ore:deepslate:d8dce8",
			p -> new Block(p.mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
					.strength(4.5f, 3f).sound(SoundType.DEEPSLATE)));

	private ModBlocks() {
	}

	private static Block block(String id, String ja, String style, Function<BlockBehaviour.Properties, Block> f) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, id));
		Block b = Registry.register(BuiltInRegistries.BLOCK, key, f.apply(BlockBehaviour.Properties.of().setId(key)));
		ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, id));
		Item item = Registry.register(BuiltInRegistries.ITEM, ik, new BlockItem(b, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
		ALL.put(id, b);
		ITEMS.put(id, item);
		NAMES.put(id, ja);
		return b;
	}

	public static void init() {
	}
}
