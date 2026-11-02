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

/** 使い魔契約書(右クリックで契約する)。 */
public class ContractItem extends Item {

	public final Familiars.Kind kind;

	public ContractItem(Properties p, Familiars.Kind kind) {
		super(p);
		this.kind = kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			if (Familiars.contract(sp, kind)) {
				player.getItemInHand(hand).shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack s, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
		Balance.FamiliarRow r = kind.row();
		out.accept(Component.literal(r.role() + ": " + r.special()).withStyle(ChatFormatting.AQUA));
		out.accept(Component.literal("HP " + Math.round(r.hp()) + " / 攻撃 " + Math.round(r.attack()) + " / 維持MP " + r.upkeep() + "/秒")
				.withStyle(ChatFormatting.GRAY));
		out.accept(Component.literal("右クリックで契約、[H] で召喚/送還").withStyle(ChatFormatting.DARK_GRAY));
	}
}
