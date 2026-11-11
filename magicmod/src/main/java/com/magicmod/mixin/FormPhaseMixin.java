package com.magicmod.mixin;

import java.util.Map;
import java.util.WeakHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.magicmod.Forms;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;

/**
 * すり抜け中は、スペクテイターと同じようにブロックの当たり判定を無視する(Player.tick が毎回 noPhysics を決め直すので、その直後に上書きする)。
 * 発動した地点の床の高さを「床」として扱い、それより下には沈まない(ジャンプもできる)。サーバーとクライアントの両方で同じ計算をする。
 */
@Mixin(Player.class)
public abstract class FormPhaseMixin {

	private static final Map<Player, Double> FLOOR = new WeakHashMap<>();

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;updateIsUnderwater()Z"), require = 0)
	private void magicmod$phase(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (!Forms.phasing(self)) {
			FLOOR.remove(self);
			return;
		}
		self.noPhysics = true;
		Double floor = FLOOR.get(self);
		if (floor == null) {
			// 発動した地点の真下にある床の上面(空中で発動しても、その下の地面)
			Vec3 from = self.position().add(0, 0.2, 0);
			var hit = self.level().clip(new ClipContext(from, from.add(0, -96, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
			floor = hit.getType() == HitResult.Type.MISS ? self.getY() : hit.getLocation().y;
			FLOOR.put(self, floor);
		}
		if (self.getY() <= floor) {
			self.setPos(self.getX(), floor, self.getZ());
			Vec3 v = self.getDeltaMovement();
			if (v.y < 0) {
				self.setDeltaMovement(v.x, 0, v.z);
			}
			self.setOnGround(true);
		}
	}
}
