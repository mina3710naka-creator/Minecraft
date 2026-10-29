package com.povreplay.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.povreplay.ReplayMode;
import com.povreplay.SessionLog;
import com.povreplay.Sessions;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.ProtocolInfo;

/**
 * サーバーから届いた通信を、デコードする直前のバイト列のまま、記録する(ゲームの動きは変えない)。
 * 通信の段階(設定 → ゲーム)が変わった印も、あわせて記録する。
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

	@Unique
	private SessionLog povreplay$log;

	@Inject(method = "configurePacketHandler", at = @At("TAIL"))
	private void povreplay$tap(ChannelPipeline pipeline, CallbackInfo ci) {
		Connection self = (Connection) (Object) this;
		// クライアントの受信側は、はじめは "inbound_config"(まだ段階が決まっていない)で、あとで "decoder" に置き換わる。その手前に付ける
		String anchor = pipeline.get("decoder") != null ? "decoder" : (pipeline.get("inbound_config") != null ? "inbound_config" : null);
		if (ReplayMode.active() || self.getReceiving() != PacketFlow.CLIENTBOUND || anchor == null) {
			return;
		}
		SessionLog log = Sessions.begin();
		if (log == null) {
			return;
		}
		povreplay$log = log;
		pipeline.addBefore(anchor, "povreplay_tap", new ChannelInboundHandlerAdapter() {
			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
				if (msg instanceof ByteBuf buf) {
					byte[] bytes = new byte[buf.readableBytes()];
					buf.getBytes(buf.readerIndex(), bytes);
					log.add(SessionLog.FRAME, bytes);
				}
				ctx.fireChannelRead(msg);
			}
		});
	}

	@Inject(method = "setupInboundProtocol", at = @At("HEAD"))
	private <T extends PacketListener> void povreplay$protocol(ProtocolInfo<T> protocol, T listener, CallbackInfo ci) {
		if (povreplay$log != null) {
			povreplay$log.add(SessionLog.PROTOCOL, protocol.id().name().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
		}
	}
}
