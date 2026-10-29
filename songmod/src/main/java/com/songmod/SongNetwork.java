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
	public record OpenPayload(BlockPos pos, String current, List<String> songs) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<OpenPayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SongMod.MOD_ID, "selector_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenPayload::pos,
				ByteBufCodecs.STRING_UTF8, OpenPayload::current,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenPayload::songs,
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

	private SongNetwork() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectPayload.TYPE, SelectPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> SongSelector.select(player, payload.pos(), payload.name()));
		});
	}

	static void open(ServerPlayer player, BlockPos pos, String current, List<String> songs) {
		ServerPlayNetworking.send(player, new OpenPayload(pos, current, songs));
	}
}
