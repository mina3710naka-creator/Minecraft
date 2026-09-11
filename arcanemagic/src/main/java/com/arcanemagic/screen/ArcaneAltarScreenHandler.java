package com.arcanemagic.screen;

import com.arcanemagic.component.ModComponents;
import com.arcanemagic.component.SpellbookData;
import com.arcanemagic.component.WandData;
import com.arcanemagic.item.ModItems;
import com.arcanemagic.item.SpellScrollItem;
import com.arcanemagic.item.SpellbookItem;
import com.arcanemagic.item.WandItem;
import com.arcanemagic.spell.SpellRegistry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * アーケインの祭壇のGUIロジック。
 * スロット0=杖/魔導書、スロット1=巻物/クリスタル、スロット2=結果(取り出すと消費される)。
 * バニラの「鍛冶台」と同じ「材料を置く→結果スロットから取り出すと確定」方式。
 */
public class ArcaneAltarScreenHandler extends ScreenHandler {

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

	private final Inventory input = new SimpleInventory(2) {
		@Override
		public void markDirty() {
			super.markDirty();
			updateResult();
		}
	};
	private final Inventory output = new SimpleInventory(1);

	private int xpCost = 0;
	private Text reason = Text.translatable("arcanemagic.altar.reason.empty");

	public ArcaneAltarScreenHandler(int syncId, PlayerInventory playerInventory) {
		super(ModScreenHandlers.ARCANE_ALTAR, syncId);

		this.addSlot(new Slot(input, FOCUS_SLOT, FOCUS_X, FOCUS_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof WandItem || stack.getItem() instanceof SpellbookItem;
			}
		});
		this.addSlot(new Slot(input, CATALYST_SLOT, CATALYST_X, CATALYST_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof SpellScrollItem || stack.isOf(ModItems.ARCANE_CRYSTAL);
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
		ItemStack focus = input.getStack(FOCUS_SLOT);
		ItemStack catalyst = input.getStack(CATALYST_SLOT);
		output.setStack(0, ItemStack.EMPTY);
		xpCost = 0;
		reason = Text.translatable("arcanemagic.altar.reason.empty");

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
		WandData data = focus.getOrDefault(ModComponents.WAND_DATA, WandData.EMPTY);

		if (catalyst.getItem() instanceof SpellScrollItem scroll) {
			ItemStack result = focus.copyWithCount(1);
			result.set(ModComponents.WAND_DATA, data.withSpell(scroll.getSpellId()));
			xpCost = 1 + data.level();
			output.setStack(0, result);
			reason = Text.translatable("arcanemagic.altar.preview.set_spell",
					spellName(scroll.getSpellId()), focus.getName().getString());
		} else if (catalyst.isOf(ModItems.ARCANE_CRYSTAL)) {
			if (data.isMaxLevel()) {
				reason = Text.translatable("arcanemagic.altar.reason.max_level", WandData.MAX_LEVEL);
			} else if (catalyst.getCount() < CRYSTAL_COST) {
				reason = Text.translatable("arcanemagic.altar.reason.not_enough_crystal", CRYSTAL_COST);
			} else {
				int newLevel = data.level() + 1;
				ItemStack result = focus.copyWithCount(1);
				result.set(ModComponents.WAND_DATA, data.withLevel(newLevel));
				xpCost = newLevel;
				output.setStack(0, result);
				reason = Text.translatable("arcanemagic.altar.preview.upgrade", focus.getName().getString(), newLevel);
			}
		}
	}

	private void updateSpellbookResult(ItemStack focus, ItemStack catalyst) {
		SpellbookData data = focus.getOrDefault(ModComponents.SPELLBOOK_DATA, SpellbookData.EMPTY);

		if (catalyst.getItem() instanceof SpellScrollItem scroll) {
			if (data.isFull()) {
				reason = Text.translatable("arcanemagic.altar.reason.book_full");
			} else {
				int newSlotNumber = data.spells().size() + 1;
				ItemStack result = focus.copyWithCount(1);
				result.set(ModComponents.SPELLBOOK_DATA, data.withAdded(scroll.getSpellId()));
				xpCost = 1;
				output.setStack(0, result);
				reason = Text.translatable("arcanemagic.altar.preview.learn",
						spellName(scroll.getSpellId()), focus.getName().getString(), newSlotNumber);
			}
		}
	}

	private static String spellName(net.minecraft.util.Identifier spellId) {
		return SpellRegistry.get(spellId).map(spell -> spell.displayName().getString()).orElse("?");
	}

	public int getXpCost() {
		return xpCost;
	}

	public Text getReasonText() {
		return reason;
	}

	private class AltarOutputSlot extends Slot {
		AltarOutputSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}

		@Override
		public void onTakeItem(PlayerEntity player, ItemStack stack) {
			if (!player.getWorld().isClient && xpCost > 0 && player.experienceLevel >= xpCost) {
				player.experienceLevel -= xpCost;

				ItemStack catalyst = input.getStack(CATALYST_SLOT);
				if (catalyst.isOf(ModItems.ARCANE_CRYSTAL)) {
					catalyst.decrement(CRYSTAL_COST);
				} else {
					catalyst.decrement(1);
				}
				input.setStack(FOCUS_SLOT, ItemStack.EMPTY);
				input.markDirty();
			}
			super.onTakeItem(player, stack);
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);

		if (slot != null && slot.hasStack()) {
			ItemStack stackInSlot = slot.getStack();
			result = stackInSlot.copy();

			if (index == RESULT_SLOT) {
				if (!this.insertItem(stackInSlot, PLAYER_INV_START, PLAYER_INV_END, true)) {
					return ItemStack.EMPTY;
				}
				slot.onQuickTransfer(stackInSlot, result);
			} else if (index == FOCUS_SLOT || index == CATALYST_SLOT) {
				if (!this.insertItem(stackInSlot, PLAYER_INV_START, PLAYER_INV_END, true)) {
					return ItemStack.EMPTY;
				}
			} else if (index < PLAYER_INV_END) {
				if (!this.insertItem(stackInSlot, FOCUS_SLOT, RESULT_SLOT, false)) {
					return ItemStack.EMPTY;
				}
			}

			if (stackInSlot.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return result;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		if (!player.getWorld().isClient) {
			this.dropInventory(player, input);
		}
	}
}
