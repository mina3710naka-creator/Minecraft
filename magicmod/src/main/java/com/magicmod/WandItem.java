package com.magicmod;

import java.util.List;
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

/** 杖(木→銀→魔鋼→竜骨→星屑)。右クリック(または詠唱キー)で、選んでいる魔法を撃つ。 */
public class WandItem extends Item {

	public final int tier;

	public WandItem(Properties p, int tier) {
		super(p);
		this.tier = tier;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			Casting.castHeld(sp, hand);
		}
		return InteractionResult.SUCCESS;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack s, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
		describe(s, out);
	}

	public static void describe(ItemStack s, Consumer<Component> out) {
		out.accept(Component.literal("本 " + Books.wandBooks(s).size() + " / " + Books.wandCapacity(s) + " 冊   エンチャント "
				+ Books.enchCount(s) + " / " + Books.enchCapacity(s)).withStyle(ChatFormatting.GRAY));
		for (String e : Enchants.WAND_IDS) {
			int lv = Books.wandEnch(s, e);
			if (lv > 0) {
				out.accept(Component.literal(" " + Enchants.name(e) + " " + Enchants.roman(lv)).withStyle(ChatFormatting.AQUA));
			}
		}
		List<Books.Entry> es = Books.entries(s);
		Books.Entry sel = Books.selectedEntry(s);
		for (Books.Entry en : es) {
			boolean on = sel != null && sel.book() == en.book() && sel.slot() == en.slot();
			out.accept(Component.literal((on ? "▶ " : "   ") + Net.spellName(en.id())).withStyle(on ? ChatFormatting.YELLOW : en.bookElement().fmt));
		}
		if (es.isEmpty()) {
			out.accept(Component.literal("魔法テーブルで本をセットしてください").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
