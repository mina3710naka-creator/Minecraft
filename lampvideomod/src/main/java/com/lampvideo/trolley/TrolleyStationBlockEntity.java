package com.lampvideo.trolley;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** トロッコムービーの特殊ブロックの記憶: 選んだ曲と、通路の壁に飾るマップの番号(通路の位置 1〜199 の順)。 */
public class TrolleyStationBlockEntity extends BlockEntity {

	public String song = "";
	/** 位置 i(1〜199)のマップの番号。0 番目は使わない。作る前は null。 */
	public int[] maps;

	public TrolleyStationBlockEntity(BlockPos pos, BlockState state) {
		super(ModTrolley.STATION_ENTITY, pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		out.putString("song", song);
		if (maps != null) {
			StringBuilder sb = new StringBuilder();
			for (int i = 1; i < maps.length; i++) {
				if (i > 1) {
					sb.append(',');
				}
				sb.append(maps[i]);
			}
			out.putString("maps", sb.toString());
		}
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		song = in.getStringOr("song", "");
		String m = in.getStringOr("maps", "");
		if (m.isEmpty()) {
			maps = null;
			return;
		}
		String[] parts = m.split(",");
		int[] arr = new int[parts.length + 1];
		try {
			for (int i = 0; i < parts.length; i++) {
				arr[i + 1] = Integer.parseInt(parts[i].trim());
			}
			maps = arr;
		} catch (NumberFormatException e) {
			maps = null;
		}
	}
}
