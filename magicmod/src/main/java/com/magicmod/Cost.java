package com.magicmod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 素材コスト(アイテム × 個数 + 経験値レベル)。 */
public final class Cost {

	private final Map<Item, Integer> items = new LinkedHashMap<>();
	private int xp;

	public static Cost of() {
		return new Cost();
	}

	public Cost add(Item item, int count) {
		if (count > 0) {
			items.merge(item, count, Integer::sum);
		}
		return this;
	}

	public Cost add(List<ItemStack> stacks) {
		for (ItemStack s : stacks) {
			add(s.getItem(), s.getCount());
		}
		return this;
	}

	public Cost xp(int levels) {
		xp += levels;
		return this;
	}

	/** 個数を倍率で変える(合成の熟達など)。 */
	public Cost scale(float f) {
		items.replaceAll((k, v) -> Math.max(1, Math.round(v * f)));
		return this;
	}

	public static int count(Player p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	public boolean has(Player p) {
		if (p.isCreative()) {
			return true;
		}
		if (p.experienceLevel < xp) {
			return false;
		}
		for (var e : items.entrySet()) {
			if (count(p, e.getKey()) < e.getValue()) {
				return false;
			}
		}
		return true;
	}

	public boolean take(Player p) {
		if (!has(p)) {
			return false;
		}
		if (p.isCreative()) {
			return true;
		}
		for (var e : items.entrySet()) {
			int left = e.getValue();
			for (int i = 0; i < p.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack s = p.getInventory().getItem(i);
				if (s.is(e.getKey())) {
					int n = Math.min(left, s.getCount());
					s.shrink(n);
					left -= n;
				}
			}
		}
		if (xp > 0) {
			p.giveExperienceLevels(-xp);
		}
		return true;
	}

	public List<String> lines(Player p) {
		List<String> out = new ArrayList<>();
		for (var e : items.entrySet()) {
			int have = count(p, e.getKey());
			out.add((have >= e.getValue() ? "§a" : "§c") + e.getKey().getDefaultInstance().getHoverName().getString() + " ×" + e.getValue() + " §7(所持 " + have + ")");
		}
		if (xp > 0) {
			out.add((p.experienceLevel >= xp ? "§a" : "§c") + "経験値レベル " + xp);
		}
		if (out.isEmpty()) {
			out.add("§7素材なし");
		}
		return out;
	}

	public String shortText() {
		StringBuilder sb = new StringBuilder();
		for (var e : items.entrySet()) {
			if (!sb.isEmpty()) {
				sb.append(", ");
			}
			sb.append(e.getKey().getDefaultInstance().getHoverName().getString()).append("×").append(e.getValue());
		}
		if (xp > 0) {
			sb.append(sb.isEmpty() ? "" : ", ").append("Lv").append(xp);
		}
		return sb.toString();
	}
}
