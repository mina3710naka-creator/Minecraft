package com.lampvideo.display;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** プロジェクターの状態。ディスプレイと同じ(PC に接続して、再生する)。違うのは、描画だけ(壁に映す)。 */
public class ProjectorBlockEntity extends DisplayBlockEntity {

	public ProjectorBlockEntity(BlockPos pos, BlockState state) {
		super(ModDisplay.PROJECTOR_ENTITY, pos, state);
	}
}
