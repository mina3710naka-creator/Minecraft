package com.lampvideo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundEvent;

/**
 * クライアント側: ランプの単色テクスチャを、内蔵のリソースパック(flat_lamps)として登録し、
 * /lampvideotexture flat|normal で、単色と元のテクスチャを切り替える。
 */
public class LampVideoClient implements ClientModInitializer {

	private static final String PACK_NAME = "flat_lamps";

	/** チャンクビルダーの元の設定。同期にしている間だけ覚えておいて、戻すときに使う。 */
	private static PrioritizeChunkUpdates originalChunkMode;

	/** いま鳴らしている動画の音声。止めるときに使う。 */
	private static SoundInstance currentSound;

	/** チャンクの同期更新の範囲を、画面全体に広げるか(広げるのは、動画の再生中だけ)。 */
	private static boolean syncAll;
	/** サーバーから知らされた、動画の再生中かどうか。 */
	private static volatile boolean videoPlaying;
	/** 画面を含む範囲 {minX,minY,minZ,maxX,maxY,maxZ}(ブロック座標)。 */
	private static volatile int[] screenBox = new int[6];

	/** ゲームを一時停止していた時間の累計(ナノ秒)。サーバー側が、映像の時計から引くために読む。 */
	private static volatile long pausedNanos;
	private static long lastPausePoll;

	// 診断用の数え上げ(/lampvideochunks status で見られる)
	private static volatile long mixinCalls;
	private static volatile long mixinWidened;
	private static volatile long statePackets;
	private static volatile long soundPackets;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(LampSoundPayload.TYPE, (payload, context) -> {
			soundPackets++;
			Minecraft.getInstance().execute(() -> handleSound(payload));
		});

		ClientPlayNetworking.registerGlobalReceiver(LampStatePayload.TYPE, (payload, context) -> {
			statePackets++;
			videoPlaying = payload.playing();
			screenBox = new int[] { payload.minX(), payload.minY(), payload.minZ(), payload.maxX(), payload.maxY(), payload.maxZ() };
		});

		// ゲームを一時停止している間は、サーバーも音も止まるので、その時間を数えておく(映像の時計から引く)
		LampVideoMod.pausedNanosProbe = () -> pausedNanos;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			long now = System.nanoTime();
			if (lastPausePoll != 0 && client.isPaused()) {
				pausedNanos += now - lastPausePoll;
			}
			lastPausePoll = now;
		});

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
						.then(ClientCommands.literal("all").executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal(applyChunkMode(2)));
							return 1;
						}))
						.then(ClientCommands.literal("sync").executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal(applyChunkMode(1)));
							return 1;
						}))
						.then(ClientCommands.literal("normal").executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal(applyChunkMode(0)));
							return 1;
						}))
						.then(ClientCommands.literal("status").executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal(chunkStatus()));
							return 1;
						}))));
	}

	/**
	 * 音声を、プレイヤーに付いてくる音(位置に関係なく、いつもプレイヤーの位置から聞こえる)として鳴らす/止める。
	 * 画面から離れたり、飛び回ったりしても、音量が変わらない。
	 */
	private static void handleSound(LampSoundPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (currentSound != null) {
			mc.getSoundManager().stop(currentSound);
			currentSound = null;
		}
		if (!payload.play() || payload.sound().isEmpty()) {
			return;
		}
		String[] id = payload.sound().split(":", 2);
		if (id.length != 2) {
			return;
		}
		SoundEvent event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(id[0], id[1]));
		currentSound = SimpleSoundInstance.forUI(event, 1.0F, 1.0F);
		mc.getSoundManager().play(currentSound);
	}

	/** チャンク更新の状態: 0=通常、1=近くを同期、2=画面全体を同期(再生中)。 */
	public static int chunkMode() {
		boolean nearby = Minecraft.getInstance().options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.NEARBY;
		return !nearby ? 0 : (syncAll ? 2 : 1);
	}

	/**
	 * チャンクの作り直しの方式を切り替える。
	 * 1: 設定「チャンクビルダー」を「完全ブロッキング」にする(近く、約27ブロック以内を、同じフレームで作り直す)。
	 * 2: 1 に加えて、動画の再生中だけ、画面全体(すべての区画)を同じフレームで作り直す(重い代わりに、全体が同時になる)。
	 * 0: 元の設定に戻す。
	 */
	public static String applyChunkMode(int mode) {
		Minecraft mc = Minecraft.getInstance();
		OptionInstance<PrioritizeChunkUpdates> option = mc.options.prioritizeChunkUpdates();
		syncAll = mode == 2;
		if (mode > 0) {
			if (originalChunkMode == null) {
				originalChunkMode = option.get();
			}
			option.set(PrioritizeChunkUpdates.NEARBY);
		} else {
			option.set(originalChunkMode != null ? originalChunkMode : PrioritizeChunkUpdates.NONE);
			originalChunkMode = null;
		}
		mc.options.save();
		String name = mode == 0 ? "元の設定" : (mode == 1 ? "完全ブロッキング(近くを同期)" : "完全ブロッキング + 画面全体を同期(再生中)");
		return "[ランプ動画] チャンク更新を「" + name + "」にしました";
	}

	/**
	 * Mixin から呼ばれる。再生中に「画面全体を同期」で、区画の中心 pos が画面の範囲(まわり16ブロックを含む)の中なら、
	 * 距離を 0 として返して、同期で作り直させる。画面と関係ない区画は、バニラの距離のまま(非同期)。
	 */
	public static double syncDistance(double original, int x, int y, int z) {
		mixinCalls++;
		if (syncAll && videoPlaying) {
			int[] b = screenBox;
			if (x >= b[0] - 16 && x <= b[3] + 16 && y >= b[1] - 16 && y <= b[4] + 16 && z >= b[2] - 16 && z <= b[5] + 16) {
				mixinWidened++;
				return 0.0;
			}
		}
		return original;
	}

	/** 診断用: 割り込み(Mixin)が実際に呼ばれたか、再生中の知らせが届いたか。 */
	public static String chunkStatus() {
		return "[ランプ動画] チャンク更新の診断: 方式=" + chunkMode() + "(0通常/1近く同期/2全体同期), 再生中の知らせ=" + videoPlaying
				+ ", 割り込みの呼び出し=" + mixinCalls + " 回(そのうち範囲を広げた=" + mixinWidened + " 回), 再生状態の通信=" + statePackets
				+ " 回, 音声の通信=" + soundPackets + " 回"
				+ (mixinCalls == 0 ? "\n(割り込みの呼び出しが 0 回です。割り込みが適用されていません。Sodium などの描画 MOD が原因のことがあります)" : "");
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
