package com.lampvideo.pc;

import com.lampvideo.LampVideoMod;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** PC・キーボード・管理パネルの登録。 */
public final class ModPc {

	public static final Block PC = block("pc", PcBlock.Kind.PC);
	public static final Block KEYBOARD = block("keyboard", PcBlock.Kind.KEYBOARD);
	public static final Block SPEAKER = speakerBlock("speaker");
	public static final Item SPEAKER_ITEM = item("speaker", SPEAKER);
	public static final BlockEntityType<SpeakerBlockEntity> SPEAKER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "speaker"),
			FabricBlockEntityTypeBuilder.create(SpeakerBlockEntity::new, SPEAKER).build());
	/** ヘッドホン: 頭の装備スロットに着けると、遠くからでも、はっきり聞こえる。 */
	public static final Item HEADPHONES = headphones("headphones");
	/** ヘッドホン専用スロットの中身。プレイヤーごとに保存され、ほかのプレイヤーにも同期される(見た目のため)。 */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<ItemStack> HEADPHONE_SLOT =
			net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(
					Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "headphone_slot"),
					builder -> builder.persistent(ItemStack.OPTIONAL_CODEC)
							.syncWith(ItemStack.OPTIONAL_STREAM_CODEC, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all())
							.copyOnDeath());
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

	private static Block speakerBlock(String path) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.BLOCK, key, new SpeakerBlock(
				BlockBehaviour.Properties.of().setId(key).strength(1.5F).sound(SoundType.METAL).noOcclusion()));
	}

	private static Item headphones(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		// 兜の欄には入らない(専用スロットだけ)。頭の見た目は、描画のときだけ、別に付ける(HeadphoneVisual)
		return Registry.register(BuiltInRegistries.ITEM, key, new HeadphonesItem(new Item.Properties().setId(key)));
	}

	private static Item plainItem(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key, new ManagementPanelItem(new Item.Properties().setId(key)));
	}

	private static Item item(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	public static boolean isWearingHeadphones(net.minecraft.world.entity.player.Player player) {
		ItemStack s = player.getAttached(HEADPHONE_SLOT);
		return s != null && s.is(HEADPHONES);
	}

	public static void init() {
		PcNetwork.init();
		PanelNetwork.init();
	}
}
