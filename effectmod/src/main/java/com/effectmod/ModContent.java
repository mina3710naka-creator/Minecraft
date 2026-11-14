package com.effectmod;

import java.util.List;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** ブロック・アイテム・画面・データ部品の登録。 */
public final class ModContent {

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(EffectMod.MOD_ID, path);
	}

	/** アイテムに付いた効果の一覧(アイテムごとに保存・通信される)。 */
	public static final DataComponentType<List<Entry>> EFFECTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			id("effects"), new DataComponentType.Builder<List<Entry>>().persistent(Entry.CODEC.listOf())
					.networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(Entry.CODEC.listOf())).build());

	public static final Block BENCH;
	public static final Item BENCH_ITEM;

	static {
		ResourceKey<Block> bk = ResourceKey.create(Registries.BLOCK, id("effect_bench"));
		BENCH = Registry.register(BuiltInRegistries.BLOCK, bk, new BenchBlock(
				BlockBehaviour.Properties.of().setId(bk).strength(3.5f, 1200f).sound(SoundType.STONE).lightLevel(s -> 7)));
		ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, id("effect_bench"));
		BENCH_ITEM = Registry.register(BuiltInRegistries.ITEM, ik,
				new BlockItem(BENCH, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
	}

	public static final ExtendedMenuType<BenchMenu, BlockPos> BENCH_MENU = Registry.register(BuiltInRegistries.MENU,
			id("effect_bench"),
			new ExtendedMenuType<BenchMenu, BlockPos>((id, inv, pos) -> new BenchMenu(id, inv, ContainerLevelAccess.NULL),
					BlockPos.STREAM_CODEC));

	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("main"));

	private ModContent() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(BENCH_ITEM))
				.title(Component.translatable("itemGroup.effectmod"))
				.displayItems((ctx, out) -> out.accept(BENCH_ITEM))
				.build());
	}
}
