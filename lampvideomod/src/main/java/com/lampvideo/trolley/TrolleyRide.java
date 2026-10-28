package com.lampvideo.trolley;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lampvideo.LampVideoMod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1回の乗車。ボタンが押されると、通路にトロッコを出して、押した人を乗せ、左の壁を向かせる。1秒たつと、一定の速さで走り出す。
 *
 * 壁のマップは、動画の1コマずつ。位置 i のマップには、「トロッコの位置 p にいるとき、フレーム(いまのコマ + i - p)」が出るようにする。
 * トロッコが1ブロック進むたびに、10 ブロック先のマップに、10 コマ先を書き込む(マップは 0.5 秒ごとに、見ている人へ送られるので、先に書く)。
 * 通路の終わりに近づいたら、手前にテレポートして、前後 10 コマのマップを、そのまま写す(見た目が変わらないので、つなぎ目が分からない)。
 * 速さは、1秒に 30 ブロック。動画も、1秒に 30 コマにして使うので、動画は、等倍の速さで流れる(音はなし)。
 * トロッコの最高速度(8 ブロック/秒)を超えるので、トロッコの物理には頼らず、毎 tick、位置を決めて、位置の通信を毎 tick 送る。
 */
final class TrolleyRide {

	private static final Logger LOG = LoggerFactory.getLogger("lampvideo-trolley");
	static final String TAG = "lampvideo_trolley";

	/** 1秒に進むブロック数(= 動画のコマ数)。トロッコの最高速度(8 ブロック/秒)を超えるので、トロッコの物理ではなく、毎 tick、位置を直接動かす。 */
	static final double SPEED = 30.0;
	private static final int AHEAD = 15;       // 先に書くコマ数(= 前後に動かす範囲)
	private static final int P0 = AHEAD + 2;   // 出発する位置
	/** 通路を、トロッコの何ブロック先まで作っておくか。動画の長さに合わせて、先へ先へと伸ばす(戻らない)。 */
	private static final int BUILD_AHEAD = 64;
	/** 伸ばした部分を、トロッコの何ブロック後ろまで残すか(それより後ろは、消す)。 */
	private static final int KEEP_BEHIND = 40;
	private static final int BUILD_PER_TICK = 4;
	private static final int START_DELAY = 20; // 乗ってから、走り出すまで(tick)

	private static final Map<BlockPos, TrolleyRide> RIDES = new HashMap<>();

	private final ServerLevel level;
	private final BlockPos station;
	private final Direction f;
	private final Direction l;
	private final TrolleyStationBlockEntity be;
	private final ServerPlayer player;
	private final Minecart cart;
	private final MapFrames frames;
	private final byte black = MapFrames.black();

	private int tick;
	private boolean running;
	private int lastP = P0;
	/** トロッコの、通路に沿った位置(ブロック単位。i のブロックの中心が i + 0.5)。 */
	private double coord = P0 + 0.5;
	private long steps;
	private int wasteTicks;
	private int framesTotal = -1;
	/** 通路を、ここまで作った(最初の通路は 0〜LENGTH-1。LENGTH は行き止まりの壁)。 */
	private int builtTo = TrolleyCorridor.LENGTH - 1;
	/** 伸ばした部分を、ここまで消した。 */
	private int cleanedTo = TrolleyCorridor.LENGTH - 1;
	private final Map<Integer, ItemFrame> frameAt = new HashMap<>();
	private boolean finishedDecode;

	private TrolleyRide(ServerLevel level, BlockPos station, Direction f, TrolleyStationBlockEntity be, ServerPlayer player,
			Minecart cart, MapFrames frames) {
		this.level = level;
		this.station = station;
		this.f = f;
		this.l = f.getCounterClockWise();
		this.be = be;
		this.player = player;
		this.cart = cart;
		this.frames = frames;
	}

	static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(TrolleyRide::tickAll);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (TrolleyRide r : new ArrayList<>(RIDES.values())) {
				r.end(false);
			}
		});
	}

	static void tryStart(ServerLevel level, BlockPos station, BlockState state) {
		if (RIDES.containsKey(station) || !(level.getBlockEntity(station) instanceof TrolleyStationBlockEntity be)) {
			return;
		}
		Player nearest = level.getNearestPlayer(station.getX() + 0.5, station.getY() + 0.5, station.getZ() + 0.5, 8.0, false);
		if (!(nearest instanceof ServerPlayer player)) {
			return;
		}
		if (be.song.isEmpty()) {
			player.sendSystemMessage(Component.literal("[トロッコムービー] 曲が選ばれていません。特殊ブロックを右クリックして、選んでください"));
			return;
		}
		Path file = TrolleyMedia.resolve(be.song);
		if (file == null) {
			player.sendSystemMessage(Component.literal("[トロッコムービー] 動画が見つかりません: " + be.song));
			return;
		}
		if (be.maps == null || be.maps.length < TrolleyCorridor.LENGTH) {
			player.sendSystemMessage(Component.literal("[トロッコムービー] 通路のマップがありません。通路を作り直してください"));
			return;
		}
		Direction f = state.getValue(TrolleyStationBlock.FACING).getOpposite();
		MapFrames frames;
		try {
			frames = new MapFrames(file, SPEED);
		} catch (IOException e) {
			player.sendSystemMessage(Component.literal("[トロッコムービー] ffmpeg を実行できません。ffmpeg をインストールしてください"));
			return;
		}
		Minecart cart = net.minecraft.world.entity.EntityTypes.MINECART.create(level, EntitySpawnReason.TRIGGERED);
		if (cart == null) {
			frames.close();
			return;
		}
		Vec3 start = railPos(station, f, P0 + 0.5);
		cart.snapTo(start.x, start.y, start.z, f.toYRot(), 0f);
		cart.addTag(TAG);
		level.addFreshEntity(cart);
		player.startRiding(cart);
		// 最初だけ、左の壁を向かせる(そのあとは、自由に見回せる)
		float yaw = f.getCounterClockWise().toYRot();
		player.setYRot(yaw);
		player.setXRot(0f);
		player.connection.send(new ClientboundPlayerRotationPacket(yaw, false, 0f, false));

		TrolleyRide ride = new TrolleyRide(level, station, f, be, player, cart, frames);
		RIDES.put(station.immutable(), ride);
		ride.ensureFrames(P0 - AHEAD, P0 + AHEAD + 12);
		for (int i = P0 - AHEAD; i < P0; i++) {
			ride.fill(i, null);   // 出発の前は、黒
		}
	}

	private static Vec3 railPos(BlockPos station, Direction f, double along) {
		return new Vec3(station.getX() + 0.5 + f.getStepX() * along, station.getY() + 0.0625, station.getZ() + 0.5 + f.getStepZ() * along);
	}

	private static void tickAll(MinecraftServer server) {
		if (RIDES.isEmpty()) {
			return;
		}
		for (TrolleyRide r : new ArrayList<>(RIDES.values())) {
			try {
				r.tick();
			} catch (RuntimeException e) {
				LOG.warn("trolley ride failed", e);
				r.end(true);
			}
		}
	}

	private void tick() {
		tick++;
		if (cart.isRemoved() || player.isRemoved()) {
			end(false);
			return;
		}
		if (!cart.hasPassenger(player)) {
			end(true);   // 降りたら、スタートに戻る
			return;
		}
		if (!running) {
			// 走り出す前: 最初の 11 コマがそろうまで待つ(最長 7 秒)
			if (tick >= START_DELAY && (frames.ready() >= AHEAD + 1 || frames.endOfStream())) {
				for (int k = 0; k <= AHEAD; k++) {
					fill(P0 + k, frames.poll());
				}
				running = true;
			} else if (tick > 140) {
				player.sendSystemMessage(Component.literal("[トロッコムービー] 動画を読み込めませんでした"));
				end(true);
			}
			return;
		}
		coord += SPEED / 20.0;
		moveCart();
		extendCorridor();
		int p = (int) Math.floor(coord);
		while (lastP < p) {
			lastP++;
			steps++;
			int e = lastP + AHEAD;
			ensureFrames(e, e + 8);
			removeFramesBefore(lastP - 20);
			if (frames.finished() || finishedDecode) {
				finishedDecode = true;
				if (framesTotal < 0) {
					framesTotal = (int) steps + AHEAD;
				}
				fill(e, null);
			} else {
				byte[] fr = frames.poll();
				if (fr == null) {
					wasteTicks++;
					fill(e, null);
				} else {
					fill(e, fr);
				}
			}
		}
		if (framesTotal >= 0 && steps > framesTotal + AHEAD + 2) {
			player.sendSystemMessage(Component.literal("[トロッコムービー] おわり"));
			end(true);
			return;
		}
	}

	/** トロッコの先(BUILD_AHEAD ブロック)まで、通路を作る。後ろ(KEEP_BEHIND より後ろ)の、伸ばした部分は消す。読み込まれていない場所は、待つ。 */
	private void extendCorridor() {
		int built = 0;
		while (built < BUILD_PER_TICK && builtTo < lastP + BUILD_AHEAD) {
			BlockPos probe = station.relative(f, builtTo + 1);
			if (!level.hasChunkAt(probe)) {
				break;
			}
			builtTo++;
			TrolleyCorridor.slice(level, station, f, builtTo, TrolleyCorridor.NORMAL);
			built++;
		}
		while (cleanedTo + 1 < lastP - KEEP_BEHIND && cleanedTo + 1 < builtTo) {
			cleanedTo++;
			if (cleanedTo >= TrolleyCorridor.LENGTH) {   // 最初の通路(0〜LENGTH-1)は、残す
				TrolleyCorridor.slice(level, station, f, cleanedTo, TrolleyCorridor.CLEAR);
			}
		}
	}

	private void removeFramesBefore(int index) {
		java.util.Iterator<Map.Entry<Integer, ItemFrame>> it = frameAt.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, ItemFrame> e = it.next();
			if (e.getKey() < index) {
				e.getValue().discard();
				it.remove();
			}
		}
	}

	/** トロッコを、coord の位置に動かして、近くのプレイヤーに、毎 tick、位置を送る(なめらかに見せるため)。 */
	private void moveCart() {
		Vec3 pos = railPos(station, f, coord);
		cart.setPos(pos.x, pos.y, pos.z);
		cart.setDeltaMovement(Vec3.ZERO);
		level.getChunkSource().sendToTrackingPlayers(cart,
				new ClientboundTeleportEntityPacket(cart.getId(), PositionMoveRotation.of(cart), Set.of(), false));
	}

	/** 書き換えたマップを、すぐ乗っている人に送る(額縁ごとの定期送信は 0.5 秒ごとで、30 ブロック/秒では、間に合わないため)。 */
	private void push(int i, MapItemSavedData d) {
		net.minecraft.network.protocol.Packet<?> pk = d.getUpdatePacket(new MapId(mapId(i)), player);
		if (pk != null) {
			player.connection.send(pk);
		}
	}

	/** 位置 i のマップの番号。通路は先へ伸びるので、用意した 199 枚を、順に使い回す(遠く離れた額縁は、残さない)。 */
	private int mapId(int i) {
		return be.maps[1 + (i - 1) % (be.maps.length - 1)];
	}

	private MapItemSavedData data(int i) {
		if (i < 1) {
			return null;
		}
		return level.getMapData(new MapId(mapId(i)));
	}

	/** 位置 i のマップに、コマ(null なら黒)を書く。変わった所だけ書く(送る量が減る)。 */
	private void fill(int i, byte[] frame) {
		MapItemSavedData d = data(i);
		if (d == null) {
			return;
		}
		for (int k = 0; k < d.colors.length; k++) {
			byte c = frame == null ? black : frame[k];
			if (d.colors[k] != c) {
				d.updateColor(k & 127, k >> 7, c);
			}
		}
		push(i, d);
	}

	/** 位置 from〜to の額縁が、なければ出す(乗っている近くだけ。遠くの区画は、読み込まれていないので)。 */
	private void ensureFrames(int from, int to) {
		for (int i = Math.max(1, from); i <= to; i++) {
			BlockPos p = station.relative(f, i).relative(l, 1).above();
			if (!level.hasChunkAt(p)) {
				continue;
			}
			List<ItemFrame> existing = level.getEntitiesOfClass(ItemFrame.class, new AABB(p));
			if (!existing.isEmpty()) {
				frameAt.putIfAbsent(i, existing.get(0));
				continue;
			}
			ItemFrame frame = new ItemFrame(level, p, l.getOpposite());
			ItemStack map = new ItemStack(Items.FILLED_MAP);
			map.set(DataComponents.MAP_ID, new MapId(mapId(i)));
			frame.setItem(map, false);
			frame.setInvisible(true);
			frame.addTag(TAG);
			level.addFreshEntity(frame);
			frameAt.put(i, frame);
		}
	}

	private void end(boolean teleportBack) {
		RIDES.remove(station);
		frames.close();
		// 額縁は、乗っている間だけ出す(消す)。伸ばした通路は、消して、最初の行き止まりの壁を戻す
		for (ItemFrame fr : frameAt.values()) {
			fr.discard();
		}
		frameAt.clear();
		if (builtTo >= TrolleyCorridor.LENGTH) {
			for (int i = Math.max(cleanedTo + 1, TrolleyCorridor.LENGTH); i <= builtTo; i++) {
				TrolleyCorridor.slice(level, station, f, i, TrolleyCorridor.CLEAR);
			}
			TrolleyCorridor.slice(level, station, f, TrolleyCorridor.LENGTH, TrolleyCorridor.CAP);
		}
		if (!cart.isRemoved()) {
			cart.ejectPassengers();
			cart.discard();
		}
		if (teleportBack && !player.isRemoved()) {
			Vec3 back = railPos(station, f, -2.5);
			player.teleportTo(back.x, station.getY(), back.z);
		}
	}
}
