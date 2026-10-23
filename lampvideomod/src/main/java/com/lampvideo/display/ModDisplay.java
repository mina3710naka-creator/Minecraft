package com.lampvideo.display;

import com.lampvideo.LampVideoMod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** メディアディスプレイのブロック・ブロックエンティティ・アイテム・通信の登録。 */
public final class ModDisplay {

	public static final Block DISPLAY = registerBlock("display");
	public static final Block LARGE_DISPLAY = registerLargeBlock("large_display");
	public static final Item DISPLAY_ITEM = registerItem("display", DISPLAY);
	public static final Item LARGE_DISPLAY_ITEM = registerPlainItem("large_display");
	public static final BlockEntityType<DisplayBlockEntity> DISPLAY_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "display"),
			FabricBlockEntityTypeBuilder.create(DisplayBlockEntity::new, DISPLAY, LARGE_DISPLAY).build());

	private ModDisplay() {
	}

	private static Block registerBlock(String path) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.BLOCK, key, new DisplayBlock(BlockBehaviour.Properties.of().setId(key)
				.strength(1.0F).sound(SoundType.METAL).noOcclusion()));
	}

	private static Block registerLargeBlock(String path) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.BLOCK, key, new LargeDisplayBlock(BlockBehaviour.Properties.of().setId(key)
				.strength(1.0F).sound(SoundType.METAL).noOcclusion()));
	}

	private static Item registerItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	private static Item registerPlainItem(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key, new LargeDisplayItem(new Item.Properties().setId(key)));
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(DisplayControlPayload.TYPE, DisplayControlPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DisplayControlPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				if (player.level().isClientSide()
						|| player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) {
					return;
				}
				if (!(player.level().getBlockEntity(payload.pos()) instanceof DisplayBlockEntity be)) {
					return;
				}
				int mode = Math.max(0, Math.min(4, payload.mode()));
				switch (payload.action()) {
					case DisplayControlPayload.PLAY -> be.play(safeName(payload.media()), mode);
					case DisplayControlPayload.STOP -> be.stop();
					case DisplayControlPayload.MODE -> be.setMode(mode);
					case DisplayControlPayload.PAUSE -> be.togglePause();
					case DisplayControlPayload.SEEK -> be.seek(Math.max(-600_000, Math.min(600_000, payload.value())));
					default -> { }
				}
			});
		});
	}

	/** ファイル名として安全な形にする(パスの区切りなどを除く。実際の読み込みはクライアントが、決まったフォルダの中だけで行う)。 */
	public static String safeName(String name) {
		if (name == null) {
			return "";
		}
		String n = name.replace('\\', '/');
		n = n.substring(n.lastIndexOf('/') + 1);
		return n.length() > 120 ? n.substring(0, 120) : n;
	}
}
