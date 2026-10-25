package com.lampvideo.pc;

import com.lampvideo.LampVideoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** クライアント→サーバー: PC への操作。 */
public record PcRequestPayload(BlockPos pc, int action, String text, int value, int mode) implements CustomPacketPayload {

	public static final int AUTH = 0;
	/** text = 新しいパスワード(空なら解除)。持ち主だけ。 */
	public static final int SET_PASSWORD = 1;
	/** (使わない。以前の「近くの画面を登録」。番号は、そのまま空けてある) */
	public static final int LINK_NEARBY = 2;
	public static final int UNLINK_ALL = 3;
	/** text = ファイル名、mode = 表示モード。 */
	public static final int PLAY = 4;
	public static final int STOP = 5;
	public static final int PAUSE = 6;
	/** value = 動かす量(ミリ秒)。 */
	public static final int SEEK = 7;
	public static final int MODE = 8;
	/** 状態の取得(画面の開き直し)。 */
	public static final int REFRESH = 9;

	public static final CustomPacketPayload.Type<PcRequestPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "pc_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PcRequestPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, PcRequestPayload::pc,
			ByteBufCodecs.VAR_INT, PcRequestPayload::action,
			ByteBufCodecs.STRING_UTF8, PcRequestPayload::text,
			ByteBufCodecs.VAR_INT, PcRequestPayload::value,
			ByteBufCodecs.VAR_INT, PcRequestPayload::mode,
			PcRequestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
