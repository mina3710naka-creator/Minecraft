package com.lampvideo.pc;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** ヘッドホン。右クリックで、ヘッドホン専用スロットに着ける(持ち物画面でも、専用スロットに入れられる)。 */
public class HeadphonesItem extends Item {

	public HeadphonesItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			ItemStack held = player.getItemInHand(hand);
			if (ModPc.isWearingHeadphones(player)) {
				player.sendSystemMessage(Component.literal("ヘッドホンは、すでに着けています(持ち物画面の専用スロット)"));
			} else {
				player.setAttached(ModPc.HEADPHONE_SLOT, held.copyWithCount(1));
				held.shrink(1);
				player.sendSystemMessage(Component.literal("ヘッドホンを着けました"));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
