package com.magicmod;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** 物理武器(カテゴリ別)。投擲武器は右クリックで投げる。 */
public class WeaponItem extends Item {

	public final String category;
	public final boolean throwable;

	public WeaponItem(Properties p, String category, boolean throwable) {
		super(p);
		this.category = category;
		this.throwable = throwable;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (Books.isFused(player.getItemInHand(hand))) {
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				Casting.castHeld(sp, hand);
			}
			return InteractionResult.SUCCESS;
		}
		if (!throwable) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			Techniques.throwWeapon(sp, hand);
		}
		return InteractionResult.SUCCESS;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack s, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
		out.accept(Component.literal("武器カテゴリ: " + Techniques.categoryName(category)).withStyle(ChatFormatting.GRAY));
		if (throwable) {
			out.accept(Component.literal("右クリックで投げる").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
