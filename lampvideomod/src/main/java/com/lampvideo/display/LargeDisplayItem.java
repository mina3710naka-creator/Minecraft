package com.lampvideo.display;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 大型ディスプレイ(枠なし)。クリックした面に置く(隣り合うと、自動で1枚の大画面につながる)。ふつうに使うと1枚だけ置き、しゃがんで使うと 横7 x 縦4 を一度に置く。
 * 並べる向きは、ディスプレイ単体と同じ(壁なら上が真上、床・天井なら上が北)。
 */
public class LargeDisplayItem extends Item {

	public static final int COLS = 7;
	public static final int ROWS = 4;

	public LargeDisplayItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		Direction face = context.getClickedFace();
		BlockPos base = context.getClickedPos().relative(face);
		// 床・天井に置くときは、画面の上を、プレイヤーが向いている方向にする
		Direction spin = face.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection() : Direction.NORTH;
		Direction right = DisplayGeometry.right(face, spin);
		Direction up = DisplayGeometry.up(face, spin);
		boolean single = player == null || !player.isShiftKeyDown();
		int cols = single ? 1 : COLS;
		int rows = single ? 1 : ROWS;
		BlockPos start = single ? base : base.relative(right.getOpposite(), COLS / 2);

		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				BlockPos p = start.relative(right, col).relative(up, row);
				if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced()) {
					if (player != null) {
						player.sendSystemMessage(Component.literal("大型ディスプレイ(横" + cols + "x縦" + rows
								+ ")を置く場所が足りません。空いている場所に置いてください"));
					}
					return InteractionResult.FAIL;
				}
			}
		}

		BlockState panel = ModDisplay.LARGE_DISPLAY.defaultBlockState().setValue(DisplayBlock.FACING, face)
				.setValue(DisplayBlock.SPIN, spin);
		if (single) {
			panel = DisplayGeometry.adopt(level, start, panel);
		}
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				level.setBlock(start.relative(right, col).relative(up, row), panel, 3);
			}
		}
		if (player == null || !player.isCreative()) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
