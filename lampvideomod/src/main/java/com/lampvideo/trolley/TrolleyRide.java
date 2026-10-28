package com.lampvideo.trolley;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lampvideo.LampVideoMod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
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
 * 速さは、1秒に 7.5 ブロック。動画も、1秒に 7.5 コマにして使うので、動画は、等倍の速さで流れる(音はなし)。
 */
final class TrolleyRide {

	private static final Logger LOG = LoggerFactory.getLogger("lampvideo-trolley");
	static final String TAG = "lampvideo_trolley";

	/** 1秒に進むブロック数(= 動画のコマ数)。トロッコの最高速度(8 ブロック/秒)より、少し遅い。 */
	static final double SPEED = 7.5;
	private static final int P0 = 12;          // 出発する位置
	private static final int AHEAD = 10;       // 先に書くコマ数(= 前後に動かす範囲)
	private static final int WRAP_AT = TrolleyCorridor.LENGTH - AHEAD - 2;
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
	private long steps;
	private int wasteTicks;
	private int framesTotal = -1;
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
		Minecart cart = EntityType.MINECART.create(level, EntitySpawnReason.TRIGGERED);
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
		if (cart.isRemoved() || !cart.hasPassenger(player) || player.isRemoved()) {
			end(false);
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
		Vec3 dir = new Vec3(f.getStepX(), 0, f.getStepZ());
		cart.setDeltaMovement(dir.scale(SPEED / 20.0));
		double coord = (cart.getX() - station.getX() - 0.5) * f.getStepX() + (cart.getZ() - station.getZ() - 0.5) * f.getStepZ();
		int p = (int) Math.floor(coord);
		while (lastP < p) {
			lastP++;
			steps++;
			int e = lastP + AHEAD;
			ensureFrames(e, e + 3);
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
		if (lastP >= WRAP_AT) {
			wrap();
		}
	}

	/** 通路の終わりの手前で、手前に戻る。前後 AHEAD ブロックのマップを写してから動かすので、見た目は変わらない。 */
	private void wrap() {
		int delta = lastP - P0;
		for (int j = -AHEAD; j <= AHEAD; j++) {
			copyMap(lastP + j, P0 + j);
		}
		ensureFrames(P0 - AHEAD, P0 + AHEAD + 12);
		Vec3 pos = cart.position();
		cart.snapTo(pos.x - f.getStepX() * delta, pos.y, pos.z - f.getStepZ() * delta, cart.getYRot(), cart.getXRot());
		lastP -= delta;
	}

	private void copyMap(int from, int to) {
		MapItemSavedData a = data(from);
		MapItemSavedData b = data(to);
		if (a == null || b == null) {
			return;
		}
		byte[] src = a.colors.clone();
		for (int k = 0; k < src.length; k++) {
			if (b.colors[k] != src[k]) {
				b.setColor(k & 127, k >> 7, src[k]);
			}
		}
	}

	private MapItemSavedData data(int i) {
		if (i < 1 || i >= be.maps.length) {
			return null;
		}
		return level.getMapData(new MapId(be.maps[i]));
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
				d.setColor(k & 127, k >> 7, c);
			}
		}
	}

	/** 位置 from〜to の額縁が、なければ出す(乗っている近くだけ。遠くの区画は、読み込まれていないので)。 */
	private void ensureFrames(int from, int to) {
		for (int i = Math.max(1, from); i <= Math.min(TrolleyCorridor.LENGTH - 1, to); i++) {
			BlockPos p = station.relative(f, i).relative(l, 1).above();
			if (!level.hasChunkAt(p)) {
				continue;
			}
			List<ItemFrame> existing = level.getEntitiesOfClass(ItemFrame.class, new AABB(p));
			if (!existing.isEmpty()) {
				continue;
			}
			ItemFrame frame = new ItemFrame(level, p, l.getOpposite());
			ItemStack map = new ItemStack(Items.FILLED_MAP);
			map.set(DataComponents.MAP_ID, new MapId(be.maps[i]));
			frame.setItem(map, false);
			frame.setInvisible(true);
			frame.addTag(TAG);
			level.addFreshEntity(frame);
		}
	}

	private void end(boolean teleportBack) {
		RIDES.remove(station);
		frames.close();
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
