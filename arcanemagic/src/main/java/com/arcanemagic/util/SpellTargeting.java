package com.arcanemagic.util;

import java.util.Optional;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 魔法の照準判定(視線上の一番近いエンティティを探す)をまとめたユーティリティ。
 * バニラの内部レイキャストAPIに依存せず、境界ボックスとの交差判定だけで組み立てている。
 */
public final class SpellTargeting {

	private SpellTargeting() {
	}

	public static Optional<LivingEntity> raycastEntity(ServerWorld world, PlayerEntity caster, double range) {
		Vec3d start = caster.getEyePos();
		Vec3d look = caster.getRotationVec(1.0F);
		Vec3d end = start.add(look.multiply(range));
		Box searchBox = caster.getBoundingBox().stretch(look.multiply(range)).expand(1.0);

		LivingEntity best = null;
		double bestDistanceSq = Double.MAX_VALUE;

		for (Entity entity : world.getOtherEntities(caster, searchBox, e -> e instanceof LivingEntity && e.isAlive())) {
			LivingEntity living = (LivingEntity) entity;
			Box box = living.getBoundingBox().expand(0.3);
			Optional<Vec3d> hit = box.raycast(start, end);
			if (hit.isPresent()) {
				double distanceSq = start.squaredDistanceTo(hit.get());
				if (distanceSq < bestDistanceSq) {
					bestDistanceSq = distanceSq;
					best = living;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	public static Vec3d forwardPoint(PlayerEntity caster, double distance) {
		return caster.getEyePos().add(caster.getRotationVec(1.0F).multiply(distance));
	}
}
