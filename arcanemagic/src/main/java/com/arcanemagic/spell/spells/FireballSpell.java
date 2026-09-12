package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/** 視線方向にファイアボールを撃ち出し、当たった相手を燃やす。 */
public class FireballSpell implements Spell {

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "fireball");
	}

	@Override
	public Component displayName() {
		return Component.translatable("spell.arcanemagic.fireball");
	}

	@Override
	public int baseCooldownTicks() {
		return 40;
	}

	@Override
	public void cast(ServerLevel serverLevel, Player caster, int spellLevel) {
		Vec3 look = caster.getViewVector(1.0F);
		Vec3 start = caster.getEyePosition().add(look.scale(1.2));
		Vec3 velocity = look.scale(1.4 + spellLevel * 0.15);

		SmallFireball fireball = new SmallFireball(serverLevel, caster, velocity.x, velocity.y, velocity.z);
		fireball.setPos(start.x, start.y, start.z);
		serverLevel.addFreshEntity(fireball);

		serverLevel.playSound(null, caster.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}
