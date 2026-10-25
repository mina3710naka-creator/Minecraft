package com.lampvideo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lampvideo.pc.HeadphoneContainer;
import com.lampvideo.pc.HeadphoneSlot;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/** 持ち物画面(InventoryMenu)に、ヘッドホン専用のスロットを1つ足す(サーバーとクライアントの両方で、同じ位置に)。 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

	@Shadow
	protected abstract Slot addSlot(Slot slot);

	@Inject(method = "<init>(Lnet/minecraft/world/entity/player/Inventory;ZLnet/minecraft/world/entity/player/Player;)V",
			at = @At("RETURN"), require = 0)
	private void lampvideo$addHeadphoneSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		addSlot(new HeadphoneSlot(new HeadphoneContainer(owner), 77, 44));
	}
}
