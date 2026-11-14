package com.effectmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 付与MOD: 付与台で、アイテムに「文章で書いた効果」を付ける。 */
public class EffectMod implements ModInitializer {

	public static final String MOD_ID = "effectmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final Map<UUID, Vec3> lastPos = new HashMap<>();

	@Override
	public void onInitialize() {
		ModContent.init();
		Net.init();

		// 攻撃したとき / ダメージを受けたとき
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (Runner.busy() || taken <= 0) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer atk && source.getDirectEntity() == atk && victim != atk) {
				Runner.fireCtx(atk, Parser.Trigger.HIT, Runner.HAND, victim, taken, null, null);
			}
			if (victim instanceof ServerPlayer vp) {
				LivingEntity attacker = source.getEntity() instanceof LivingEntity l ? l : null;
				Runner.fireCtx(vp, Parser.Trigger.HURT, Runner.ALL, attacker, taken, null, null);
			}
		});
		// 倒したとき
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer p && entity != p) {
				Runner.fire(p, Parser.Trigger.KILL, Runner.HAND, entity);
			}
		});
		// 右クリック(空中 / ブロック)
		UseItemCallback.EVENT.register((player, level, hand) -> {
			use(player, level.isClientSide(), hand);
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level.getBlockState(hit.getBlockPos()).getBlock() instanceof BenchBlock)) {
				use(player, level.isClientSide(), hand);
			}
			return InteractionResult.PASS;
		});
		// ブロックを壊したとき
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (player instanceof ServerPlayer sp) {
				Runner.fireCtx(sp, Parser.Trigger.MINE, Runner.HAND, null, 0, pos, state);
			}
		});
		// 動いている間 / 装備している間 / 時間で消えるブロック
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Runner.tickPlayer(p, tick, lastPos);
			}
			Runner.tickTemps(tick);
		});
	}

	private static void use(net.minecraft.world.entity.player.Player player, boolean client, InteractionHand hand) {
		if (!client && player instanceof ServerPlayer sp) {
			Runner.fire(sp, Parser.Trigger.USE, hand == InteractionHand.MAIN_HAND ? Runner.HAND : Runner.OFFHAND, null);
		}
	}
}
