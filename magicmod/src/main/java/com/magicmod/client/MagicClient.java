package com.magicmod.client;

import com.mojang.blaze3d.platform.InputConstants;

import com.magicmod.Books;
import com.magicmod.MagicMod;
import com.magicmod.ModEffects;
import com.magicmod.ModEntities;
import com.magicmod.MobClasses.*;
import com.magicmod.Net;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

/** クライアント側: キー操作・HUD・画面・エンティティの見た目。 */
public class MagicClient implements ClientModInitializer {

	private static final KeyMapping.Category CAT = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "keys"));
	private static KeyMapping WHEEL, DODGE, CAST, PREV, NEXT, TECH, TECH_NEXT, FAMILIAR, STATUS;
	private static boolean jumpWasDown;

	@Override
	public void onInitializeClient() {
		WHEEL = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.wheel", InputConstants.KEY_B, CAT));
		DODGE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.dodge", InputConstants.KEY_LALT, CAT));
		CAST = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.cast", InputConstants.KEY_R, CAT));
		PREV = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.prev", InputConstants.KEY_Z, CAT));
		NEXT = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.next", InputConstants.KEY_X, CAT));
		TECH = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.tech", InputConstants.KEY_G, CAT));
		TECH_NEXT = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.tech_next", InputConstants.KEY_V, CAT));
		FAMILIAR = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.familiar", InputConstants.KEY_H, CAT));
		STATUS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.status", InputConstants.KEY_K, CAT));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ClientFx.tick();
			if (mc.player == null) {
				return;
			}
			if (WHEEL.isDown() && mc.gui.screen() == null && !ClientState.wheel.isEmpty()) {
				mc.setScreenAndShow(new WheelScreen(KeyMappingHelper.getBoundKeyOf(WHEEL).getValue()));
			}
			while (DODGE.consumeClick()) {
				int bits = (mc.options.keyUp.isDown() ? 1 : 0) | (mc.options.keyDown.isDown() ? 2 : 0) | (mc.options.keyLeft.isDown() ? 4 : 0)
						| (mc.options.keyRight.isDown() ? 8 : 0);
				key("dodge", bits);
			}
			while (CAST.consumeClick()) {
				key("cast", 0);
			}
			while (PREV.consumeClick()) {
				key("cycle", -1);
			}
			while (NEXT.consumeClick()) {
				key("cycle", 1);
			}
			while (TECH.consumeClick()) {
				key("tech", 0);
			}
			while (TECH_NEXT.consumeClick()) {
				key("tech_cycle", 1);
			}
			while (FAMILIAR.consumeClick()) {
				key("familiar", 0);
			}
			while (STATUS.consumeClick()) {
				key("status", 0);
			}
			boolean jump = mc.options.keyJump.isDown();
			if (jump && !jumpWasDown && !mc.player.onGround() && mc.player.hasEffect(ModEffects.DOUBLE_JUMP) && !mc.player.getAbilities().flying) {
				key("double_jump", 0);
			}
			jumpWasDown = jump;
		});

		ClientPreAttackCallback.EVENT.register((mc, player, clicks) -> {
			if (clicks > 0 && ClientState.arch.equals("魔法型") && Books.isCaster(player.getMainHandItem()) && mc.gui.screen() == null) {
				key("seal", 0);
				player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
				return true;
			}
			return false;
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "hud"),
				(g, delta) -> Hud.draw(g));

		ClientPlayNetworking.registerGlobalReceiver(Net.ScreenFxPayload.TYPE,
				(pl, ctx) -> ctx.client().execute(() -> ClientFx.start(pl.kind(), pl.strength(), pl.ticks(), pl.color())));
		ClientPlayNetworking.registerGlobalReceiver(Net.SyncPayload.TYPE, (pl, ctx) -> ctx.client().execute(() -> ClientState.apply(pl.tag())));
		ClientPlayNetworking.registerGlobalReceiver(Net.UiPayload.TYPE, (pl, ctx) -> ctx.client().execute(() -> {
			Minecraft mc = Minecraft.getInstance();
			if (pl.tag().getBooleanOr("close", false)) {
				if (mc.gui.screen() instanceof UiScreen) {
					mc.gui.setScreen(null);
				}
				return;
			}
			if (mc.gui.screen() instanceof UiScreen s && s.screenId().equals(pl.tag().getStringOr("screen", ""))) {
				s.update(pl.tag());
			} else {
				mc.setScreenAndShow(new UiScreen(pl.tag()));
			}
		}));

		registerParticles();
		EntityRendererRegistry.register(ModEntities.MAGIC_BOLT, ctx -> new ThrownItemRenderer<>(ctx, 1.6f, true));
		CustomModels.registerAll();
	}

	private static void particle(net.minecraft.core.particles.SimpleParticleType type, MagicParticle.Style s) {
		net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(type,
				sprites -> MagicParticle.provider(sprites, s));
	}

	/** 独自パーティクルの動き: 寿命, 大きさ, 終わりの大きさ倍率, 重力, 摩擦, 回転, 光る, コマ送り, 上昇。 */
	private static void registerParticles() {
		particle(com.magicmod.ModParticles.EMBER, new MagicParticle.Style(10, 22, 0.16f, 0.3f, -0.02f, 0.94f, 0.05f, true, true, 0.02f));
		particle(com.magicmod.ModParticles.SPARK, new MagicParticle.Style(4, 9, 0.18f, 0.6f, 0f, 0.8f, 0.6f, true, true, 0f));
		particle(com.magicmod.ModParticles.DROPLET, new MagicParticle.Style(12, 24, 0.12f, 0.8f, 0.6f, 0.98f, 0f, false, true, 0.05f));
		particle(com.magicmod.ModParticles.GALE, new MagicParticle.Style(10, 20, 0.28f, 1.6f, -0.01f, 0.92f, 0.35f, false, true, 0f));
		particle(com.magicmod.ModParticles.VOID_MOTE, new MagicParticle.Style(14, 26, 0.2f, 0.1f, 0f, 0.9f, 0.2f, true, true, 0f));
		particle(com.magicmod.ModParticles.SHADOW_WISP, new MagicParticle.Style(20, 36, 0.3f, 2.2f, -0.01f, 0.95f, 0.03f, false, true, 0.01f));
		particle(com.magicmod.ModParticles.HOLY_STAR, new MagicParticle.Style(12, 24, 0.18f, 0.2f, -0.005f, 0.9f, 0.12f, true, true, 0f));
		particle(com.magicmod.ModParticles.CURSE_MOTE, new MagicParticle.Style(16, 30, 0.2f, 0.6f, -0.015f, 0.95f, 0.05f, true, true, 0.01f));
		particle(com.magicmod.ModParticles.RUNE, new MagicParticle.Style(18, 30, 0.2f, 0.5f, -0.02f, 0.92f, 0.04f, true, false, 0.03f));
		particle(com.magicmod.ModParticles.STEEL_FLASH, new MagicParticle.Style(3, 6, 0.35f, 1.5f, 0f, 0.6f, 0.4f, true, true, 0f));
	}

	private static void key(String action, int value) {
		ClientPlayNetworking.send(new Net.KeyPayload(action, value));
	}
}
