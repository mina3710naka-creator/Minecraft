package com.arcanemagic.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** アーケインの祭壇のGUI描画。専用テクスチャは使わず、色の塗りつぶしだけで枠を描く。 */
public class ArcaneAltarScreen extends HandledScreen<ArcaneAltarScreenHandler> {

	public ArcaneAltarScreen(ArcaneAltarScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;

		context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF2B1B40);
		context.fill(x + 4, y + 4, x + backgroundWidth - 4, y + 18, 0xFF3A2A5C);

		drawSlotFrame(context, x + ArcaneAltarScreenHandler.FOCUS_X, y + ArcaneAltarScreenHandler.FOCUS_Y);
		drawSlotFrame(context, x + ArcaneAltarScreenHandler.CATALYST_X, y + ArcaneAltarScreenHandler.CATALYST_Y);
		drawSlotFrame(context, x + ArcaneAltarScreenHandler.RESULT_X, y + ArcaneAltarScreenHandler.RESULT_Y);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawSlotFrame(context, x + 8 + col * 18, y + 84 + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			drawSlotFrame(context, x + 8 + col * 18, y + 142);
		}

		int arrowY = y + 33;
		context.fill(x + 62, arrowY + 3, x + 112, arrowY + 5, 0xFFB79CE8);
	}

	private void drawSlotFrame(DrawContext context, int slotX, int slotY) {
		context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF1A1027);
		context.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF4A3A70);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		super.drawForeground(context, mouseX, mouseY);

		int cost = this.handler.getXpCost();
		Text status = cost > 0
				? Text.translatable("arcanemagic.altar.cost", cost)
				: this.handler.getReasonText();
		int color = cost > 0 ? 0xFF8CFF8C : 0xFFFF9C9C;
		context.drawText(this.textRenderer, status, 8, 56, color, false);
	}
}
