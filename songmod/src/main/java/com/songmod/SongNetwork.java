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

	/** サーバー → クライアント: 曲の一覧画面を開く。 */
	public record OpenPayload(BlockPos pos, String current, List<String> songs, int nowTenths, int totalTenths,
			boolean paused) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<OpenPayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SongMod.MOD_ID, "selector_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenPayload::pos,
				ByteBufCodecs.STRING_UTF8, OpenPayload::current,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenPayload::songs,
				ByteBufCodecs.VAR_INT, OpenPayload::nowTenths,
				ByteBufCodecs.VAR_INT, OpenPayload::totalTenths,
				ByteBufCodecs.BOOL, OpenPayload::paused,
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

	private SongNetwork() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectPayload.TYPE, SelectPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ErasePayload.TYPE, ErasePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ControlPayload.TYPE, ControlPayload.CODEC);
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

	static void open(ServerPlayer player, BlockPos pos, String current, List<String> songs, int nowTenths, int totalTenths,
			boolean paused) {
		ServerPlayNetworking.send(player, new OpenPayload(pos, current, songs, nowTenths, totalTenths, paused));
	}
}
