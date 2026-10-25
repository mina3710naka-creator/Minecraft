package com.lampvideo.pc;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** ヘッドホン専用スロットの中身。プレイヤーに付けた保存・同期データ(ModPc.HEADPHONE_SLOT)をそのまま読み書きする。 */
public final class HeadphoneContainer implements Container {

	private final Player player;

	public HeadphoneContainer(Player player) {
		this.player = player;
	}

	private ItemStack stack() {
		ItemStack s = player.getAttached(ModPc.HEADPHONE_SLOT);
		return s == null ? ItemStack.EMPTY : s;
	}

	private void set(ItemStack s) {
		player.setAttached(ModPc.HEADPHONE_SLOT, s.isEmpty() ? null : s.copy());
	}

	@Override
	public int getContainerSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return stack().isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return stack();
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack s = stack().copy();
		if (s.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = s.split(amount);
		set(s);
		return out;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack s = stack().copy();
		set(ItemStack.EMPTY);
		return s;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		set(stack);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(Player p) {
		return true;
	}

	@Override
	public void clearContent() {
		set(ItemStack.EMPTY);
	}
}
