package com.magicmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** ギルドの小屋: 9×7 の木と石の建物。掲示板・受付・宝箱・ベッド・本棚。自然に生成され、`/magic guildhall` でも建てられる。 */
public record GuildHall(String style) implements Feature {

	public static final MapCodec<GuildHall> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.fieldOf("style").forGetter(GuildHall::style))
			.apply(i, GuildHall::new));

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "guild_hall"), CODEC);
	}

	@Override
	public MapCodec<GuildHall> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin) {
		return build(level, origin);
	}

	private static void set(WorldGenLevel l, BlockPos p, Block b) {
		l.setBlock(p, b.defaultBlockState(), 2);
	}

	private static void set(WorldGenLevel l, BlockPos p, BlockState s) {
		l.setBlock(p, s, 2);
	}

	/** origin = 地表の 1 つ上。南(+z)側に入口。 */
	public static boolean build(WorldGenLevel l, BlockPos origin) {
		int x0 = origin.getX(), y0 = origin.getY(), z0 = origin.getZ();
		final int W = 4, D = 3; // 半幅 4(=9)、半奥行き 3(=7)
		for (int dx = -W - 1; dx <= W + 1; dx++) {
			for (int dz = -D - 1; dz <= D + 1; dz++) {
				// 土台
				for (int y = y0 - 1; y >= y0 - 6; y--) {
					BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
					if (y < y0 - 1 && !l.getBlockState(p).isAir() && l.getBlockState(p).getFluidState().isEmpty() && !l.getBlockState(p).canBeReplaced()) {
						break;
					}
					set(l, p, y == y0 - 1 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS);
				}
				for (int y = y0; y <= y0 + 6; y++) {
					set(l, new BlockPos(x0 + dx, y, z0 + dz), Blocks.AIR);
				}
			}
		}
		for (int dx = -W; dx <= W; dx++) {
			for (int dz = -D; dz <= D; dz++) {
				set(l, new BlockPos(x0 + dx, y0 - 1, z0 + dz), (dx + dz) % 2 == 0 ? Blocks.SPRUCE_PLANKS : Blocks.DARK_OAK_PLANKS);
				boolean edge = Math.abs(dx) == W || Math.abs(dz) == D;
				for (int y = 0; y < 4; y++) {
					if (edge) {
						boolean corner = Math.abs(dx) == W && Math.abs(dz) == D;
						set(l, new BlockPos(x0 + dx, y0 + y, z0 + dz), corner ? Blocks.STRIPPED_OAK_LOG : (y == 0 ? Blocks.STONE_BRICKS : Blocks.OAK_PLANKS));
					}
				}
				set(l, new BlockPos(x0 + dx, y0 + 4, z0 + dz), Blocks.SPRUCE_PLANKS);                // 屋根の下地
			}
		}
		for (int dx = -W - 1; dx <= W + 1; dx++) {                                                      // 屋根のひさし
			for (int dz = -D - 1; dz <= D + 1; dz++) {
				if (Math.abs(dx) == W + 1 || Math.abs(dz) == D + 1) {
					set(l, new BlockPos(x0 + dx, y0 + 4, z0 + dz), Blocks.DARK_OAK_SLAB);
				}
			}
		}
		for (int dx = -W + 1; dx <= W - 1; dx++) {
			set(l, new BlockPos(x0 + dx, y0 + 5, z0), Blocks.DARK_OAK_PLANKS);                         // 棟
		}
		// 入口(南の中央): 2 マスの扉の位置を空ける
		for (int y = 0; y < 2; y++) {
			set(l, new BlockPos(x0, y0 + y, z0 + D), Blocks.AIR);
		}
		set(l, new BlockPos(x0, y0 + 2, z0 + D), Blocks.OAK_PLANKS);
		for (int dx : new int[] { -2, 2 }) {                                                            // 窓
			set(l, new BlockPos(x0 + dx, y0 + 2, z0 + D), Blocks.GLASS_PANE);
			set(l, new BlockPos(x0 + dx, y0 + 2, z0 - D), Blocks.GLASS_PANE);
		}
		for (int dz : new int[] { -1, 1 }) {
			set(l, new BlockPos(x0 - W, y0 + 2, z0 + dz), Blocks.GLASS_PANE);
			set(l, new BlockPos(x0 + W, y0 + 2, z0 + dz), Blocks.GLASS_PANE);
		}
		// 内装: 奥の中央に掲示板、受付はその手前、左に本棚と宝箱、右にベッド、ランタン
		set(l, new BlockPos(x0, y0, z0 - D + 1), ModBlocks.GUILD_BOARD);
		set(l, new BlockPos(x0 - 3, y0, z0 - D + 1), Blocks.BOOKSHELF);
		set(l, new BlockPos(x0 - 3, y0 + 1, z0 - D + 1), Blocks.BOOKSHELF);
		set(l, new BlockPos(x0 - 3, y0, z0), Blocks.BARREL);
		set(l, new BlockPos(x0 + 3, y0, z0 - D + 1), Blocks.CRAFTING_TABLE);
		for (int dx : new int[] { -2, 2 }) {
			set(l, new BlockPos(x0 + dx, y0 + 3, z0 - 1), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
			set(l, new BlockPos(x0 + dx, y0 + 4, z0 - 1), Blocks.SPRUCE_PLANKS);
		}
		set(l, new BlockPos(x0, y0, z0 - 1), Blocks.OAK_PRESSURE_PLATE);
		// 入口前の道しるべ(ランタン付きの柱)
		for (int sx : new int[] { -2, 2 }) {
			set(l, new BlockPos(x0 + sx, y0, z0 + D + 2), Blocks.OAK_FENCE);
			set(l, new BlockPos(x0 + sx, y0 + 1, z0 + D + 2), Blocks.OAK_FENCE);
			set(l, new BlockPos(x0 + sx, y0 + 2, z0 + D + 2), Blocks.LANTERN);
		}
		Guild.spawnReceptionist(l, new BlockPos(x0, y0, z0 - 1));
		return true;
	}
}
