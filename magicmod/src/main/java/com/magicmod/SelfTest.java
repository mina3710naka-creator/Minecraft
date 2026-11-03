package com.magicmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** /magic selftest: 偽プレイヤーで全魔法・全わざ・合成・画面を一通り動かして、例外が出ないか確かめる(開発用)。 */
final class SelfTest {

	private SelfTest() {
	}

	static int run(CommandSourceStack src) {
		ServerLevel level = src.getLevel();
		Vec3 at = src.getPosition();
		FakePlayer fp = FakePlayer.get(level);
		fp.snapTo(at.x, at.y, at.z, 0, 0);
		PlayerMagic pm = PlayerMagic.of(fp);
		pm.archetype = Archetype.BALANCED;
		Spells.all().forEach(s -> pm.learned.add(s.id));
		Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		z.snapTo(at.x, at.y, at.z + 5, 180, 0);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		z.setHealth(1000);
		z.setNoAi(true);
		level.addFreshEntity(z);
		fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
		List<String> errors = new ArrayList<>();
		int ok = 0;
		ItemStack wand = new ItemStack(ModItems.WANDS.get(4));
		fp.setItemInHand(InteractionHand.MAIN_HAND, wand);
		for (Spell s : Spells.all()) {
			try {
				Ctx c = new Ctx(fp, s, Calc.spellPower(fp, s, 5, wand));
				s.form.cast(c);
				ok++;
			} catch (Exception e) {
				errors.add(s.id + ": " + e);
				MagicMod.LOGGER.error("[selftest] spell {}", s.id, e);
			}
		}
		for (String key : Spells.FIXED_RECIPES.keySet()) {
			try {
				String[] parts = key.split("\\+");
				Spell sp = Spells.build(new PlayerMagic.Synth("t", parts[0], List.of(parts).subList(1, parts.length), 3, "test"));
				sp.form.cast(new Ctx(fp, sp, 10));
				ok++;
			} catch (Exception e) {
				errors.add(key + ": " + e);
				MagicMod.LOGGER.error("[selftest] synth {}", key, e);
			}
		}
		fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		for (String cat : Techniques.CATEGORIES) {
			for (Techniques.Tech t : Techniques.of(cat)) {
				try {
					Ctx c = new Ctx(fp, t.spell, Calc.techPower(fp, t, fp.getMainHandItem()));
					c.physical = true;
					t.spell.form.cast(c);
					ok++;
				} catch (Exception e) {
					errors.add(t.id + ": " + e);
					MagicMod.LOGGER.error("[selftest] tech {}", t.id, e);
				}
			}
		}
		try {
			ok += Ui.buildAll(fp, BlockPos.containing(at));
		} catch (Exception e) {
			errors.add("ui: " + e);
			MagicMod.LOGGER.error("[selftest] ui", e);
		}
		try {
			for (Element e : Element.values()) {
				Casting.mobCast(z, Spells.get(e, 1));
			}
			ok++;
		} catch (Exception e) {
			errors.add("mobCast: " + e);
			MagicMod.LOGGER.error("[selftest] mobcast", e);
		}
		try {
			for (var h : java.util.List.of(ModEffects.BLEED, ModEffects.BURN, ModEffects.SHOCK, ModEffects.PARALYSIS, ModEffects.STUN, ModEffects.BIND,
					ModEffects.KNOCKDOWN, ModEffects.DROWN, ModEffects.FEAR, ModEffects.CONFUSION, ModEffects.SILENCE, ModEffects.MANA_SEAL,
					ModEffects.WET, ModEffects.DOOM, ModEffects.OVERCHARGE, ModEffects.CLONE, ModEffects.WIND_SHIELD, ModEffects.AQUA_SHIELD,
					ModEffects.HEAT_HAZE, ModEffects.COUNTER, ModEffects.REBIRTH, ModEffects.ARMOR_BREAK)) {
				z.addEffect(new net.minecraft.world.effect.MobEffectInstance(h, 100, 1));
			}
			DamageNumbers.damage(z, 12.5f, 0xFF5A1A, true, false);
			DamageNumbers.heal(z, 3f);
			Terrain.freeze(level, at, 3);
			Terrain.douse(level, at, 3);
			Terrain.melt(level, at, 3, true);
			for (String id : java.util.List.of("gale_harpy", "igdrum")) {
				Enemies.Def d = Enemies.BY_ID.get(id);
				net.minecraft.world.entity.Mob m = d.type().create(level, EntitySpawnReason.COMMAND);
				m.snapTo(at.x + 8, at.y, at.z, 0, 0);
				level.addFreshEntity(m);
				BossFx.intro(m, level, d);
				BossFx.phaseTwo(m, level, d);
				BossFx.death(m, level, d);
				Tasks.later(60, m::discard);
			}
			ok++;
		} catch (Exception e) {
			errors.add("fx: " + e);
			MagicMod.LOGGER.error("[selftest] fx", e);
		}
		// 回帰テスト: 一度も殴っていない動物にも範囲・扇状の攻撃が当たる / 村人には当たらない
		try {
			fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			fp.snapTo(at.x + 20, at.y, at.z, 0, 0);
			net.minecraft.world.entity.animal.cow.Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			cow.snapTo(at.x + 20, at.y, at.z + 2, 0, 0);
			cow.setNoAi(true);
			level.addFreshEntity(cow);
			net.minecraft.world.entity.npc.villager.Villager vil = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			vil.snapTo(at.x + 21, at.y, at.z + 1.5, 0, 0);
			vil.setNoAi(true);
			level.addFreshEntity(vil);
			fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.7, 0));
			float h0 = cow.getHealth(), v0 = vil.getHealth();
			Spell cone = Spells.get(Element.FIRE, 12);
			cone.form.cast(new Ctx(fp, cone, 3));
			float h1 = cow.getHealth();
			Techniques.Tech spin = Techniques.get("blade_4");
			Ctx tc = new Ctx(fp, spin.spell, 3);
			tc.physical = true;
			spin.spell.form.cast(tc);
			float h2 = cow.getHealth();
			if (!(h1 < h0 && h2 < h1)) {
				errors.add("一度も殴っていない動物に攻撃が当たらない: " + h0 + " -> " + h1 + " -> " + h2);
			} else {
				ok++;
			}
			cow.setHealth(cow.getMaxHealth());
			float c0 = cow.getHealth();
			cow.hurtServer(level, level.damageSources().playerAttack(fp), 1);
			float c1 = cow.getHealth();
			cone.form.cast(new Ctx(fp, cone, 2));
			float c2 = cow.getHealth();
			cow.hurtServer(level, level.damageSources().playerAttack(fp), 1);
			float c3 = cow.getHealth();
			if (!(c1 < c0 && c2 < c1 && c3 < c2)) {
				errors.add("殴る→魔法→殴る の連携でダメージが吸われる: " + c0 + " -> " + c1 + " -> " + c2 + " -> " + c3);
			} else {
				ok++;
			}
			if (vil.getHealth() < v0) {
				errors.add("村人に範囲攻撃が当たっている");
			} else {
				ok++;
			}
			cow.discard();
			vil.discard();
		} catch (Exception e) {
			errors.add("target rule: " + e);
			MagicMod.LOGGER.error("[selftest] target rule", e);
		}
		int fok = ok;
		src.sendSuccess(() -> Component.literal("[selftest] OK " + fok + " / エラー " + errors.size()), false);
		for (String e : errors) {
			src.sendSuccess(() -> Component.literal(" " + e), false);
		}
		Tasks.later(200, () -> {
			MagicMod.LOGGER.info("[selftest] target zombie health after 10s: {}", z.getHealth());
			z.discard();
		});
		return 1;
	}
}
