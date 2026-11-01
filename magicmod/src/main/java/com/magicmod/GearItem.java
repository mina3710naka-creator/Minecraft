package com.magicmod;

import java.util.Map;
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

/** 帽子・ペンダント・手袋・腕輪(右クリックで装備スロットに付ける)。 */
public class GearItem extends Item {

	public final Gear.Kind kind;

	public GearItem(Properties p, Gear.Kind kind) {
		super(p);
		this.kind = kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			Status.equipGear(sp, hand);
		}
		return InteractionResult.SUCCESS;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack s, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
		out.accept(Component.literal("部位: " + kind.slot.ja).withStyle(ChatFormatting.GRAY));
		for (Map.Entry<Gear.Stat, Float> e : kind.stats.entrySet()) {
			String v = e.getKey().percent ? "+" + Math.round(e.getValue() * 100) + "%" : "+" + trim(e.getValue());
			out.accept(Component.literal(" " + e.getKey().ja + " " + v).withStyle(ChatFormatting.AQUA));
		}
		if (kind.only != null) {
			out.accept(Component.literal(kind.only.ja + "固有装備(均衡型も装備可)").withStyle(ChatFormatting.GOLD));
		}
		int power = Books.data(s).getIntOr("power", 0);
		int cost = Books.data(s).getIntOr("cost", 0);
		if (power > 0) {
			out.accept(Component.literal(" 魔力強化 " + Enchants.roman(power)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (cost > 0) {
			out.accept(Component.literal(" MP回復強化 " + Enchants.roman(cost)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		out.accept(Component.literal("右クリックで装備").withStyle(ChatFormatting.DARK_GRAY));
	}

	static String trim(float f) {
		return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.valueOf(f);
	}
}
