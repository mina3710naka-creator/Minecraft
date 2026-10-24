package com.lampvideo.pc;

import com.lampvideo.LampVideoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** クライアント→サーバー: 管理パネルでの操作。 */
public record PanelRequestPayload(BlockPos panel, int action, BlockPos pc, BlockPos display, String text, int value)
		implements CustomPacketPayload {

	/** display を pc に接続する(持ち主だけ)。 */
	public static final int LINK = 0;
	/** display の接続を解除する。 */
	public static final int UNLINK = 1;
	/** display の名前(text)。 */
	public static final int LABEL = 2;
	/** display の音量(value は 0〜100)。 */
	public static final int VOLUME = 3;
	public static final int MUTE = 4;
	/** pc に接続された画面を、全部止める / 一時停止⇔再開。 */
	public static final int STOP_ALL = 5;
	public static final int PAUSE_ALL = 6;
	/** pc の操作画面を開く。 */
	public static final int OPEN_PC = 7;
	public static final int REFRESH = 8;

	public static final CustomPacketPayload.Type<PanelRequestPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "panel_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PanelRequestPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, PanelRequestPayload::panel,
			ByteBufCodecs.VAR_INT, PanelRequestPayload::action,
			BlockPos.STREAM_CODEC, PanelRequestPayload::pc,
			BlockPos.STREAM_CODEC, PanelRequestPayload::display,
			ByteBufCodecs.STRING_UTF8, PanelRequestPayload::text,
			ByteBufCodecs.VAR_INT, PanelRequestPayload::value,
			PanelRequestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
