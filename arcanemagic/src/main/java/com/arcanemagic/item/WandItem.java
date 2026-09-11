package com.arcanemagic.item;

import com.arcanemagic.component.ModComponents;
import com.arcanemagic.component.WandData;
import com.arcanemagic.spell.Spell;
import com.arcanemagic.spell.SpellRegistry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * 杖: 1つの魔法だけを保持できるが、アーケインの祭壇でレベル強化できる。
 * 右クリックで選択中(唯一)の魔法を発動する。
 */
public class WandItem extends Item {

	public WandItem(Settings settings) {
		super(settings.maxCount(1).component(ModComponents.WAND_DATA, WandData.EMPTY));
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		WandData data = stack.getOrDefault(ModComponents.WAND_DATA, WandData.EMPTY);
		if (data.spellId().isEmpty()) {
			user.sendMessage(Text.translatable("arcanemagic.message.wand_no_spell"), true);
			return ActionResult.FAIL;
		}

		Spell spell = SpellRegistry.get(data.spellId().get()).orElse(null);
		if (spell == null) {
			return ActionResult.FAIL;
		}

		if (user.getItemCooldownManager().isCoolingDown(stack)) {
			user.sendMessage(Text.translatable("arcanemagic.message.cooldown"), true);
			return ActionResult.FAIL;
		}

		spell.cast((ServerWorld) world, user, data.level());
		user.getItemCooldownManager().set(stack, spell.cooldownTicks(data.level()));
		return ActionResult.SUCCESS;
	}
}
