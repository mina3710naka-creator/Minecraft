package com.lampvideo.pc;

import java.util.ArrayList;
import java.util.List;

import com.lampvideo.LampVideoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** サーバー→クライアント: 管理パネルの画面(近くの PC と画面の一覧)を開く/更新する。 */
public record PanelStatePayload(BlockPos panel, List<PcInfo> pcs, List<DisplayInfo> displays, String message)
		implements CustomPacketPayload {

	/** locked = パスワードがあって、このプレイヤーは未解錠。 */
	public record PcInfo(BlockPos pos, int linked, boolean locked, boolean owner) {
	}

	/** pcIndex = 接続されている PC の番号(pcs の中での位置)。-1 なら未接続。 */
	public record DisplayInfo(BlockPos pos, String label, int cols, int rows, boolean stand, int state, int volume,
			boolean muted, int pcIndex) {
	}

	public static final CustomPacketPayload.Type<PanelStatePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "panel_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PanelStatePayload> STREAM_CODEC = StreamCodec.of(
			(buf, p) -> {
				BlockPos.STREAM_CODEC.encode(buf, p.panel);
				buf.writeVarInt(p.pcs.size());
				for (PcInfo i : p.pcs) {
					BlockPos.STREAM_CODEC.encode(buf, i.pos);
					buf.writeVarInt(i.linked);
					buf.writeBoolean(i.locked);
					buf.writeBoolean(i.owner);
				}
				buf.writeVarInt(p.displays.size());
				for (DisplayInfo d : p.displays) {
					BlockPos.STREAM_CODEC.encode(buf, d.pos);
					ByteBufCodecs.STRING_UTF8.encode(buf, d.label);
					buf.writeVarInt(d.cols);
					buf.writeVarInt(d.rows);
					buf.writeBoolean(d.stand);
					buf.writeVarInt(d.state);
					buf.writeVarInt(d.volume);
					buf.writeBoolean(d.muted);
					buf.writeVarInt(d.pcIndex + 1);
				}
				ByteBufCodecs.STRING_UTF8.encode(buf, p.message);
			},
			buf -> {
				BlockPos panel = BlockPos.STREAM_CODEC.decode(buf);
				int n = buf.readVarInt();
				List<PcInfo> pcs = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					pcs.add(new PcInfo(BlockPos.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
				}
				int m = buf.readVarInt();
				List<DisplayInfo> displays = new ArrayList<>();
				for (int i = 0; i < m; i++) {
					displays.add(new DisplayInfo(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
							buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
							buf.readBoolean(), buf.readVarInt() - 1));
				}
				return new PanelStatePayload(panel, pcs, displays, ByteBufCodecs.STRING_UTF8.decode(buf));
			});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
