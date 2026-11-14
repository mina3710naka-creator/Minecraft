package com.magicmod.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.magicmod.ModSounds;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** 独自の敵・使い魔の鳴き声を、見た目に合わせた MOD の音に置き換える(元の音の「.hurt」「.death」で種類を判断)。 */
@Mixin(LivingEntity.class)
public abstract class EntityVoiceMixin {

	@Inject(method = "makeSound", at = @At("HEAD"), cancellable = true)
	private void magicmod$voice(@Nullable SoundEvent sound, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (sound == null || self.level().isClientSide()) {
			return;
		}
		String path = sound.location().getPath();
		if (!path.startsWith("entity.")) {
			return;
		}
		String cat = path.endsWith(".death") ? "death" : path.endsWith(".hurt") || path.contains(".hurt_") ? "hurt" : "idle";
		SoundEvent mine = ModSounds.voice(self, cat);
		if (mine == null) {
			return;
		}
		float pitch = 0.92f + self.getRandom().nextFloat() * 0.16f;
		self.level().playSound(null, self.getX(), self.getY(), self.getZ(), mine, self.getSoundSource(), "death".equals(cat) ? 1.2f : 0.9f, pitch);
		ci.cancel();
	}
}
