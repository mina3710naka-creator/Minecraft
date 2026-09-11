package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** 視線方向にファイアボールを撃ち出し、当たった相手を燃やす。 */
public class FireballSpell implements Spell {

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "fireball");
	}

	@Override
	public Text displayName() {
		return Text.translatable("spell.arcanemagic.fireball");
	}

	@Override
	public int baseCooldownTicks() {
		return 40;
	}

	@Override
	public void cast(ServerWorld world, PlayerEntity caster, int level) {
		Vec3d look = caster.getRotationVec(1.0F);
		Vec3d start = caster.getEyePos().add(look.multiply(1.2));
		Vec3d velocity = look.multiply(1.4 + level * 0.15);

		SmallFireballEntity fireball = new SmallFireballEntity(world, caster, velocity.x, velocity.y, velocity.z);
		fireball.setPosition(start.x, start.y, start.z);
		world.spawnEntity(fireball);

		world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F);
	}
}
