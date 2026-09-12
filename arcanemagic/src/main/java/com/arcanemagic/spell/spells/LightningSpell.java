package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 視線の先(ブロックに当たればそこ、当たらなければ最大射程)に雷を落とす。 */
public class LightningSpell implements Spell {

	private static final double RANGE = 20.0;

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "lightning");
	}

	@Override
	public Component displayName() {
		return Component.translatable("spell.arcanemagic.lightning");
	}

	@Override
	public int baseCooldownTicks() {
		return 120;
	}

	@Override
	public void cast(ServerLevel serverLevel, Player caster, int spellLevel) {
		Vec3 start = caster.getEyePosition();
		Vec3 look = caster.getViewVector(1.0F);
		Vec3 end = start.add(look.scale(RANGE));

		BlockHitResult blockHit = serverLevel.clip(new ClipContext(start, end,
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));

		Vec3 strikePos = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;
		int strikes = 1 + spellLevel / 3;

		for (int i = 0; i < strikes; i++) {
			LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, serverLevel);
			double offsetX = i == 0 ? 0.0 : (serverLevel.random.nextDouble() - 0.5) * 3.0;
			double offsetZ = i == 0 ? 0.0 : (serverLevel.random.nextDouble() - 0.5) * 3.0;
			bolt.moveTo(strikePos.x + offsetX, strikePos.y, strikePos.z + offsetZ);
			serverLevel.addFreshEntity(bolt);
		}
	}
}
