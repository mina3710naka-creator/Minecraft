package com.lampvideo.pc;

import com.lampvideo.LampVideoMod;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** PC・キーボード・管理パネルの登録。 */
public final class ModPc {

	public static final Block PC = block("pc", PcBlock.Kind.PC);
	public static final Block KEYBOARD = block("keyboard", PcBlock.Kind.KEYBOARD);
	public static final Item PC_ITEM = item("pc", PC);
	public static final Item KEYBOARD_ITEM = item("keyboard", KEYBOARD);
	/** 管理パネル: ブロックではなく、持ち歩くアイテム(リモコンの進化版)。 */
	public static final Item PANEL_ITEM = plainItem("management_panel");
	public static final BlockEntityType<PcBlockEntity> PC_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "pc"),
			FabricBlockEntityTypeBuilder.create(PcBlockEntity::new, PC).build());

	private ModPc() {
	}

	private static Block block(String path, PcBlock.Kind kind) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		BlockBehaviour.Properties props = BlockBehaviour.Properties.of().setId(key).strength(1.5F).sound(SoundType.METAL);
		if (kind == PcBlock.Kind.KEYBOARD) {
			props = props.noOcclusion();
		}
		return Registry.register(BuiltInRegistries.BLOCK, key, new PcBlock(props, kind));
	}

	private static Item plainItem(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key, new ManagementPanelItem(new Item.Properties().setId(key)));
	}

	private static Item item(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	public static void init() {
		PcNetwork.init();
		PanelNetwork.init();
	}
}
