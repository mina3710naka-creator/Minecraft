package com.effectmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 付与台の中身: 効果を付けるアイテム1つ + プレイヤーの持ち物。 */
public class BenchMenu extends AbstractContainerMenu {

	public static final int MAX_EFFECTS = 3;
	public static final int SLOT_X = 12;
	public static final int SLOT_Y = 22;
	public static final int INV_X = 47;
	public static final int INV_Y = 184;

	private final Container input = new SimpleContainer(1);
	private final ContainerLevelAccess access;

	public BenchMenu(int id, Inventory inv, ContainerLevelAccess access) {
		super(ModContent.BENCH_MENU, id);
		this.access = access;
		addSlot(new Slot(input, 0, SLOT_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack s) {
				return !s.isEmpty() && s.getMaxStackSize() == 1;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addStandardInventorySlots(inv, INV_X, INV_Y);
	}

	public ItemStack target() {
		return input.getItem(0);
	}

	/** クライアントからの操作(mode 0 = 文章で付与, 1 = 全部消す, 2 = text 番目の効果を消す)。 */
	public void apply(ServerPlayer p, int mode, String text) {
		ItemStack st = input.getItem(0);
		if (st.isEmpty()) {
			reply(p, "効果を付けるアイテムを左のスロットに入れてください", false);
			return;
		}
		List<Entry> cur = new ArrayList<>(st.getOrDefault(ModContent.EFFECTS, List.<Entry>of()));
		if (mode == 1) {
			if (cur.isEmpty()) {
				reply(p, "このアイテムに効果は付いていません", false);
				return;
			}
			st.remove(ModContent.EFFECTS);
			st.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
			changed(p, st);
			reply(p, "効果をすべて消しました", true);
			return;
		}
		if (mode == 2) {
			int idx;
			try {
				idx = Integer.parseInt(text.trim());
			} catch (NumberFormatException e) {
				return;
			}
			if (idx < 0 || idx >= cur.size()) {
				return;
			}
			Entry gone = cur.remove(idx);
			if (cur.isEmpty()) {
				st.remove(ModContent.EFFECTS);
				st.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
			} else {
				st.set(ModContent.EFFECTS, List.copyOf(cur));
			}
			changed(p, st);
			reply(p, "消しました: " + Parser.label(gone), true);
			return;
		}
		if (cur.size() >= MAX_EFFECTS) {
			reply(p, "1つのアイテムに付けられる効果は " + MAX_EFFECTS + " 個までです(「全削除」で消せます)", false);
			return;
		}
		Parser.Result r = Parser.parse(text, st);
		if (!r.ok()) {
			reply(p, r.error(), false);
			return;
		}
		int cost = Parser.cost(r.entry());
		boolean free = p.hasInfiniteMaterials();
		if (!free && p.experienceLevel < cost) {
			reply(p, "経験値レベルが足りません(必要 " + cost + " / 現在 " + p.experienceLevel + ")", false);
			return;
		}
		if (!free) {
			p.giveExperienceLevels(-cost);
		}
		cur.add(r.entry());
		st.set(ModContent.EFFECTS, List.copyOf(cur));
		st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		changed(p, st);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, 1f);
		reply(p, "付与しました: " + Parser.label(r.entry()) + (free ? "" : "(Lv-" + cost + ")"), true);
	}

	private void changed(ServerPlayer p, ItemStack st) {
		input.setItem(0, st);
		input.setChanged();
		broadcastChanges();
	}

	private static void reply(ServerPlayer p, String msg, boolean ok) {
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new Net.ResultPayload(msg, ok));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot.hasItem()) {
			ItemStack s = slot.getItem();
			result = s.copy();
			if (index == 0) {
				if (!moveItemStackTo(s, 1, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (slots.get(0).mayPlace(s) && !slots.get(0).hasItem()) {
				ItemStack one = s.split(1);
				slots.get(0).set(one);
			} else {
				return ItemStack.EMPTY;
			}
			if (s.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, ModContent.BENCH);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((lv, pos) -> clearContainer(player, input));
	}

	public static Component title() {
		return Component.translatable("block.effectmod.effect_bench");
	}
}
