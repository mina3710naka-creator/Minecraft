package com.magicmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 解呪の聖水: 自分の悪い効果(ドゥームカース含む)を消す。 */
public class PurifyItem extends Item {

	public PurifyItem(Properties p) {
		super(p.stacksTo(16));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel sl) {
			Ctx.cleanse(player);
			sl.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
			level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, player.getSoundSource(), 1f, 1f);
			if (!player.isCreative()) {
				player.getItemInHand(hand).shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
