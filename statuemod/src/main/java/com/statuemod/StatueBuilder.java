package com.statuemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1 つの像を 1 層目(いちばん下)から順に建てる作業。
 *
 * <p>配置: プレイヤーの向いている方向の 1 ブロック先から奥へ建て、像の正面はプレイヤー側を向く。
 * JSON の「行」は先頭が正面(手前)、「列」は正面から見て左から右。
 * ブロックの向き(stairs の facing など)は JSON を「北を向いて見たとき」の向きで書く。
 */
public final class StatueBuilder {

	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private final ServerLevel level;
	private final UUID owner;
	private final StatueData data;
	private final int ticksPerLayer;
	private final List<BlockPos> positions = new ArrayList<>();
	private final List<BlockState> states = new ArrayList<>();
	/** 各層の positions の終端 */
	private final List<Integer> layerEnd = new ArrayList<>();
	private final Map<BlockPos, BlockState> before = new HashMap<>();

	private int nextLayer;
	private int cooldown;

	/**
	 * @param columnsPerStep 高さが 1 層だけの像で、1 回(ticksPerLayer ごと)に建てる列数。左(プレイヤーから見て)から右へ建てる。
	 *                       0 以下なら全部を一度に建てる。高さが 2 層以上の像では使わない(下の層から 1 層ずつ)
	 */
	public StatueBuilder(ServerLevel level, UUID owner, StatueData data, BlockPos origin, Direction facing,
			int ticksPerLayer, int columnsPerStep) {
		this.level = level;
		this.owner = owner;
		this.data = data;
		this.ticksPerLayer = ticksPerLayer;

		Rotation rotation = switch (facing) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		// 前(奥)方向 d と、前を向いた人から見た右方向 r=(-dz,dx)
		int dx = facing.getStepX();
		int dz = facing.getStepZ();
		int rx = -dz;
		int rz = dx;
		int half = (data.width - 1) / 2;

		List<Integer> cols = new ArrayList<>();
		Map<Character, BlockState> cache = new HashMap<>();
		for (Map.Entry<Character, String> e : data.palette.entrySet()) {
			cache.put(e.getKey(), parse(e.getValue()).rotate(rotation));
		}

		for (int y = 0; y < data.height(); y++) {
			List<String> rows = data.layers.get(y);
			for (int row = 0; row < rows.size(); row++) {
				String line = rows.get(row);
				for (int col = 0; col < line.length(); col++) {
					char c = line.charAt(col);
					if (c == ' ') {
						continue;
					}
					BlockState state = cache.get(c);
					if (state.isAir() && !data.replaceAir) {
						continue;
					}
					int fwd = row + 1;
					int side = col - half;
					BlockPos pos = origin.offset(dx * fwd + rx * side, y, dz * fwd + rz * side);
					positions.add(pos);
					states.add(state);
					cols.add(col);
				}
			}
			layerEnd.add(positions.size());
		}

		if (data.height() == 1 && columnsPerStep > 0 && !positions.isEmpty()) {
			// 左の列から順に、columnsPerStep 列ずつ建てる
			Integer[] order = new Integer[positions.size()];
			for (int i = 0; i < order.length; i++) {
				order[i] = i;
			}
			java.util.Arrays.sort(order, (a, c2) -> Integer.compare(cols.get(a), cols.get(c2)));
			List<BlockPos> sortedPos = new ArrayList<>(order.length);
			List<BlockState> sortedStates = new ArrayList<>(order.length);
			layerEnd.clear();
			int stepStartCol = cols.get(order[0]);
			for (int i = 0; i < order.length; i++) {
				int col = cols.get(order[i]);
				if (col - stepStartCol >= columnsPerStep) {
					layerEnd.add(sortedPos.size());
					stepStartCol = col;
				}
				sortedPos.add(positions.get(order[i]));
				sortedStates.add(states.get(order[i]));
			}
			layerEnd.add(sortedPos.size());
			positions.clear();
			positions.addAll(sortedPos);
			states.clear();
			states.addAll(sortedStates);
		}
	}

	private BlockState parse(String text) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false).blockState();
		} catch (Exception e) {
			throw new IllegalArgumentException("ブロック \"" + text + "\" を読めません");
		}
	}

	/** @return 完了したら true */
	public boolean tick() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		placeLayer(nextLayer);
		nextLayer++;
		cooldown = ticksPerLayer - 1;
		if (nextLayer >= layerEnd.size()) {
			var player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[像] 「" + data.name + "」ができました!"));
			}
			return true;
		}
		return false;
	}

	private void placeLayer(int layer) {
		int from = layer == 0 ? 0 : layerEnd.get(layer - 1);
		int to = layerEnd.get(layer);
		BlockPos last = null;
		for (int i = from; i < to; i++) {
			BlockPos pos = positions.get(i);
			before.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, states.get(i), FLAGS);
			last = pos;
		}
		if (last != null) {
			level.playSound(null, last, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F + 0.04F * layer);
		}
	}

	/** 置いた分を元の状態に戻す。@return 戻したブロック数 */
	public int undo() {
		int n = 0;
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), FLAGS);
			n++;
		}
		before.clear();
		return n;
	}
}
