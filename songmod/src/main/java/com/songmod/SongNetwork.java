package com.songmod;

import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** 選曲台の曲選び画面のための通信(サーバー → 画面を開く、画面 → サーバー 曲を選んだ)。 */
public final class SongNetwork {

	/** 一覧画面に出す再生位置(0.1 秒単位。流れていなければ -1)。 */
	public record Status(int nowTenths, int totalTenths, boolean paused) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Status::nowTenths,
				ByteBufCodecs.VAR_INT, Status::totalTenths,
				ByteBufCodecs.BOOL, Status::paused,
				Status::new);
	}

	/** 建てるときの設定(速度 %、移調 半音、音色の表示名)。 */
	public record Options(int speedPct, int transpose, String instLabel) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Options> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Options::speedPct,
				ByteBufCodecs.VAR_INT, Options::transpose,
				ByteBufCodecs.STRING_UTF8, Options::instLabel,
				Options::new);
	}

	/** サーバー → クライアント: 曲の一覧画面を開く。 */
	public record OpenPayload(BlockPos pos, String current, List<String> songs, Status status, Options options)
			implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<OpenPayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SongMod.MOD_ID, "selector_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenPayload::pos,
				ByteBufCodecs.STRING_UTF8, OpenPayload::current,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenPayload::songs,
				Status.CODEC, OpenPayload::status,
				Options.CODEC, OpenPayload::options,
				OpenPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** クライアント → サーバー: 一覧から曲を選んだ。 */
	public record SelectPayload(BlockPos pos, String name) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<SelectPayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SongMod.MOD_ID, "selector_select"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SelectPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, SelectPayload::pos,
				ByteBufCodecs.STRING_UTF8, SelectPayload::name,
				SelectPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** クライアント → サーバー: この台が建てた回路を消す。 */
	public record ErasePayload(BlockPos pos) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ErasePayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SongMod.MOD_ID, "selector_erase"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ErasePayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, ErasePayload::pos,
				ErasePayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** クライアント → サーバー: 再生位置の操作(SongPlayback.RESTART / SEEK / PAUSE。value は SEEK の秒数)。 */
	public record ControlPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ControlPayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SongMod.MOD_ID, "selector_control"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ControlPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, ControlPayload::pos,
				ByteBufCodecs.VAR_INT, ControlPayload::action,
				ByteBufCodecs.VAR_INT, ControlPayload::value,
				ControlPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** クライアント → サーバー: 建てるときの設定を、次の値に切り替える(kind: 0=速度 1=移調 2=音色)。 */
	public record OptionPayload(BlockPos pos, int kind) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<OptionPayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SongMod.MOD_ID, "selector_option"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OptionPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OptionPayload::pos,
				ByteBufCodecs.VAR_INT, OptionPayload::kind,
				OptionPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private SongNetwork() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectPayload.TYPE, SelectPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ErasePayload.TYPE, ErasePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ControlPayload.TYPE, ControlPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(OptionPayload.TYPE, OptionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OptionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> SongSelector.option(player, payload.pos(), payload.kind()));
		});
		ServerPlayNetworking.registerGlobalReceiver(ControlPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> SongSelector.control(player, payload.pos(), payload.action(), payload.value()));
		});
		ServerPlayNetworking.registerGlobalReceiver(ErasePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> SongSelector.erase(player, payload.pos()));
		});
		ServerPlayNetworking.registerGlobalReceiver(SelectPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> SongSelector.select(player, payload.pos(), payload.name()));
		});
	}

	static void open(ServerPlayer player, BlockPos pos, String current, List<String> songs, Status status, Options options) {
		ServerPlayNetworking.send(player, new OpenPayload(pos, current, songs, status, options));
	}
}
