package com.lampvideo.trolley;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.saveddata.maps.MapId;

/**
 * トロッコムービーの通路を作る。石レンガの壁と床、天井なし、幅 3、真ん中にレール、長さ 200。
 * 位置 i(0〜199)は、特殊ブロックを 0 として、向いている方向への番号。i=0 は特殊ブロックのある行き止まり、i=1〜199 が線路。
 * 左の壁(進む向きの左)の内側に、位置ごとにマップの額縁を飾る(額縁は、乗るときに、近くから順に出す)。
 */
final class TrolleyCorridor {

	static final int LENGTH = 200;
	static final int NORMAL = 0;
	static final int CAP = 1;
	static final int CLEAR = 2;

	private TrolleyCorridor() {
	}

	/** floor = 床の高さの、始まりのブロック(クリックしたブロック)。f = 通路が伸びる水平の向き。 */
	static void build(ServerLevel level, BlockPos floor, Direction f) {
		Direction l = f.getCounterClockWise();
		BlockPos station = floor.above();
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState rail = Blocks.RAIL.defaultBlockState()
				.setValue(RailBlock.SHAPE, f.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);

		for (int i = -1; i <= LENGTH; i++) {
			BlockPos base = floor.relative(f, i);
			for (int lat = -2; lat <= 2; lat++) {
				BlockPos p = base.relative(l, lat);
				level.setBlock(p, brick, 2);
				boolean wallCol = Math.abs(lat) == 2;
				boolean cap = i == 0 || i == LENGTH;
				for (int h = 1; h <= 4; h++) {
					BlockState s;
					if (i == -1) {
						s = air;   // 手前(特殊ブロックの前)は、空ける
					} else if (wallCol && h <= 3) {
						s = brick;
					} else if (cap && Math.abs(lat) <= 1 && h <= 3 && !(i == 0 && lat == 0)) {
						s = brick;   // 行き止まりの壁
					} else if (wallCol && lat == -2 && h == 3) {
						s = brick;
					} else {
						s = air;
					}
					if (wallCol && lat == -2 && h == 3 && i > 0 && i < LENGTH && i % 6 == 3) {
						s = Blocks.SEA_LANTERN.defaultBlockState();   // 右の壁に、灯り(暗くならないように)
					}
					level.setBlock(p.above(h), s, 2);
				}
			}
		}
		for (int i = 1; i < LENGTH; i++) {
			level.setBlock(floor.relative(f, i).above(), rail, 2);
		}

		// 特殊ブロック + 上のボタン + 手前の看板
		Direction front = f.getOpposite();
		level.setBlock(station, ModTrolley.STATION.defaultBlockState().setValue(TrolleyStationBlock.FACING, front), 2);
		level.setBlock(station.above(), Blocks.STONE_BUTTON.defaultBlockState()
				.setValue(ButtonBlock.FACE, AttachFace.FLOOR).setValue(ButtonBlock.FACING, front), 2);
		BlockPos signPos = station.relative(front);
		level.setBlock(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, front), 2);

		// マップを、位置ごとに用意する(中身は、乗るときに書く)
		if (level.getBlockEntity(station) instanceof TrolleyStationBlockEntity be) {
			be.maps = new int[LENGTH];
			for (int i = 1; i < LENGTH; i++) {
				ItemStack stack = MapItem.create(level, station.getX(), station.getZ(), (byte) 0, false, false);
				MapId id = stack.get(DataComponents.MAP_ID);
				be.maps[i] = id == null ? 0 : id.id();
			}
			be.song = "";
			be.setChanged();
		}
		updateSign(level, station);
		level.sendBlockUpdated(station, level.getBlockState(station), level.getBlockState(station), 3);
	}

	/** 手前の看板に、いま選ばれている曲を表示する。 */
	static void updateSign(ServerLevel level, BlockPos station) {
		BlockState st = level.getBlockState(station);
		if (!(st.getBlock() instanceof TrolleyStationBlock) || !(level.getBlockEntity(station) instanceof TrolleyStationBlockEntity be)) {
			return;
		}
		BlockPos signPos = station.relative(st.getValue(TrolleyStationBlock.FACING));
		BlockEntity sbe = level.getBlockEntity(signPos);
		if (!(sbe instanceof SignBlockEntity sign)) {
			return;
		}
		String name = be.song.isEmpty() ? "(未選択)" : be.song;
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}
		Component[] lines = new Component[4];
		lines[0] = Component.literal("▶ 選んだ曲");
		for (int k = 0; k < 3; k++) {
			int from = k * 9;
			lines[k + 1] = Component.literal(from < name.length() ? name.substring(from, Math.min(name.length(), from + 9)) : "");
		}
		SignText text = new SignText(List.of(lines), List.of(lines), DyeColor.BLACK, false);
		sign.setText(text, SignTextSlot.FRONT);
		sign.setWaxed(true);
		sign.setChanged();
		level.sendBlockUpdated(signPos, level.getBlockState(signPos), level.getBlockState(signPos), 3);
	}

	/**
	 * 位置 i の1枚の断面(床 5、壁 左右 3 の高さ、真ん中にレール、間は空ける)を、作る(NORMAL)・行き止まりの壁にする(CAP)・消す(CLEAR)。
	 * 動画の長さに合わせて通路を先へ伸ばすときと、伸ばした部分を消すときに使う。
	 */
	static void slice(ServerLevel level, BlockPos station, Direction f, int i, int mode) {
		Direction l = f.getCounterClockWise();
		BlockPos floor = station.below().relative(f, i);
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState rail = Blocks.RAIL.defaultBlockState()
				.setValue(RailBlock.SHAPE, f.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);
		for (int lat = -2; lat <= 2; lat++) {
			BlockPos p = floor.relative(l, lat);
			level.setBlock(p, mode == CLEAR ? air : brick, 2);
			boolean wallCol = Math.abs(lat) == 2;
			for (int h = 1; h <= 4; h++) {
				BlockState s;
				if (mode == CLEAR) {
					s = air;
				} else if (mode == CAP) {
					s = h <= 3 ? brick : air;
				} else if (wallCol && h <= 3) {
					s = lat == -2 && h == 3 && i % 6 == 3 ? Blocks.SEA_LANTERN.defaultBlockState() : brick;
				} else if (lat == 0 && h == 1) {
					s = rail;
				} else {
					s = air;
				}
				level.setBlock(p.above(h), s, 2);
			}
		}
	}
}
