package com.healingblock.block;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 右クリックすると、体力と満腹度を全回復させるブロック。使うと一定時間クールダウンする。 */
public class HealingAltarBlock extends Block {

	/** クールダウン(tick単位)。20 tick = 1 秒なので 600 tick = 30 秒。 */
	private static final int COOLDOWN_TICKS = 600;

	/**
	 * ブロックごとの「次に使える時刻(ワールドのゲーム内tick)」を覚えておくための表。
	 * メモリ上に置いているだけの簡易実装なので、サーバーの再起動や /reload で
	 * クールダウンはリセットされる(あとで拡張する際はブロックエンティティ化して
	 * NBT に保存する、というのが次のステップになる)。
	 */
	private static final Map<String, Long> readyAtGameTime = new HashMap<>();

	public HealingAltarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
			String key = cooldownKey(serverLevel, pos);
			long now = serverLevel.getGameTime();
			long readyAt = readyAtGameTime.getOrDefault(key, 0L);

			if (now < readyAt) {
				long remainingSeconds = (readyAt - now) / 20 + 1;
				player.displayClientMessage(
						Component.translatable("block.healingblock.healing_altar.cooldown", remainingSeconds),
						true);
				serverLevel.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			player.setHealth(player.getMaxHealth());
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(20.0F);

			readyAtGameTime.put(key, now + COOLDOWN_TICKS);

			serverLevel.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
			serverLevel.sendParticles(ParticleTypes.HEART,
					pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
					12, 0.4, 0.3, 0.4, 0.02);
		}
		return InteractionResult.SUCCESS;
	}

	private static String cooldownKey(ServerLevel level, BlockPos pos) {
		return level.dimension().location() + "@" + pos.asLong();
	}
}
