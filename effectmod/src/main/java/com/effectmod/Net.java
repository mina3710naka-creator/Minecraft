package com.effectmod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** 通信: 付与の操作(クライアント → サーバー)と結果表示(サーバー → クライアント)。 */
public final class Net {

	/** mode 0 = 文章で効果を付与 / 1 = すべて消す。 */
	public record ApplyPayload(int mode, String text) implements CustomPacketPayload {
		public static final Type<ApplyPayload> TYPE = new Type<>(ModContent.id("apply"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ApplyPayload::mode, ByteBufCodecs.STRING_UTF8, ApplyPayload::text, ApplyPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ResultPayload(String message, boolean ok) implements CustomPacketPayload {
		public static final Type<ResultPayload> TYPE = new Type<>(ModContent.id("result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ResultPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ResultPayload::message, ByteBufCodecs.BOOL, ResultPayload::ok, ResultPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private Net() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(ApplyPayload.TYPE, ApplyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ResultPayload.TYPE, ResultPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ApplyPayload.TYPE, (pl, ctx) -> {
			ServerPlayer p = ctx.player();
			ctx.server().execute(() -> {
				if (p.containerMenu instanceof BenchMenu m) {
					m.apply(p, pl.mode(), pl.text());
				}
			});
		});
	}
}
