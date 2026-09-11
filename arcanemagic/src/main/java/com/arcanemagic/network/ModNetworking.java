package com.arcanemagic.network;

import com.arcanemagic.component.SpellbookData;
import com.arcanemagic.item.SpellbookItem;
import com.arcanemagic.spell.SpellRegistry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class ModNetworking {

	private ModNetworking() {
	}

	/** クライアント・サーバー両方から呼ばれる共通登録(パケットの型だけを登録する)。 */
	public static void registerCommon() {
		PayloadTypeRegistry.playC2S().register(CycleSpellPayload.ID, CycleSpellPayload.CODEC);
	}

	/** サーバー側の受信処理。実際にアイテムのデータを書き換えるのはここ(権威はサーバー)。 */
	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(CycleSpellPayload.ID, (payload, context) -> {
			PlayerEntity player = context.player();
			context.server().execute(() -> {
				ItemStack main = player.getMainHandStack();
				ItemStack off = player.getOffHandStack();
				ItemStack book;
				if (main.getItem() instanceof SpellbookItem) {
					book = main;
				} else if (off.getItem() instanceof SpellbookItem) {
					book = off;
				} else {
					return;
				}

				SpellbookData updated = SpellbookItem.cycle(book, payload.direction());
				updated.selectedSpell().ifPresent(spellId -> SpellRegistry.get(spellId).ifPresent(spell ->
						player.sendMessage(Text.translatable("arcanemagic.message.spell_selected",
								spell.displayName(), updated.selected() + 1), true)));
			});
		});
	}
}
