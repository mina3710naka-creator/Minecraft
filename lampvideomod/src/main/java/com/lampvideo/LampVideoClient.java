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
import net.minecraft.client.OptionInstance;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * クライアント側: ランプの単色テクスチャを、内蔵のリソースパック(flat_lamps)として登録し、
 * /lampvideotexture flat|normal で、単色と元のテクスチャを切り替える。
 */
public class LampVideoClient implements ClientModInitializer {

	private static final String PACK_NAME = "flat_lamps";

	/** チャンクビルダーの元の設定。同期にしている間だけ覚えておいて、戻すときに使う。 */
	private static PrioritizeChunkUpdates originalChunkMode;

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

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("lampvideochunks")
						.then(ClientCommands.literal("sync").executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal(applyChunkSync(true)));
							return 1;
						}))
						.then(ClientCommands.literal("normal").executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal(applyChunkSync(false)));
							return 1;
						}))));
	}

	/** いまの設定が「近くのチャンクを同期で作り直す」になっているか。 */
	public static boolean isChunkSync() {
		return Minecraft.getInstance().options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.NEARBY;
	}

	/**
	 * チャンクビルダー(設定 > ビデオ設定)を切り替える。sync=true で「完全ブロッキング」(近くのチャンクを、同じフレームで作り直す)、
	 * false で元の設定に戻す。効くのは、プレイヤーの近く(約2チャンク)だけ。
	 */
	public static String applyChunkSync(boolean sync) {
		Minecraft mc = Minecraft.getInstance();
		OptionInstance<PrioritizeChunkUpdates> option = mc.options.prioritizeChunkUpdates();
		if (sync) {
			if (originalChunkMode == null) {
				originalChunkMode = option.get();
			}
			option.set(PrioritizeChunkUpdates.NEARBY);
		} else {
			option.set(originalChunkMode != null ? originalChunkMode : PrioritizeChunkUpdates.NONE);
			originalChunkMode = null;
		}
		mc.options.save();
		return "[ランプ動画] チャンクビルダーを「" + (sync ? "完全ブロッキング(近くを同期)" : "元の設定") + "」にしました";
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
