package com.arcanemagic.util;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 魔法の照準判定(視線上の一番近いエンティティを探す)をまとめたユーティリティ。
 * バニラの内部レイキャストAPIに依存せず、境界ボックスとの交差判定だけで組み立てている。
 */
public final class SpellTargeting {

	private SpellTargeting() {
	}

	public static Optional<LivingEntity> raycastEntity(ServerLevel level, Player caster, double range) {
		Vec3 start = caster.getEyePosition();
		Vec3 look = caster.getViewVector(1.0F);
		Vec3 end = start.add(look.scale(range));
		AABB searchBox = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);

		LivingEntity best = null;
		double bestDistanceSq = Double.MAX_VALUE;

		for (Entity entity : level.getEntities(caster, searchBox, e -> e instanceof LivingEntity && e.isAlive())) {
			LivingEntity living = (LivingEntity) entity;
			AABB box = living.getBoundingBox().inflate(0.3);
			Optional<Vec3> hit = box.clip(start, end);
			if (hit.isPresent()) {
				double distanceSq = start.distanceToSqr(hit.get());
				if (distanceSq < bestDistanceSq) {
					bestDistanceSq = distanceSq;
					best = living;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	public static Vec3 forwardPoint(Player caster, double distance) {
		return caster.getEyePosition().add(caster.getViewVector(1.0F).scale(distance));
	}
}
