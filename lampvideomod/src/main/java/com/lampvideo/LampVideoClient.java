package com.lampvideo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * クライアント側: ランプの単色テクスチャを、内蔵のリソースパック(flat_lamps)として登録し、
 * /lampvideotexture flat|normal で、単色と元のテクスチャを切り替える。
 */
public class LampVideoClient implements ClientModInitializer {

	private static final String PACK_NAME = "flat_lamps";

	@Override
	public void onInitializeClient() {
		LampVideoRemoteItem.openMenu = () -> Minecraft.getInstance().setScreenAndShow(new LampVideoMenuScreen());

		FabricLoader.getInstance().getModContainer(LampVideoMod.MOD_ID).ifPresent(container ->
				ResourceManagerHelper.registerBuiltinResourcePack(
						Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, PACK_NAME), container,
						Component.literal("ランプ動画: 単色ランプ"), ResourcePackActivationType.DEFAULT_ENABLED));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("lampvideotexture")
						.then(ClientCommands.literal("flat").executes(ctx -> setFlat(ctx.getSource(), true)))
						.then(ClientCommands.literal("normal").executes(ctx -> setFlat(ctx.getSource(), false)))));
	}

	private static int setFlat(FabricClientCommandSource source, boolean flat) {
		Minecraft mc = source.getClient();
		PackRepository repo = mc.getResourcePackRepository();
		String id = null;
		for (String candidate : repo.getAvailableIds()) {
			if (candidate.toLowerCase(Locale.ROOT).contains(PACK_NAME)) {
				id = candidate;
			}
		}
		if (id == null) {
			source.sendError(Component.literal("[ランプ動画] 単色ランプのリソースパックが見つかりません"));
			return 0;
		}
		List<String> selected = new ArrayList<>(repo.getSelectedIds());
		boolean enabled = selected.contains(id);
		if (flat == enabled) {
			source.sendFeedback(Component.literal("[ランプ動画] すでに" + (flat ? "単色" : "元のテクスチャ") + "です"));
			return 1;
		}
		if (flat) {
			selected.add(id);
		} else {
			selected.remove(id);
		}
		repo.setSelected(selected);
		mc.options.updateResourcePacks(repo);
		mc.reloadResourcePacks();
		source.sendFeedback(Component.literal("[ランプ動画] ランプを" + (flat ? "単色" : "元のテクスチャ")
				+ "にしました(リソースを再読み込みします)"));
		return 1;
	}
}
