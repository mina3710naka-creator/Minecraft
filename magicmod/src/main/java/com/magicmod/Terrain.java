package com.magicmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * 魔法が地形に与える小さな変化(壊しすぎないもの): 水は火を消し溶岩を冷やす、炎は雪と氷を溶かす、
 * 凍結は水面を薄氷(時間で溶ける)にする。燃え広がる火を置くのは mobGriefing が有効なときの大技だけ。
 */
public final class Terrain {

	private Terrain() {
	}

	static void react(Ctx c, Vec3 center, double r) {
		if (c.physical || c.element() == null || !Settings.get().terrainEffects) {
			return;
		}
		switch (c.element()) {
			case WATER -> douse(c.level, center, r);
			case FIRE -> melt(c.level, center, r, c.spell != null && c.spell.no >= 8);
			default -> {
			}
		}
	}

	private static Iterable<BlockPos> sphere(Vec3 c, double r) {
		BlockPos b = BlockPos.containing(c);
		int ri = (int) Math.ceil(r);
		return BlockPos.betweenClosed(b.offset(-ri, -2, -ri), b.offset(ri, 2, ri));
	}

	/** 火を消し、溶岩を黒曜石/丸石に。 */
	public static void douse(ServerLevel l, Vec3 c, double r) {
		int n = 0;
		for (BlockPos p : sphere(c, r)) {
			if (p.distToCenterSqr(c) > r * r) {
				continue;
			}
			BlockState s = l.getBlockState(p);
			if (s.is(BlockTags.FIRE)) {
				l.removeBlock(p, false);
				n++;
			} else if (s.getFluidState().is(Fluids.LAVA)) {
				l.setBlockAndUpdate(p, Blocks.OBSIDIAN.defaultBlockState());
				n++;
			} else if (s.getFluidState().is(Fluids.FLOWING_LAVA)) {
				l.setBlockAndUpdate(p, Blocks.COBBLESTONE.defaultBlockState());
				n++;
			}
			if (n > 0 && n % 4 == 1) {
				l.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.02);
			}
		}
		if (n > 0) {
			l.playSound(null, BlockPos.containing(c), SoundEvents.LAVA_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
		}
	}

	/** 雪と氷を溶かす。big なら(mobGriefing が有効なとき)地面に火をつける。 */
	public static void melt(ServerLevel l, Vec3 c, double r, boolean big) {
		boolean grief = l.getGameRules().get(GameRules.MOB_GRIEFING);
		for (BlockPos p : sphere(c, r)) {
			if (p.distToCenterSqr(c) > r * r) {
				continue;
			}
			BlockState s = l.getBlockState(p);
			if (s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW)) {
				l.removeBlock(p, false);
			} else if (s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE)) {
				l.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
				l.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
			} else if (big && grief && Settings.get().fireIgnitesGround && s.isAir() && l.getRandom().nextFloat() < 0.18f) {
				BlockPos below = p.below();
				if (l.getBlockState(below).isFaceSturdy(l, below, net.minecraft.core.Direction.UP)) {
					l.setBlockAndUpdate(p, BaseFireBlock.getState(l, p));
				}
			}
		}
	}

	/** 水面を薄氷にする(時間で溶けて元の水に戻る)。 */
	public static void freeze(ServerLevel l, Vec3 c, double r) {
		for (BlockPos p : sphere(c, r)) {
			if (p.distToCenterSqr(c) > r * r) {
				continue;
			}
			BlockState s = l.getBlockState(p);
			if (s.is(Blocks.WATER) && s.getFluidState().isSource() && l.getBlockState(p.above()).isAir()) {
				l.setBlockAndUpdate(p, Blocks.FROSTED_ICE.defaultBlockState());
				l.scheduleTick(p, Blocks.FROSTED_ICE, 60 + l.getRandom().nextInt(60));
				l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.01);
			}
		}
	}
}
