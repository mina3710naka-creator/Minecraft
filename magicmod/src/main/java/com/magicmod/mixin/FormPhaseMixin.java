package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.magicmod.Forms;

import net.minecraft.world.entity.player.Player;

/** すり抜け中は、スペクテイターと同じようにブロックの当たり判定を無視する(Player.tick が毎回 noPhysics を決め直すので、その直後に上書きする)。 */
@Mixin(Player.class)
public abstract class FormPhaseMixin {

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;updateIsUnderwater()Z"), require = 0)
	private void magicmod$phase(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (Forms.phasing(self)) {
			self.noPhysics = true;
		}
	}
}
