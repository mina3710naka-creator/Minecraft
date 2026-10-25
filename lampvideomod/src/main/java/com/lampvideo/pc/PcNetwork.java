package com.lampvideo.pc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lampvideo.display.DisplayGeometry;
import com.lampvideo.display.DisplayBlockEntity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;

/** PC の操作(サーバー側)。操作の窓口(PC・キーボード・管理パネル・リモコン)は、近くの PC を探して、その操作画面を開く。 */
public final class PcNetwork {

	/** リモコンで操作できる範囲。 */
	public static final int REMOTE_RANGE = 32;
	/** 「近くの画面を登録」で探す範囲(PC から)。 */
	private static final int LINK_RANGE = 24;

	private PcNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(PcRequestPayload.TYPE, PcRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PcStatePayload.TYPE, PcStatePayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PcRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> handle(player, payload));
		});
	}

	/** near の近く(range ブロック以内)で一番近い PC を探して、その操作画面を開く。 */
	public static void open(ServerPlayer player, BlockPos near, int range, String message) {
		PcBlockEntity pc = nearestPc((ServerLevel) player.level(), near, range);
		if (pc == null) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("近く(" + range + "ブロック以内)に PC がありません"));
			return;
		}
		sendState(player, pc, message);
	}

	private static List<BlockEntity> blockEntitiesAround(ServerLevel level, BlockPos center, int range) {
		List<BlockEntity> out = new ArrayList<>();
		int cx0 = (center.getX() - range) >> 4;
		int cx1 = (center.getX() + range) >> 4;
		int cz0 = (center.getZ() - range) >> 4;
		int cz1 = (center.getZ() + range) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
					if (e.getKey().distSqr(center) <= (double) range * range) {
						out.add(e.getValue());
					}
				}
			}
		}
		return out;
	}

	/** キーボードから操作できる範囲(PC か、登録された画面の下の真ん中から)。 */
	public static final int KEYBOARD_RANGE = 8;

	/** 画面(グループ)の、真ん中の一番下の位置。 */
	private static net.minecraft.world.phys.Vec3 bottomCenter(ServerLevel level, BlockPos primary) {
		net.minecraft.world.level.block.state.BlockState s = level.getBlockState(primary);
		net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(primary);
		if (!DisplayGeometry.isDisplay(s)) {
			return c;
		}
		net.minecraft.core.Vec3i r = DisplayGeometry.rightOffset(s);
		net.minecraft.core.Vec3i u = DisplayGeometry.upOffset(s);
		int[] size = DisplayGeometry.sizeOf(level, primary);
		double dx = r.getX() * (size[0] - 1) / 2.0 - u.getX() * (size[1] - 1) - u.getX() * 0.5;
		double dy = r.getY() * (size[0] - 1) / 2.0 - u.getY() * (size[1] - 1) - u.getY() * 0.5;
		double dz = r.getZ() * (size[0] - 1) / 2.0 - u.getZ() * (size[1] - 1) - u.getZ() * 0.5;
		return c.add(dx, dy, dz);
	}

	/** キーボードの操作画面を開く。PC か、登録された画面の下の真ん中から 8 ブロック以内の PC のうち、一番近いもの。 */
	public static void openFromKeyboard(ServerPlayer player, BlockPos keyboard) {
		ServerLevel level = (ServerLevel) player.level();
		net.minecraft.world.phys.Vec3 k = net.minecraft.world.phys.Vec3.atCenterOf(keyboard);
		PcBlockEntity best = null;
		double bestDist = (double) KEYBOARD_RANGE * KEYBOARD_RANGE;
		for (BlockEntity be : blockEntitiesAround(level, keyboard, 48)) {
			if (!(be instanceof PcBlockEntity pc) || pc.isRemoved()) {
				continue;
			}
			pc.purge(level);
			double d = k.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pc.getBlockPos()));
			for (BlockPos p : pc.linked) {
				if (level.hasChunkAt(p)) {
					d = Math.min(d, k.distanceToSqr(bottomCenter(level, p)));
				}
			}
			if (d <= bestDist) {
				bestDist = d;
				best = pc;
			}
		}
		if (best == null) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"キーボードから " + KEYBOARD_RANGE + " ブロック以内に、PC か、登録された画面がありません"));
			return;
		}
		sendState(player, best, "");
	}

	/** ディスプレイが壊されたら、PC の接続を整理する。残った隣のパネルがあれば、そちらに引き継ぎ、なければ接続を解除する。 */
	public static void onDisplayRemoved(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState old) {
		// チャンクの解放など、ブロックが残っている場合は、何もしない
		if (DisplayGeometry.isDisplay(level.getBlockState(pos))) {
			return;
		}
		for (BlockEntity be : blockEntitiesAround(level, pos, 64)) {
			if (!(be instanceof PcBlockEntity pc) || !pc.linked.contains(pos)) {
				continue;
			}
			pc.linked.remove(pos);
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
				BlockPos q = pos.relative(dir);
				net.minecraft.world.level.block.state.BlockState ns = level.getBlockState(q);
				if (DisplayGeometry.canConnect(old, ns, dir.getUnitVec3i())) {
					BlockPos np = DisplayGeometry.primaryOf(level, q);
					if (!pc.linked.contains(np) && level.getBlockEntity(np) instanceof DisplayBlockEntity nd) {
						pc.linked.add(np);
						nd.managed = true;
						nd.setChanged();
					}
				}
			}
			pc.setChanged();
		}
	}

	public static PcBlockEntity nearestPc(ServerLevel level, BlockPos near, int range) {
		PcBlockEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockEntity be : blockEntitiesAround(level, near, range)) {
			if (be instanceof PcBlockEntity pc && !pc.isRemoved()) {
				double d = pc.getBlockPos().distSqr(near);
				if (d < bestDist) {
					bestDist = d;
					best = pc;
				}
			}
		}
		return best;
	}

	private static void sendState(ServerPlayer player, PcBlockEntity pc, String message) {
		pc.purge(pc.getLevel());
		DisplayBlockEntity first = null;
		for (BlockPos p : pc.linked) {
			if (pc.getLevel().getBlockEntity(p) instanceof DisplayBlockEntity be) {
				first = be;
				break;
			}
		}
		boolean authed = pc.canUse(player);
		ServerPlayNetworking.send(player, new PcStatePayload(pc.getBlockPos(), pc.hasPassword(), authed, pc.isOwner(player),
				pc.linked.size(), first == null ? DisplayBlockEntity.STOPPED : first.state, message));
	}

	private static void handle(ServerPlayer player, PcRequestPayload req) {
		ServerLevel level = (ServerLevel) player.level();
		if (player.distanceToSqr(req.pc().getX() + 0.5, req.pc().getY() + 0.5, req.pc().getZ() + 0.5) > 40.0 * 40.0) {
			return;
		}
		if (!(level.getBlockEntity(req.pc()) instanceof PcBlockEntity pc)) {
			return;
		}
		pc.purge(level);
		String message = "";
		switch (req.action()) {
			case PcRequestPayload.AUTH -> message = pc.tryPassword(player, req.text())
					? "解錠しました" : "パスワードが違います(2秒待ってからもう一度)";
			case PcRequestPayload.REFRESH -> { }
			default -> {
				if (!pc.canUse(player)) {
					message = "ロックされています。パスワードを入力してください";
				} else {
					message = control(level, player, pc, req);
				}
			}
		}
		sendState(player, pc, message);
	}

	private static String control(ServerLevel level, ServerPlayer player, PcBlockEntity pc, PcRequestPayload req) {
		switch (req.action()) {
			case PcRequestPayload.SET_PASSWORD -> {
				if (!pc.isOwner(player)) {
					return "パスワードを変えられるのは、PC の持ち主だけです";
				}
				pc.setPassword(req.text());
				return req.text().isEmpty() ? "パスワードを解除しました" : "パスワードを設定しました";
			}
			case PcRequestPayload.LINK_NEARBY -> {
				if (!pc.isOwner(player)) {
					return "画面を登録できるのは、PC の持ち主だけです";
				}
				int added = 0;
				for (BlockEntity be : blockEntitiesAround(level, pc.getBlockPos(), LINK_RANGE)) {
					if (be instanceof DisplayBlockEntity d && !d.isRemoved()) {
						BlockPos primary = DisplayGeometry.primaryOf(level, d.getBlockPos());
						if (!pc.linked.contains(primary) && level.getBlockEntity(primary) instanceof DisplayBlockEntity pd) {
							pc.linked.add(primary);
							pd.managed = true;
							pd.setChanged();
							added++;
						}
					}
				}
				pc.setChanged();
				return added + " 枚の画面を登録しました(合計 " + pc.linked.size() + ")";
			}
			case PcRequestPayload.UNLINK_ALL -> {
				if (!pc.isOwner(player)) {
					return "登録を解除できるのは、PC の持ち主だけです";
				}
				for (BlockPos p : pc.linked) {
					if (level.getBlockEntity(p) instanceof DisplayBlockEntity d) {
						d.managed = false;
						d.setChanged();
					}
				}
				pc.linked.clear();
				pc.setChanged();
				return "登録を解除しました";
			}
			default -> { }
		}
		int mode = Math.max(0, Math.min(4, req.mode()));
		int applied = 0;
		for (BlockPos p : pc.linked) {
			if (!(level.getBlockEntity(p) instanceof DisplayBlockEntity d)) {
				continue;
			}
			switch (req.action()) {
				case PcRequestPayload.PLAY -> d.play(com.lampvideo.display.ModDisplay.safeName(req.text()), mode);
				case PcRequestPayload.STOP -> d.stop();
				case PcRequestPayload.PAUSE -> d.togglePause();
				case PcRequestPayload.SEEK -> d.seek(Math.max(-600_000, Math.min(600_000, req.value())));
				case PcRequestPayload.MODE -> d.setMode(mode);
				default -> { continue; }
			}
			applied++;
		}
		return pc.linked.isEmpty() ? "画面が登録されていません(持ち主が「近くの画面を登録」)" : "";
	}
}
