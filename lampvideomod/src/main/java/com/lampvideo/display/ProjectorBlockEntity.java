package com.lampvideo.display;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** プロジェクターの状態。ディスプレイと同じ(PC に接続して、再生する)。違うのは、描画だけ(壁に映す)。 */
public class ProjectorBlockEntity extends DisplayBlockEntity {

	public ProjectorBlockEntity(BlockPos pos, BlockState state) {
		super(ModDisplay.PROJECTOR_ENTITY, pos, state);
	}

	private static final int[] SIZES = { 0, 3, 5, 7, 10, 14 };

	/** 映像のサイズを、自動 → 3 → 5 → 7 → 10 → 14 ブロック幅の順に切り替える。 */
	public int cycleSize() {
		int i = 0;
		for (int k = 0; k < SIZES.length; k++) {
			if (SIZES[k] == projWidth) {
				i = k;
			}
		}
		projWidth = SIZES[(i + 1) % SIZES.length];
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
		return projWidth;
	}
}
