package com.lampvideo.trolley;

import java.util.List;

import com.lampvideo.LampVideoMod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** トロッコムービーの特殊ブロックの、曲選びの通信。 */
public final class TrolleyNetwork {

	/** サーバー→クライアント: 曲選びの画面を開く。 */
	public record OpenPayload(BlockPos pos, String current, List<String> files) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<OpenPayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "trolley_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenPayload::pos,
				ByteBufCodecs.STRING_UTF8, OpenPayload::current,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenPayload::files,
				OpenPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** クライアント→サーバー: 曲を選ぶ。 */
	public record SelectPayload(BlockPos pos, String name) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<SelectPayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "trolley_select"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SelectPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, SelectPayload::pos,
				ByteBufCodecs.STRING_UTF8, SelectPayload::name,
				SelectPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** サーバー→クライアント: 通路の終わりで手前に戻ったとき。補間(なめらかに動かす処理)をやめて、新しい位置へ一瞬で移す。 */
	public record WrapPayload(double x, double y, double z) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<WrapPayload> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "trolley_wrap"));
		public static final StreamCodec<RegistryFriendlyByteBuf, WrapPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.DOUBLE, WrapPayload::x,
				ByteBufCodecs.DOUBLE, WrapPayload::y,
				ByteBufCodecs.DOUBLE, WrapPayload::z,
				WrapPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private TrolleyNetwork() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WrapPayload.TYPE, WrapPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectPayload.TYPE, SelectPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> select(player, payload.pos(), payload.name()));
		});
	}

	static void open(ServerPlayer player, BlockPos pos) {
		if (player.level().getBlockEntity(pos) instanceof TrolleyStationBlockEntity be) {
			ServerPlayNetworking.send(player, new OpenPayload(pos, be.song, TrolleyMedia.list()));
		}
	}

	private static void select(ServerPlayer player, BlockPos pos, String name) {
		if (!(player.level() instanceof ServerLevel level) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
			return;
		}
		if (!(level.getBlockEntity(pos) instanceof TrolleyStationBlockEntity be)) {
			return;
		}
		if (TrolleyMedia.resolve(name) == null) {
			player.sendSystemMessage(Component.literal("[トロッコムービー] その動画が見つかりません: " + name));
			return;
		}
		be.song = name.substring(name.replace('\\', '/').lastIndexOf('/') + 1);
		be.setChanged();
		TrolleyCorridor.updateSign(level, pos);
		player.sendSystemMessage(Component.literal("[トロッコムービー] 曲を選びました: " + be.song));
	}
}
