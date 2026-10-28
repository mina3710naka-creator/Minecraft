package com.lampvideo.trolley;

import com.lampvideo.LampVideoMod;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** トロッコムービー: アイテム(通路を出す)・特殊ブロック(持ち物にはない)・乗り物の運転。 */
public final class ModTrolley {

	private static final ResourceKey<Block> STATION_KEY =
			ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "trolley_station"));
	public static final Block STATION = Registry.register(BuiltInRegistries.BLOCK, STATION_KEY,
			new TrolleyStationBlock(BlockBehaviour.Properties.of().setId(STATION_KEY).strength(3.0F).sound(SoundType.STONE)));
	public static final BlockEntityType<TrolleyStationBlockEntity> STATION_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "trolley_station"),
			FabricBlockEntityTypeBuilder.create(TrolleyStationBlockEntity::new, STATION).build());
	public static final Item TROLLEY_MOVIE = movieItem();

	private ModTrolley() {
	}

	private static Item movieItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "trolley_movie"));
		return Registry.register(BuiltInRegistries.ITEM, key, new TrolleyMovieItem(new Item.Properties().setId(key)));
	}

	public static void init() {
		TrolleyNetwork.init();
		TrolleyRide.init();
		// 乗り物と額縁は、壊したり、動かしたり、乗り降りしたりできない(動画の再生中に、壊れないように)
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				entity.entityTags().contains(TrolleyRide.TAG) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				entity.entityTags().contains(TrolleyRide.TAG) ? InteractionResult.FAIL : InteractionResult.PASS);
	}
}
