package com.lampvideo.pc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lampvideo.display.DisplayGeometry;
import com.lampvideo.display.DisplayBlockEntity;
import com.lampvideo.display.StandDisplayBlock;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 管理パネル(サーバー側)。近く(48ブロック)の PC と画面を一覧にして、
 * 画面を1枚ずつ好きな PC に接続/解除したり、画面ごとの名前・音量・ミュートを設定したり、PC につながる画面をまとめて止めたりできる。
 */
public final class PanelNetwork {

	private static final int RANGE = 48;

	private PanelNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(PanelRequestPayload.TYPE, PanelRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PanelStatePayload.TYPE, PanelStatePayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PanelRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> handle(player, payload));
		});
	}

	public static void open(ServerPlayer player, BlockPos panel, String message) {
		sendState(player, panel, message);
	}

	private static List<BlockEntity> around(ServerLevel level, BlockPos center) {
		List<BlockEntity> out = new ArrayList<>();
		for (int cx = (center.getX() - RANGE) >> 4; cx <= (center.getX() + RANGE) >> 4; cx++) {
			for (int cz = (center.getZ() - RANGE) >> 4; cz <= (center.getZ() + RANGE) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
					if (e.getKey().distSqr(center) <= (double) RANGE * RANGE && !e.getValue().isRemoved()) {
						out.add(e.getValue());
					}
				}
			}
		}
		return out;
	}

	private static void sendState(ServerPlayer player, BlockPos panel, String message) {
		ServerLevel level = (ServerLevel) player.level();
		List<PcBlockEntity> pcs = new ArrayList<>();
		List<DisplayBlockEntity> displays = new ArrayList<>();
		for (BlockEntity be : around(level, panel)) {
			if (be instanceof PcBlockEntity pc) {
				pcs.add(pc);
			} else if (be instanceof DisplayBlockEntity d && DisplayGeometry.primaryOf(level, d.getBlockPos()).equals(d.getBlockPos())) {
				displays.add(d);
			}
		}
		pcs.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(panel), b.getBlockPos().distSqr(panel)));
		displays.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(panel), b.getBlockPos().distSqr(panel)));
		List<PanelStatePayload.PcInfo> pcInfo = new ArrayList<>();
		for (PcBlockEntity pc : pcs) {
			pcInfo.add(new PanelStatePayload.PcInfo(pc.getBlockPos(), pc.linked.size(),
					pc.hasPassword() && !pc.canUse(player), pc.isOwner(player)));
		}
		List<PanelStatePayload.DisplayInfo> dInfo = new ArrayList<>();
		for (DisplayBlockEntity d : displays) {
			int index = -1;
			for (int i = 0; i < pcs.size(); i++) {
				if (pcs.get(i).linked.contains(d.getBlockPos())) {
					index = i;
					break;
				}
			}
			int[] size = DisplayGeometry.sizeOf(level, d.getBlockPos());
			dInfo.add(new PanelStatePayload.DisplayInfo(d.getBlockPos(), d.label, size[0], size[1],
					d.getBlockState().getBlock() instanceof StandDisplayBlock, d.state, d.volume, d.muted, index));
		}
		ServerPlayNetworking.send(player, new PanelStatePayload(panel, pcInfo, dInfo, message));
	}

	private static PcBlockEntity pcAt(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof PcBlockEntity pc ? pc : null;
	}

	/** display を管理している PC(なければ null)。 */
	private static PcBlockEntity managerOf(ServerLevel level, BlockPos panel, BlockPos display) {
		for (BlockEntity be : around(level, panel)) {
			if (be instanceof PcBlockEntity pc && pc.linked.contains(display)) {
				return pc;
			}
		}
		return null;
	}

	private static void handle(ServerPlayer player, PanelRequestPayload req) {
		ServerLevel level = (ServerLevel) player.level();
		if (player.distanceToSqr(req.panel().getX() + 0.5, req.panel().getY() + 0.5, req.panel().getZ() + 0.5) > 64.0 * 64.0) {
			return;
		}
		String message = "";
		if (req.action() == PanelRequestPayload.OPEN_PC) {
			PcBlockEntity pc = pcAt(level, req.pc());
			if (pc != null) {
				PcNetwork.open(player, req.pc(), 1, "");
			}
			return;
		}
		if (req.action() != PanelRequestPayload.REFRESH) {
			message = apply(level, player, req);
		}
		sendState(player, req.panel(), message);
	}

	private static String apply(ServerLevel level, ServerPlayer player, PanelRequestPayload req) {
		DisplayBlockEntity display = level.getBlockEntity(req.display()) instanceof DisplayBlockEntity d ? d : null;
		switch (req.action()) {
			case PanelRequestPayload.LINK -> {
				PcBlockEntity pc = pcAt(level, req.pc());
				if (pc == null || display == null) {
					return "";
				}
				if (!pc.isOwner(player)) {
					return "画面を接続できるのは、その PC の持ち主だけです";
				}
				PcBlockEntity old = managerOf(level, req.panel(), req.display());
				if (old != null && old != pc) {
					if (!old.isOwner(player)) {
						return "この画面は、他人の PC に接続されています";
					}
					old.linked.remove(req.display());
					old.setChanged();
				}
				if (!pc.linked.contains(req.display())) {
					pc.linked.add(req.display());
					pc.setChanged();
				}
				display.managed = true;
				display.setChanged();
				return "画面を PC に接続しました";
			}
			case PanelRequestPayload.UNLINK -> {
				PcBlockEntity pc = managerOf(level, req.panel(), req.display());
				if (display == null) {
					return "";
				}
				if (pc != null) {
					if (!pc.isOwner(player)) {
						return "接続を解除できるのは、その PC の持ち主だけです";
					}
					pc.linked.remove(req.display());
					pc.setChanged();
				}
				display.managed = false;
				display.setChanged();
				return "接続を解除しました";
			}
			case PanelRequestPayload.LABEL, PanelRequestPayload.VOLUME, PanelRequestPayload.MUTE -> {
				if (display == null) {
					return "";
				}
				PcBlockEntity pc = managerOf(level, req.panel(), req.display());
				if (pc != null && !pc.canUse(player)) {
					return "この画面を管理している PC がロックされています";
				}
				switch (req.action()) {
					case PanelRequestPayload.LABEL -> display.setSettings(req.text(), display.volume, display.muted);
					case PanelRequestPayload.VOLUME -> display.setSettings(display.label, req.value(), display.muted);
					default -> display.setSettings(display.label, display.volume, !display.muted);
				}
				return "";
			}
			case PanelRequestPayload.STOP_ALL, PanelRequestPayload.PAUSE_ALL -> {
				PcBlockEntity pc = pcAt(level, req.pc());
				if (pc == null) {
					return "";
				}
				if (!pc.canUse(player)) {
					return "ロックされています。PC の操作画面でパスワードを入力してください";
				}
				for (BlockPos p : pc.linked) {
					if (level.getBlockEntity(p) instanceof DisplayBlockEntity d) {
						if (req.action() == PanelRequestPayload.STOP_ALL) {
							d.stop();
						} else {
							d.togglePause();
						}
					}
				}
				return "";
			}
			default -> {
				return "";
			}
		}
	}
}
