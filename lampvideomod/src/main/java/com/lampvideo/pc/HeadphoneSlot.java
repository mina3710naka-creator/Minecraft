package com.lampvideo.pc;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** ヘッドホン専用のスロット(持ち物画面の、オフハンドの上)。ヘッドホンだけが入る。 */
public class HeadphoneSlot extends Slot {

	/** 空のときに出す、ヘッドホンの絵(GUI のスプライト)。 */
	public static final Identifier EMPTY_ICON = Identifier.fromNamespaceAndPath("lampvideo", "container/slot/headphones");

	public HeadphoneSlot(Container container, int x, int y) {
		super(container, 0, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return stack.is(ModPc.HEADPHONES);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public Identifier getNoItemIcon() {
		return EMPTY_ICON;
	}
}
