package com.lampvideo.pc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** PC の状態: 持ち主、パスワード(ソルト付きハッシュ)、管理下のディスプレイの位置。 */
public class PcBlockEntity extends BlockEntity {

	public String owner = "";
	private String salt = "";
	private String hash = "";
	public final List<BlockPos> linked = new ArrayList<>();
	/** 解錠済みのプレイヤー(サーバーを動かしている間だけ。パスワードを変えると空になる)。 */
	private final Set<UUID> authorized = new HashSet<>();
	/** 失敗の続けざまの入力を防ぐ: プレイヤーごとの、次に試せる時刻。 */
	private final java.util.Map<UUID, Long> nextTry = new java.util.HashMap<>();

	public PcBlockEntity(BlockPos pos, BlockState state) {
		super(ModPc.PC_ENTITY, pos, state);
	}

	/** 壊れて(なくなって)いる画面を、接続から外す。読み込まれていない場所は、そのままにする。 */
	public void purge(net.minecraft.world.level.Level level) {
		boolean changed = linked.removeIf(p -> level.hasChunkAt(p)
				&& !com.lampvideo.display.DisplayGeometry.isDisplay(level.getBlockState(p)));
		if (changed) {
			setChanged();
		}
	}

	public boolean hasPassword() {
		return !hash.isEmpty();
	}

	public boolean isOwner(ServerPlayer p) {
		return owner.isEmpty() || owner.equals(p.getUUID().toString());
	}

	public boolean canUse(ServerPlayer p) {
		return !hasPassword() || isOwner(p) || authorized.contains(p.getUUID());
	}

	/** パスワードを試す。合っていれば、そのプレイヤーを解錠済みにする。失敗が続くときは、少し待たせる。 */
	public boolean tryPassword(ServerPlayer p, String password) {
		long now = System.currentTimeMillis();
		if (nextTry.getOrDefault(p.getUUID(), 0L) > now) {
			return false;
		}
		if (hash.equals(hash(salt, password))) {
			authorized.add(p.getUUID());
			nextTry.remove(p.getUUID());
			return true;
		}
		nextTry.put(p.getUUID(), now + 2000);
		return false;
	}

	/** パスワードを設定する(空なら解除)。 */
	public void setPassword(String password) {
		authorized.clear();
		if (password == null || password.isEmpty()) {
			salt = "";
			hash = "";
		} else {
			byte[] b = new byte[12];
			new SecureRandom().nextBytes(b);
			salt = java.util.HexFormat.of().formatHex(b);
			hash = hash(salt, password);
		}
		setChanged();
	}

	private static String hash(String salt, String password) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return java.util.HexFormat.of().formatHex(md.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		out.putString("owner", owner);
		out.putString("salt", salt);
		out.putString("hash", hash);
		StringBuilder sb = new StringBuilder();
		for (BlockPos p : linked) {
			sb.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append(';');
		}
		out.putString("linked", sb.toString());
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		owner = in.getStringOr("owner", "");
		salt = in.getStringOr("salt", "");
		hash = in.getStringOr("hash", "");
		linked.clear();
		for (String part : in.getStringOr("linked", "").split(";")) {
			String[] c = part.split(",");
			if (c.length == 3) {
				try {
					linked.add(new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])));
				} catch (NumberFormatException e) {
					// 壊れた記録は読み飛ばす
				}
			}
		}
	}
}
