package com.magicmod;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 属性の遺跡(8 種): 15×15 の石舞台に、欠けた外壁・四隅の柱・中央の壇・封印の祭壇・宝箱・見張りの雑魚。
 * 属性ごとにブロックと飾りが違う。バイオームは属性ごとに決めてあり(data/magicmod/worldgen と MagicMod の BiomeModifications)、
 * `/magic ruin <属性>` でその場に建てて確かめられる。
 */
public record Ruins(String element) implements Feature {

	public static final MapCodec<Ruins> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.fieldOf("element").forGetter(Ruins::element))
			.apply(i, Ruins::new));

	private record Style(Block wall, Block floorA, Block floorB, Block pillar, Block lamp) {
	}

	private static Style style(Element e) {
		return switch (e) {
			case FIRE -> new Style(Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.CHISELED_NETHER_BRICKS, Blocks.SHROOMLIGHT);
			case WIND -> new Style(Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_PILLAR, Blocks.AMETHYST_BLOCK);
			case THUNDER -> new Style(Blocks.IRON_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.GLOWSTONE);
			case WATER -> new Style(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN);
			case GRAVITY -> new Style(Blocks.OBSIDIAN, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.PURPUR_PILLAR, Blocks.CRYING_OBSIDIAN);
			case DARK -> new Style(Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.CHISELED_DEEPSLATE, Blocks.SOUL_LANTERN);
			case LIGHT -> new Style(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.GOLD_BLOCK, Blocks.GLOWSTONE);
			case CURSE -> new Style(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
					Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.SOUL_LANTERN);
		};
	}

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "ruin"), CODEC);
	}

	@Override
	public MapCodec<Ruins> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin) {
		Element e = Element.byId(element);
		if (e == null) {
			return false;
		}
		return build(level, origin, e, random);
	}

	private static void set(WorldGenLevel l, BlockPos p, Block b) {
		l.setBlock(p, b.defaultBlockState(), 2);
	}

	private static boolean solid(WorldGenLevel l, BlockPos p) {
		BlockState s = l.getBlockState(p);
		return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced();
	}

	/** origin = 地表の 1 つ上(空気のところ)。 */
	public static boolean build(WorldGenLevel l, BlockPos origin, Element e, RandomSource r) {
		Style st = style(e);
		int y0 = origin.getY();
		int cx = origin.getX(), cz = origin.getZ();
		final int R = 7;
		// 土台: 空洞や水の上は柱状に下の地面まで埋める(最大 10 ブロック)
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int y = y0 - 1; y >= y0 - 10; y--) {
					BlockPos p = new BlockPos(cx + dx, y, cz + dz);
					if (y < y0 - 1 && solid(l, p)) {
						break;
					}
					Block b = y == y0 - 1 ? ((dx + dz) % 2 == 0 ? st.floorA() : st.floorB()) : st.wall();
					if (y == y0 - 1 && r.nextFloat() < 0.12f) {
						b = st.floorB();
					}
					set(l, p, b);
				}
				// 上を空ける(木や岩を取り除く)
				for (int y = y0; y <= y0 + 9; y++) {
					BlockPos p = new BlockPos(cx + dx, y, cz + dz);
					if (!l.getBlockState(p).isAir()) {
						set(l, p, Blocks.AIR);
					}
				}
			}
		}
		// 欠けた外壁(四辺の中央は入口)
		for (int d = -R; d <= R; d++) {
			for (int side = 0; side < 4; side++) {
				int dx = side < 2 ? d : (side == 2 ? -R : R);
				int dz = side < 2 ? (side == 0 ? -R : R) : d;
				if (Math.abs(d) <= 1) {
					continue;
				}
				int h = r.nextInt(4) + (Math.abs(d) >= R - 1 ? 1 : 0);
				for (int y = 0; y < h; y++) {
					set(l, new BlockPos(cx + dx, y0 + y, cz + dz), r.nextFloat() < 0.15f ? st.floorB() : st.wall());
				}
			}
		}
		// 四隅の柱
		int ph = e == Element.WIND ? 9 : 6;
		for (int sx : new int[] { -1, 1 }) {
			for (int sz : new int[] { -1, 1 }) {
				for (int y = 0; y < ph; y++) {
					set(l, new BlockPos(cx + sx * 6, y0 + y, cz + sz * 6), st.pillar());
				}
				set(l, new BlockPos(cx + sx * 6, y0 + ph, cz + sz * 6), st.lamp());
				if (e == Element.THUNDER) {
					set(l, new BlockPos(cx + sx * 6, y0 + ph + 1, cz + sz * 6), Blocks.IRON_BARS);
					set(l, new BlockPos(cx + sx * 6, y0 + ph + 2, cz + sz * 6), Blocks.IRON_BARS);
				}
			}
		}
		// 中央の壇と封印の祭壇
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				set(l, new BlockPos(cx + dx, y0, cz + dz), Math.abs(dx) == 2 || Math.abs(dz) == 2 ? st.wall() : st.pillar());
			}
		}
		l.setBlock(new BlockPos(cx, y0 + 1, cz), ModBlocks.SEAL_ALTAR.defaultBlockState(), 2);
		// 宝箱(祭壇の奥)
		BlockPos chest = new BlockPos(cx, y0 + 1, cz + 2);
		l.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 2);
		RandomizableContainer.setBlockEntityLootTable(l, r, chest,
				ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "chests/ruin_" + e.id)));
		// 属性ごとの飾り
		switch (e) {
			case FIRE -> {
				for (int sx : new int[] { -1, 1 }) {
					for (int dx = 0; dx < 2; dx++) {
						for (int dz = 0; dz < 2; dz++) {
							set(l, new BlockPos(cx + sx * 4 + dx, y0 - 1, cz - 4 + dz), Blocks.LAVA);
							set(l, new BlockPos(cx + sx * 4 + dx, y0 - 2, cz - 4 + dz), Blocks.MAGMA_BLOCK);
						}
					}
				}
			}
			case WATER -> {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						set(l, new BlockPos(cx + 4 + dx, y0 - 1, cz + 4 + dz), Blocks.WATER);
					}
				}
			}
			case GRAVITY -> {
				for (int i = 0; i < 6; i++) {
					set(l, new BlockPos(cx + r.nextInt(11) - 5, y0 + 3 + r.nextInt(4), cz + r.nextInt(11) - 5), r.nextBoolean() ? Blocks.OBSIDIAN : Blocks.CRYING_OBSIDIAN);
				}
			}
			case DARK -> {
				for (int i = 0; i < 12; i++) {
					set(l, new BlockPos(cx + r.nextInt(13) - 6, y0 - 1, cz + r.nextInt(13) - 6), Blocks.SCULK);
				}
			}
			case CURSE -> {
				for (int i = 0; i < 5; i++) {
					set(l, new BlockPos(cx + r.nextInt(11) - 5, y0, cz + r.nextInt(11) - 5), Blocks.COBWEB);
				}
			}
			default -> {
			}
		}
		// 見張り(その属性の雑魚)
		List<Enemies.Def> guards = Enemies.BY_ID.values().stream().filter(d -> d.tier() == Enemies.Tier.MINION && d.element() == e).toList();
		if (!guards.isEmpty()) {
			for (int i = 0; i < 3; i++) {
				Mob m = guards.get(i % guards.size()).type().create(l.getLevel(), EntitySpawnReason.STRUCTURE);
				if (m != null) {
					m.snapTo(cx + (i - 1) * 3 + 0.5, y0 + 1, cz - 3 + 0.5, r.nextFloat() * 360, 0);
					m.setPersistenceRequired();
					l.addFreshEntityWithPassengers(m);
				}
			}
		}
		return true;
	}
}
