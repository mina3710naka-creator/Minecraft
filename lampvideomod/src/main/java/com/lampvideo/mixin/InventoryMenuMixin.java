package com.lampvideo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lampvideo.pc.HeadphoneContainer;
import com.lampvideo.pc.HeadphoneSlot;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * 持ち物画面(InventoryMenu)に、ヘッドホン専用のスロットを1つ足す(サーバーとクライアントの両方で、同じ位置に)。
 * addSlot は親クラス(AbstractContainerMenu)のメソッドなので、この Mixin も親クラスを継承して、そのまま呼ぶ。
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

	protected InventoryMenuMixin(MenuType<?> type, int containerId) {
		super(type, containerId);
	}

	@Inject(method = "<init>(Lnet/minecraft/world/entity/player/Inventory;ZLnet/minecraft/world/entity/player/Player;)V",
			at = @At("RETURN"), require = 0)
	private void lampvideo$addHeadphoneSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		this.addSlot(new HeadphoneSlot(new HeadphoneContainer(owner), 77, 44));
	}
}
