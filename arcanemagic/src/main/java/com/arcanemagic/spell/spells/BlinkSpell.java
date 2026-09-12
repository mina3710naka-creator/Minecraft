package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 視線方向へ短距離ワープする。壁があればその手前で止まる。 */
public class BlinkSpell implements Spell {

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "blink");
	}

	@Override
	public Component displayName() {
		return Component.translatable("spell.arcanemagic.blink");
	}

	@Override
	public int baseCooldownTicks() {
		return 60;
	}

	@Override
	public void cast(ServerLevel serverLevel, Player caster, int spellLevel) {
		double distance = 6.0 + spellLevel * 1.5;
		Vec3 start = caster.getEyePosition();
		Vec3 look = caster.getViewVector(1.0F);
		Vec3 end = start.add(look.scale(distance));

		BlockHitResult hit = serverLevel.clip(new ClipContext(start, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));

		Vec3 target = hit.getType() == HitResult.Type.BLOCK
				? hit.getLocation().subtract(look.scale(0.5))
				: end;

		serverLevel.sendParticles(ParticleTypes.PORTAL, caster.getX(), caster.getY() + caster.getBbHeight() * 0.5, caster.getZ(), 30, 0.3, 0.5, 0.3, 0.05);

		caster.teleportTo(target.x, target.y - caster.getBbHeight() * 0.5, target.z);

		serverLevel.sendParticles(ParticleTypes.PORTAL, target.x, target.y, target.z, 30, 0.3, 0.5, 0.3, 0.05);
		serverLevel.playSound(null, caster.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}
