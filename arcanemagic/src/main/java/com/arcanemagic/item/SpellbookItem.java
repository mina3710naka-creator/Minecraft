package com.arcanemagic.item;

import java.util.Optional;

import com.arcanemagic.component.SpellbookData;
import com.arcanemagic.component.SpellbookDataHelper;
import com.arcanemagic.spell.Spell;
import com.arcanemagic.spell.SpellRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 魔導書: 最大6個の魔法を登録でき、Ctrl+マウスホイール(または専用キー)で
 * 選択中の魔法を切り替えられる。右クリックで選択中の魔法を発動する。
 */
public class SpellbookItem extends Item {

	public SpellbookItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		SpellbookData data = SpellbookDataHelper.get(stack);
		if (data.isEmpty()) {
			user.displayClientMessage(Component.translatable("arcanemagic.message.spellbook_empty"), true);
			return InteractionResult.FAIL;
		}

		Optional<Identifier> selected = data.selectedSpell();
		if (selected.isEmpty()) {
			user.displayClientMessage(Component.translatable("arcanemagic.message.spellbook_no_spell"), true);
			return InteractionResult.FAIL;
		}

		Spell spell = SpellRegistry.get(selected.get()).orElse(null);
		if (spell == null) {
			return InteractionResult.FAIL;
		}

		if (user.getCooldowns().isOnCooldown(stack)) {
			user.displayClientMessage(Component.translatable("arcanemagic.message.cooldown"), true);
			return InteractionResult.FAIL;
		}

		spell.cast((ServerLevel) level, user, 1);
		user.getCooldowns().addCooldown(stack, spell.cooldownTicks(1));
		return InteractionResult.SUCCESS;
	}

	/** サーバー側で選択スロットを進め/戻し、更新後のデータを返す。ネットワークハンドラから呼ばれる。 */
	public static SpellbookData cycle(ItemStack stack, int direction) {
		SpellbookData data = SpellbookDataHelper.get(stack);
		SpellbookData updated = data.cycled(direction);
		SpellbookDataHelper.set(stack, updated);
		return updated;
	}
}
