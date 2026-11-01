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
	private static KeyMapping CAST, PREV, NEXT, TECH, TECH_NEXT, FAMILIAR, STATUS;
	private static boolean jumpWasDown;

	@Override
	public void onInitializeClient() {
		CAST = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.cast", InputConstants.KEY_R, CAT));
		PREV = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.prev", InputConstants.KEY_Z, CAT));
		NEXT = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.next", InputConstants.KEY_X, CAT));
		TECH = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.tech", InputConstants.KEY_G, CAT));
		TECH_NEXT = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.tech_next", InputConstants.KEY_V, CAT));
		FAMILIAR = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.familiar", InputConstants.KEY_H, CAT));
		STATUS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magicmod.status", InputConstants.KEY_K, CAT));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				return;
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

		EntityRendererRegistry.register(ModEntities.MAGIC_BOLT, ThrownItemRenderer::new);
		EntityRendererRegistry.register(ModEntities.FAM_WOLF, WolfRenderer::new);
		EntityRendererRegistry.register(ModEntities.FAM_SLIME, SlimeRenderer::new);
		EntityRendererRegistry.register(ModEntities.FAM_FAIRY, AllayRenderer::new);
		EntityRendererRegistry.register(ModEntities.FAM_PARROT, ParrotRenderer::new);
		EntityRendererRegistry.register(ModEntities.FAM_GOLEM, IronGolemRenderer::new);
		EntityRendererRegistry.register(ModEntities.FAM_ELEMENTAL, BlazeRenderer::new);
		EntityRendererRegistry.register(ModEntities.FAM_GUARDIAN, VexRenderer::new);
		EntityRendererRegistry.register(ModEntities.FAM_DRAGON, GhastRenderer::new);
		EntityRendererRegistry.register(ModEntities.WIND_SPRITE, BreezeRenderer::new);
		EntityRendererRegistry.register(ModEntities.TORNADO_BAT, PhantomRenderer::new);
		EntityRendererRegistry.register(ModEntities.SPARK_JELLY, SlimeRenderer::new);
		EntityRendererRegistry.register(ModEntities.VOLT_RAT, SilverfishRenderer::new);
		EntityRendererRegistry.register(ModEntities.FLAME_IMP, BlazeRenderer::new);
		EntityRendererRegistry.register(ModEntities.MAGMA_HOUND, RavagerRenderer::new);
		EntityRendererRegistry.register(ModEntities.TIDELING, DrownedRenderer::new);
		EntityRendererRegistry.register(ModEntities.KRAKEN_SPAWN, GuardianRenderer::new);
		EntityRendererRegistry.register(ModEntities.GRAVITY_MIMIC, ShulkerRenderer::new);
		EntityRendererRegistry.register(ModEntities.VOID_BAT, PhantomRenderer::new);
		EntityRendererRegistry.register(ModEntities.SHADOW_STALKER, EndermanRenderer::new);
		EntityRendererRegistry.register(ModEntities.NIGHTMARE_BAT, PhantomRenderer::new);
		EntityRendererRegistry.register(ModEntities.LUMIN_SPRITE, VexRenderer::new);
		EntityRendererRegistry.register(ModEntities.HOLY_GUARDIAN, IronGolemRenderer::new);
		EntityRendererRegistry.register(ModEntities.CURSED_PUPPET, SkeletonRenderer::new);
		EntityRendererRegistry.register(ModEntities.JINX_MOTH, VexRenderer::new);
		EntityRendererRegistry.register(ModEntities.GALE_HARPY, BreezeRenderer::new);
		EntityRendererRegistry.register(ModEntities.THUNDER_WOLF_ALPHA, RavagerRenderer::new);
		EntityRendererRegistry.register(ModEntities.SALAMANDER_KING, MagmaCubeRenderer::new);
		EntityRendererRegistry.register(ModEntities.LEVIATHAN_JUVENILE, ElderGuardianRenderer::new);
		EntityRendererRegistry.register(ModEntities.ORBIT_GOLEM, IronGolemRenderer::new);
		EntityRendererRegistry.register(ModEntities.ABYSS_EXECUTOR, WitherSkeletonRenderer::new);
		EntityRendererRegistry.register(ModEntities.HOLY_KNIGHT, VindicatorRenderer::new);
		EntityRendererRegistry.register(ModEntities.CURSE_WITCH, WitchRenderer::new);
		EntityRendererRegistry.register(ModEntities.ZEPHARION, PhantomRenderer::new);
		EntityRendererRegistry.register(ModEntities.GARDONIUS, BlazeRenderer::new);
		EntityRendererRegistry.register(ModEntities.IGDRUM, GhastRenderer::new);
		EntityRendererRegistry.register(ModEntities.MELUSINE, DrownedRenderer::new);
		EntityRendererRegistry.register(ModEntities.SINGULARIS, EndermanRenderer::new);
		EntityRendererRegistry.<EEvoker>register(ModEntities.NECROVANT, EvokerRenderer::new);
		EntityRendererRegistry.register(ModEntities.SERAPHIEL, VexRenderer::new);
		EntityRendererRegistry.register(ModEntities.MORODIS, RavagerRenderer::new);
	}

	private static void key(String action, int value) {
		ClientPlayNetworking.send(new Net.KeyPayload(action, value));
	}
}
