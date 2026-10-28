package com.lampvideo.trolley;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** トロッコムービー。ブロックに使うと、向いている方向に、長さ 200 の通路(石レンガの壁と床、天井なし、幅 3、真ん中にレール)を作る。 */
public class TrolleyMovieItem extends Item {

	public TrolleyMovieItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (context.getLevel() instanceof ServerLevel level) {
			Direction f = player.getDirection();
			BlockPos floor = context.getClickedPos();
			TrolleyCorridor.build(level, floor, f);
			player.sendSystemMessage(Component.literal("[トロッコムービー] 通路を作りました(長さ " + TrolleyCorridor.LENGTH
					+ ")。手前の特殊ブロックを右クリックして曲を選び、上のボタンを押すと、トロッコに乗れます"));
		}
		return InteractionResult.SUCCESS;
	}
}
