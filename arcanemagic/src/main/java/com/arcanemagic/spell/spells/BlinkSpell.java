package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.RaycastContext;
import net.minecraft.util.math.Vec3d;

/** 視線方向へ短距離ワープする。壁があればその手前で止まる。 */
public class BlinkSpell implements Spell {

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "blink");
	}

	@Override
	public Text displayName() {
		return Text.translatable("spell.arcanemagic.blink");
	}

	@Override
	public int baseCooldownTicks() {
		return 60;
	}

	@Override
	public void cast(ServerWorld world, PlayerEntity caster, int level) {
		double distance = 6.0 + level * 1.5;
		Vec3d start = caster.getEyePos();
		Vec3d look = caster.getRotationVec(1.0F);
		Vec3d end = start.add(look.multiply(distance));

		BlockHitResult hit = world.raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));

		Vec3d target = hit.getType() == HitResult.Type.BLOCK
				? hit.getPos().subtract(look.multiply(0.5))
				: end;

		world.spawnParticles(ParticleTypes.PORTAL, caster.getX(), caster.getBodyY(0.5), caster.getZ(), 30, 0.3, 0.5, 0.3, 0.05);

		caster.requestTeleport(target.x, target.y - caster.getHeight() * 0.5, target.z);

		world.spawnParticles(ParticleTypes.PORTAL, target.x, target.y, target.z, 30, 0.3, 0.5, 0.3, 0.05);
		world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
	}
}
