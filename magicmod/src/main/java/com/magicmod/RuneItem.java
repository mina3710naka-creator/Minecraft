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

/** 刻印(右クリックで刻む=オーラスロットにセット)。 */
public class RuneItem extends Item {

	public final Runes rune;

	public RuneItem(Properties p, Runes rune) {
		super(p);
		this.rune = rune;
	}

	@Override
	public boolean isFoil(ItemStack s) {
		return true;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			if (Status.engrave(sp, rune)) {
				player.getItemInHand(hand).shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack s, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
		Balance.RuneRow r = rune.row();
		out.accept(Component.literal(r.effect() + " (+" + Math.round(r.value() * 100) + "%)").withStyle(ChatFormatting.AQUA));
		out.accept(Component.literal("右クリックで刻む(常時発動・MP消費なし)").withStyle(ChatFormatting.DARK_GRAY));
	}
}
