package com.magicmod;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 魔法の本・杖のデータ(アイテムの custom_data に保存)。
 * 本: {el, lv, exp, slots:[魔法ID...]}。杖: {mm_books:[本...], mm_sel, mm_ench:{...}}。
 * 合成魔法は "syn:ID" で 2 枠使い、続く枠に "|" が入る。
 */
public final class Books {

	public static final int[] EXP_TO_LEVEL = { 0, 100, 300, 700, 1500 };

	private Books() {
	}

	public static CompoundTag data(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d == null ? new CompoundTag() : d.copyTag();
	}

	public static void setData(ItemStack s, CompoundTag t) {
		CustomData.set(DataComponents.CUSTOM_DATA, s, t);
	}

	// ---------------------------------------------------------------------------------------------
	// 本

	public static CompoundTag newBook(Element e) {
		CompoundTag b = new CompoundTag();
		b.putString("el", e.id);
		b.putInt("lv", 1);
		b.putInt("exp", 0);
		ListTag slots = new ListTag();
		for (int i = 0; i < slotCount(1); i++) {
			slots.add(StringTag.valueOf(""));
		}
		b.put("slots", slots);
		return b;
	}

	/** 本アイテムのデータ(無ければ Lv1 の空の本)。 */
	public static CompoundTag bookOf(ItemStack s) {
		if (!(s.getItem() instanceof BookItem bi)) {
			return new CompoundTag();
		}
		CompoundTag t = data(s);
		CompoundTag b = t.getCompound("book").orElse(null);
		if (b == null) {
			b = newBook(bi.element);
		}
		return b;
	}

	public static void setBook(ItemStack s, CompoundTag book) {
		CompoundTag t = data(s);
		t.put("book", book);
		setData(s, t);
	}

	public static ItemStack toItem(CompoundTag book) {
		Element e = element(book);
		ItemStack s = new ItemStack(ModItems.book(e));
		setBook(s, book);
		return s;
	}

	public static Element element(CompoundTag book) {
		Element e = Element.byId(book.getStringOr("el", "fire"));
		return e == null ? Element.FIRE : e;
	}

	public static int level(CompoundTag book) {
		return Math.max(1, Math.min(5, book.getIntOr("lv", 1)));
	}

	/** スロット数: Lv1=2 … Lv5=6(設計書 2.1)。 */
	public static int slotCount(int lv) {
		return lv + 1;
	}

	public static List<String> slots(CompoundTag book) {
		List<String> l = new ArrayList<>();
		for (Tag x : book.getListOrEmpty("slots")) {
			l.add(x.asString().orElse(""));
		}
		int n = slotCount(level(book));
		while (l.size() < n) {
			l.add("");
		}
		while (l.size() > n) {
			l.remove(l.size() - 1);
		}
		return l;
	}

	public static void setSlots(CompoundTag book, List<String> slots) {
		ListTag l = new ListTag();
		for (String s : slots) {
			l.add(StringTag.valueOf(s));
		}
		book.put("slots", l);
	}

	/** 本に EXP を足す。レベルが上がったら新しいレベルを返す(上がらなければ 0)。 */
	public static int addExp(CompoundTag book, int amount) {
		int lv = level(book);
		int exp = book.getIntOr("exp", 0) + amount;
		book.putInt("exp", exp);
		int newLv = lv;
		while (newLv < 5 && exp >= EXP_TO_LEVEL[newLv]) {
			newLv++;
		}
		if (newLv != lv) {
			book.putInt("lv", newLv);
			setSlots(book, slots(book));
			return newLv;
		}
		return 0;
	}

	// ---------------------------------------------------------------------------------------------
	// 杖

	public static boolean isCaster(ItemStack s) {
		return s.getItem() instanceof WandItem || isFused(s);
	}

	public static boolean isFused(ItemStack s) {
		return data(s).getBooleanOr("mm_fused", false);
	}

	public static List<CompoundTag> wandBooks(ItemStack wand) {
		List<CompoundTag> l = new ArrayList<>();
		for (Tag x : data(wand).getListOrEmpty("mm_books")) {
			if (x instanceof CompoundTag c) {
				l.add(c);
			}
		}
		return l;
	}

	public static void setWandBooks(ItemStack wand, List<CompoundTag> books) {
		CompoundTag t = data(wand);
		ListTag l = new ListTag();
		books.forEach(l::add);
		t.put("mm_books", l);
		setData(wand, t);
	}

	public static int wandCapacity(ItemStack wand) {
		int base;
		if (wand.getItem() instanceof WandItem w) {
			base = w.tier;
		} else {
			base = data(wand).getIntOr("mm_tier", 1);
		}
		return base + (wandEnch(wand, "slot") > 0 ? 1 : 0);
	}

	public static int wandEnch(ItemStack wand, String id) {
		return data(wand).getCompoundOrEmpty("mm_ench").getIntOr(id, 0);
	}

	public static void setWandEnch(ItemStack wand, String id, int lv) {
		CompoundTag t = data(wand);
		CompoundTag e = t.getCompoundOrEmpty("mm_ench");
		e.putInt(id, lv);
		t.put("mm_ench", e);
		setData(wand, t);
	}

	public static int enchCount(ItemStack wand) {
		CompoundTag e = data(wand).getCompoundOrEmpty("mm_ench");
		int n = 0;
		for (String k : e.keySet()) {
			if (e.getIntOr(k, 0) > 0) {
				n++;
			}
		}
		return n;
	}

	public static int enchCapacity(ItemStack wand) {
		if (wand.getItem() instanceof WandItem w) {
			return w.tier;
		}
		return Math.min(8, data(wand).getIntOr("mm_enchcap", 2));
	}

	/** 杖で選べる魔法の並び(本の順 → スロット順)。 */
	public record Entry(int book, int slot, String id, Element bookElement, int bookLevel) {
	}

	public static List<Entry> entries(ItemStack wand) {
		List<Entry> out = new ArrayList<>();
		List<CompoundTag> books = wandBooks(wand);
		for (int b = 0; b < books.size(); b++) {
			CompoundTag bk = books.get(b);
			List<String> sl = slots(bk);
			for (int i = 0; i < sl.size(); i++) {
				String id = sl.get(i);
				if (!id.isEmpty() && !id.equals("|")) {
					out.add(new Entry(b, i, id, element(bk), level(bk)));
				}
			}
		}
		return out;
	}

	public static int selected(ItemStack wand) {
		return data(wand).getIntOr("mm_sel", 0);
	}

	public static void setSelected(ItemStack wand, int i) {
		CompoundTag t = data(wand);
		t.putInt("mm_sel", i);
		setData(wand, t);
	}

	public static @Nullable Entry selectedEntry(ItemStack wand) {
		List<Entry> l = entries(wand);
		if (l.isEmpty()) {
			return null;
		}
		int i = Math.floorMod(selected(wand), l.size());
		return l.get(i);
	}

	/** ID から実際の魔法(合成魔法ならプレイヤーの合成レシピから組み立てる)。 */
	public static @Nullable Spell resolve(@Nullable Player p, String id) {
		if (id.startsWith("syn:")) {
			if (p == null) {
				return null;
			}
			PlayerMagic.Synth sy = PlayerMagic.of(p).synths.get(id.substring(4));
			return sy == null ? null : Spells.build(sy);
		}
		return Spells.get(id);
	}

	/** 杖スロット 1 番目の本の属性(エレメンタル・魔封じの型の属性、設計書 13.4.1)。 */
	public static @Nullable Element firstElement(ItemStack wand) {
		if (isFused(wand)) {
			Element e = Element.byId(data(wand).getStringOr("mm_fel", ""));
			if (e != null) {
				return e;
			}
		}
		List<CompoundTag> b = wandBooks(wand);
		return b.isEmpty() ? null : element(b.get(0));
	}

	/** 魔法を撃って得た EXP を、杖の中の同じ属性の本に入れる。 */
	public static int giveExp(ItemStack wand, Element e, int amount) {
		List<CompoundTag> books = wandBooks(wand);
		for (CompoundTag b : books) {
			if (element(b) == e) {
				int up = addExp(b, amount);
				setWandBooks(wand, books);
				return up;
			}
		}
		return 0;
	}
}
