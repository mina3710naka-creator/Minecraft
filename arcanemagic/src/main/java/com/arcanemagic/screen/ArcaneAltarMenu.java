package com.arcanemagic.screen;

import com.arcanemagic.component.SpellbookData;
import com.arcanemagic.component.SpellbookDataHelper;
import com.arcanemagic.component.WandData;
import com.arcanemagic.component.WandDataHelper;
import com.arcanemagic.item.ModItems;
import com.arcanemagic.item.SpellScrollItem;
import com.arcanemagic.item.SpellbookItem;
import com.arcanemagic.item.WandItem;
import com.arcanemagic.spell.SpellRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * アーケインの祭壇のGUIロジック。
 * スロット0=杖/魔導書、スロット1=巻物/クリスタル、スロット2=結果(取り出すと消費される)。
 * バニラの「鍛冶台」と同じ「材料を置く→結果スロットから取り出すと確定」方式。
 */
public class ArcaneAltarMenu extends AbstractContainerMenu {

	public static final int FOCUS_SLOT = 0;
	public static final int CATALYST_SLOT = 1;
	public static final int RESULT_SLOT = 2;

	public static final int FOCUS_X = 40;
	public static final int FOCUS_Y = 20;
	public static final int CATALYST_X = 40;
	public static final int CATALYST_Y = 50;
	public static final int RESULT_X = 116;
	public static final int RESULT_Y = 35;

	private static final int CRYSTAL_COST = 3;
	private static final int PLAYER_INV_START = 3;
	private static final int PLAYER_INV_END = 3 + 36;

	private final Container input = new SimpleContainer(2) {
		@Override
		public void setChanged() {
			super.setChanged();
			updateResult();
		}
	};
	private final Container output = new SimpleContainer(1);

	private int xpCost = 0;
	private Component reason = Component.translatable("arcanemagic.altar.reason.empty");

	public ArcaneAltarMenu(int syncId, Inventory playerInventory) {
		super(ModMenus.ARCANE_ALTAR, syncId);

		this.addSlot(new Slot(input, FOCUS_SLOT, FOCUS_X, FOCUS_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof WandItem || stack.getItem() instanceof SpellbookItem;
			}
		});
		this.addSlot(new Slot(input, CATALYST_SLOT, CATALYST_X, CATALYST_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof SpellScrollItem || stack.is(ModItems.ARCANE_CRYSTAL);
			}
		});
		this.addSlot(new AltarOutputSlot(output, RESULT_SLOT, RESULT_X, RESULT_Y));

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	private void updateResult() {
		ItemStack focus = input.getItem(FOCUS_SLOT);
		ItemStack catalyst = input.getItem(CATALYST_SLOT);
		output.setItem(0, ItemStack.EMPTY);
		xpCost = 0;
		reason = Component.translatable("arcanemagic.altar.reason.empty");

		if (focus.isEmpty() || catalyst.isEmpty()) {
			return;
		}

		if (focus.getItem() instanceof WandItem) {
			updateWandResult(focus, catalyst);
		} else if (focus.getItem() instanceof SpellbookItem) {
			updateSpellbookResult(focus, catalyst);
		}
	}

	private void updateWandResult(ItemStack focus, ItemStack catalyst) {
		WandData data = WandDataHelper.get(focus);

		if (catalyst.getItem() instanceof SpellScrollItem scroll) {
			ItemStack result = focus.copyWithCount(1);
			WandDataHelper.set(result, data.withSpell(scroll.getSpellId()));
			xpCost = 1 + data.level();
			output.setItem(0, result);
			reason = Component.translatable("arcanemagic.altar.preview.set_spell",
					spellName(scroll.getSpellId()), focus.getHoverName().getString());
		} else if (catalyst.is(ModItems.ARCANE_CRYSTAL)) {
			if (data.isMaxLevel()) {
				reason = Component.translatable("arcanemagic.altar.reason.max_level", WandData.MAX_LEVEL);
			} else if (catalyst.getCount() < CRYSTAL_COST) {
				reason = Component.translatable("arcanemagic.altar.reason.not_enough_crystal", CRYSTAL_COST);
			} else {
				int newLevel = data.level() + 1;
				ItemStack result = focus.copyWithCount(1);
				WandDataHelper.set(result, data.withLevel(newLevel));
				xpCost = newLevel;
				output.setItem(0, result);
				reason = Component.translatable("arcanemagic.altar.preview.upgrade", focus.getHoverName().getString(), newLevel);
			}
		}
	}

	private void updateSpellbookResult(ItemStack focus, ItemStack catalyst) {
		SpellbookData data = SpellbookDataHelper.get(focus);

		if (catalyst.getItem() instanceof SpellScrollItem scroll) {
			if (data.isFull()) {
				reason = Component.translatable("arcanemagic.altar.reason.book_full");
			} else {
				int newSlotNumber = data.spells().size() + 1;
				ItemStack result = focus.copyWithCount(1);
				SpellbookDataHelper.set(result, data.withAdded(scroll.getSpellId()));
				xpCost = 1;
				output.setItem(0, result);
				reason = Component.translatable("arcanemagic.altar.preview.learn",
						spellName(scroll.getSpellId()), focus.getHoverName().getString(), newSlotNumber);
			}
		}
	}

	private static String spellName(Identifier spellId) {
		return SpellRegistry.get(spellId).map(spell -> spell.displayName().getString()).orElse("?");
	}

	public int getXpCost() {
		return xpCost;
	}

	public Component getReasonText() {
		return reason;
	}

	private class AltarOutputSlot extends Slot {
		AltarOutputSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public void onTake(Player player, ItemStack stack) {
			if (!player.level().isClientSide && xpCost > 0 && player.experienceLevel >= xpCost) {
				player.experienceLevel -= xpCost;

				ItemStack catalyst = input.getItem(CATALYST_SLOT);
				if (catalyst.is(ModItems.ARCANE_CRYSTAL)) {
					catalyst.shrink(CRYSTAL_COST);
				} else {
					catalyst.shrink(1);
				}
				input.setItem(FOCUS_SLOT, ItemStack.EMPTY);
				input.setChanged();
			}
			super.onTake(player, stack);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);

		if (slot != null && slot.hasItem()) {
			ItemStack stackInSlot = slot.getItem();
			result = stackInSlot.copy();

			if (index == RESULT_SLOT) {
				if (!this.moveItemStackTo(stackInSlot, PLAYER_INV_START, PLAYER_INV_END, true)) {
					return ItemStack.EMPTY;
				}
				slot.onQuickCraft(stackInSlot, result);
			} else if (index == FOCUS_SLOT || index == CATALYST_SLOT) {
				if (!this.moveItemStackTo(stackInSlot, PLAYER_INV_START, PLAYER_INV_END, true)) {
					return ItemStack.EMPTY;
				}
			} else if (index < PLAYER_INV_END) {
				if (!this.moveItemStackTo(stackInSlot, FOCUS_SLOT, RESULT_SLOT, false)) {
					return ItemStack.EMPTY;
				}
			}

			if (stackInSlot.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!player.level().isClientSide) {
			this.clearContainer(player, input);
		}
	}
}
