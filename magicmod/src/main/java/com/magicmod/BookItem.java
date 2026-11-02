package com.magicmod;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** 魔法の本(属性ごと)。Lv1〜5、スロット 2〜6。魔法テーブルで魔法を装着し、杖にセットして使う。 */
public class BookItem extends Item {

	public final Element element;

	public BookItem(Properties p, Element element) {
		super(p);
		this.element = element;
	}

	@Override
	public boolean isFoil(ItemStack s) {
		return Books.level(Books.bookOf(s)) >= 5;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack s, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
		CompoundTag b = Books.bookOf(s);
		describe(b, out);
	}

	public static void describe(CompoundTag b, Consumer<Component> out) {
		int lv = Books.level(b);
		int exp = b.getIntOr("exp", 0);
		String next = lv >= 5 ? "MAX" : exp + " / " + Books.EXP_TO_LEVEL[lv];
		out.accept(Component.literal("本Lv " + lv + "  EXP " + next).withStyle(ChatFormatting.GRAY));
		List<String> slots = Books.slots(b);
		for (int i = 0; i < slots.size(); i++) {
			String id = slots.get(i);
			if (id.equals("|")) {
				continue;
			}
			String name = id.isEmpty() ? "(空き)" : Net.spellName(id);
			out.accept(Component.literal(" [" + (i + 1) + "] " + name).withStyle(id.isEmpty() ? ChatFormatting.DARK_GRAY : Books.element(b).fmt));
		}
	}
}
