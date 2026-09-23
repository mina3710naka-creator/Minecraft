package com.healingblock.block;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
	 * ブロックの座標(BlockPos)ごとの「次に使える時刻(ワールドのゲーム内tick)」を
	 * 覚えておくための表。メモリ上に置いているだけの簡易実装なので、サーバーの
	 * 再起動や /reload でクールダウンはリセットされる(あとで拡張する際は
	 * ブロックエンティティ化してNBTに保存する、というのが次のステップになる)。
	 * また、座標だけで管理しているため、別のディメンションの同じ座標にもう1つ
	 * 祭壇を置いた場合はクールダウンを共有してしまう(簡易実装ゆえの制約)。
	 */
	private static final Map<Long, Long> readyAtGameTime = new HashMap<>();

	public HealingAltarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			long posKey = pos.asLong();
			long now = serverLevel.getGameTime();
			long readyAt = readyAtGameTime.getOrDefault(posKey, 0L);

			if (now < readyAt) {
				long remainingSeconds = (readyAt - now) / 20 + 1;
				serverPlayer.sendSystemMessage(
						Component.translatable("block.healingblock.healing_altar.cooldown", remainingSeconds),
						true);
				serverLevel.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			player.setHealth(player.getMaxHealth());
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(20.0F);

			readyAtGameTime.put(posKey, now + COOLDOWN_TICKS);

			serverLevel.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
			serverLevel.sendParticles(ParticleTypes.HEART,
					pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
					12, 0.4, 0.3, 0.4, 0.02);
		}
		return InteractionResult.SUCCESS;
	}
}
