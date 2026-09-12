package com.arcanemagic.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** アーケインの祭壇のGUI描画。専用テクスチャは使わず、色の塗りつぶしだけで枠を描く。 */
public class ArcaneAltarScreen extends AbstractContainerScreen<ArcaneAltarMenu> {

	public ArcaneAltarScreen(ArcaneAltarMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 166;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;

		guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B1B40);
		guiGraphics.fill(x + 4, y + 4, x + imageWidth - 4, y + 18, 0xFF3A2A5C);

		drawSlotFrame(guiGraphics, x + ArcaneAltarMenu.FOCUS_X, y + ArcaneAltarMenu.FOCUS_Y);
		drawSlotFrame(guiGraphics, x + ArcaneAltarMenu.CATALYST_X, y + ArcaneAltarMenu.CATALYST_Y);
		drawSlotFrame(guiGraphics, x + ArcaneAltarMenu.RESULT_X, y + ArcaneAltarMenu.RESULT_Y);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawSlotFrame(guiGraphics, x + 8 + col * 18, y + 84 + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			drawSlotFrame(guiGraphics, x + 8 + col * 18, y + 142);
		}

		int arrowY = y + 33;
		guiGraphics.fill(x + 62, arrowY + 3, x + 112, arrowY + 5, 0xFFB79CE8);
	}

	private void drawSlotFrame(GuiGraphics guiGraphics, int slotX, int slotY) {
		guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF1A1027);
		guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF4A3A70);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		super.renderLabels(guiGraphics, mouseX, mouseY);

		int cost = this.menu.getXpCost();
		Component status = cost > 0
				? Component.translatable("arcanemagic.altar.cost", cost)
				: this.menu.getReasonText();
		int color = cost > 0 ? 0xFF8CFF8C : 0xFFFF9C9C;
		guiGraphics.drawString(this.font, status, 8, 56, color, false);
	}
}
