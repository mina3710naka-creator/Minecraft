package com.arcanemagic.item;

import java.util.Optional;

import com.arcanemagic.component.ModComponents;
import com.arcanemagic.component.SpellbookData;
import com.arcanemagic.spell.Spell;
import com.arcanemagic.spell.SpellRegistry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 魔導書: 最大6個の魔法を登録でき、Ctrl+マウスホイール(または専用キー)で
 * 選択中の魔法を切り替えられる。右クリックで選択中の魔法を発動する。
 */
public class SpellbookItem extends Item {

	public SpellbookItem(Settings settings) {
		super(settings.maxCount(1).component(ModComponents.SPELLBOOK_DATA, SpellbookData.EMPTY));
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		SpellbookData data = stack.getOrDefault(ModComponents.SPELLBOOK_DATA, SpellbookData.EMPTY);
		if (data.isEmpty()) {
			user.sendMessage(Text.translatable("arcanemagic.message.spellbook_empty"), true);
			return ActionResult.FAIL;
		}

		Optional<Identifier> selected = data.selectedSpell();
		if (selected.isEmpty()) {
			user.sendMessage(Text.translatable("arcanemagic.message.spellbook_no_spell"), true);
			return ActionResult.FAIL;
		}

		Spell spell = SpellRegistry.get(selected.get()).orElse(null);
		if (spell == null) {
			return ActionResult.FAIL;
		}

		if (user.getItemCooldownManager().isCoolingDown(stack)) {
			user.sendMessage(Text.translatable("arcanemagic.message.cooldown"), true);
			return ActionResult.FAIL;
		}

		spell.cast((ServerWorld) world, user, 1);
		user.getItemCooldownManager().set(stack, spell.cooldownTicks(1));
		return ActionResult.SUCCESS;
	}

	/** サーバー側で選択スロットを進め/戻し、更新後のデータを返す。ネットワークハンドラから呼ばれる。 */
	public static SpellbookData cycle(ItemStack stack, int direction) {
		SpellbookData data = stack.getOrDefault(ModComponents.SPELLBOOK_DATA, SpellbookData.EMPTY);
		SpellbookData updated = data.cycled(direction);
		stack.set(ModComponents.SPELLBOOK_DATA, updated);
		return updated;
	}
}
