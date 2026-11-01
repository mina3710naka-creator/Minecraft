package com.magicmod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** 合成魔法の巻物(合成炉で作り、魔法テーブルの「装着」でベース属性の本に入れる。本の枠を 2 つ使う)。 */
public class SynthScrollItem extends Item {

	public SynthScrollItem(Properties p) {
		super(p.stacksTo(1));
	}

	public static ItemStack make(String base, List<String> subs, int tier, String name) {
		ItemStack s = new ItemStack(ModItems.SYNTH_SCROLL);
		CompoundTag d = new CompoundTag();
		d.putString("base", base);
		ListTag l = new ListTag();
		subs.forEach(x -> l.add(StringTag.valueOf(x)));
		d.put("subs", l);
		d.putInt("tier", tier);
		d.putString("name", name);
		Books.setData(s, d);
		s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("◆" + name).withStyle(ChatFormatting.LIGHT_PURPLE));
		return s;
	}

	public static List<String> lines(CompoundTag d) {
		List<String> out = new ArrayList<>();
		out.add("§7ベース: §f" + Net.spellName(d.getStringOr("base", "")));
		for (var x : d.getListOrEmpty("subs")) {
			out.add("§7サブ: §f" + Net.spellName(x.asString().orElse("")));
		}
		out.add("§7素材ランク: " + d.getIntOr("tier", 1));
		return out;
	}

	@Override
	public boolean isFoil(ItemStack s) {
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack s, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
		for (String l : lines(Books.data(s))) {
			out.accept(Component.literal(l));
		}
		out.accept(Component.literal("魔法テーブルの「装着」で本に入れる(2枠)").withStyle(ChatFormatting.DARK_GRAY));
	}
}
