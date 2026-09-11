package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.RaycastContext;
import net.minecraft.util.math.Vec3d;

/** 視線の先(ブロックに当たればそこ、当たらなければ最大射程)に雷を落とす。 */
public class LightningSpell implements Spell {

	private static final double RANGE = 20.0;

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "lightning");
	}

	@Override
	public Text displayName() {
		return Text.translatable("spell.arcanemagic.lightning");
	}

	@Override
	public int baseCooldownTicks() {
		return 120;
	}

	@Override
	public void cast(ServerWorld world, PlayerEntity caster, int level) {
		Vec3d start = caster.getEyePos();
		Vec3d look = caster.getRotationVec(1.0F);
		Vec3d end = start.add(look.multiply(RANGE));

		BlockHitResult blockHit = world.raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, caster));

		Vec3d strikePos = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getPos() : end;
		int strikes = 1 + level / 3;

		for (int i = 0; i < strikes; i++) {
			LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
			double offsetX = i == 0 ? 0.0 : (world.random.nextDouble() - 0.5) * 3.0;
			double offsetZ = i == 0 ? 0.0 : (world.random.nextDouble() - 0.5) * 3.0;
			bolt.refreshPositionAfterTeleport(strikePos.x + offsetX, strikePos.y, strikePos.z + offsetZ);
			world.spawnEntity(bolt);
		}
	}
}
