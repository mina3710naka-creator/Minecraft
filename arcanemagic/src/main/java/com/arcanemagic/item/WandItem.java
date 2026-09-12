package com.arcanemagic.item;

import com.arcanemagic.component.WandData;
import com.arcanemagic.component.WandDataHelper;
import com.arcanemagic.spell.Spell;
import com.arcanemagic.spell.SpellRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 杖: 1つの魔法だけを保持できるが、アーケインの祭壇でレベル強化できる。
 * 右クリックで選択中(唯一)の魔法を発動する。
 */
public class WandItem extends Item {

	public WandItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		WandData data = WandDataHelper.get(stack);
		if (data.spellId().isEmpty()) {
			user.displayClientMessage(Component.translatable("arcanemagic.message.wand_no_spell"), true);
			return InteractionResult.FAIL;
		}

		Spell spell = SpellRegistry.get(data.spellId().get()).orElse(null);
		if (spell == null) {
			return InteractionResult.FAIL;
		}

		if (user.getCooldowns().isOnCooldown(stack)) {
			user.displayClientMessage(Component.translatable("arcanemagic.message.cooldown"), true);
			return InteractionResult.FAIL;
		}

		spell.cast((ServerLevel) level, user, data.level());
		user.getCooldowns().addCooldown(stack, spell.cooldownTicks(data.level()));
		return InteractionResult.SUCCESS;
	}
}
