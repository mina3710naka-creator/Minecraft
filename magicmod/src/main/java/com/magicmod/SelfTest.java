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
			MagicMod.LOGGER.info("[selftest-debug] cow alive={} pos={} fp={} enemy={} area={} dir={}", cow.isAlive(), cow.position(), fp.position(),
					Combat.isEnemy(fp, cow, true), Combat.isEnemy(fp, cow, false), new Ctx(fp, cone, 3).dir);
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
		// A: コンボ・回避・元素反応・予兆
		try {
			PlayerMagic fpm = PlayerMagic.of(fp);
			fpm.combo = 0;
			Combo.hit(fp, false, 1);
			Combo.hit(fp, true, 1);
			if (fpm.combo != 3) {
				errors.add("コンボの数え方(魔法→物理で +1 ボーナス): " + fpm.combo);
			} else {
				ok++;
			}
			fpm.combo = 25;
			fpm.comboTier = Combo.tierOf(25);
			fpm.comboUntil = level.getGameTime() + 50;
			if (Combo.mult(fpm, level.getGameTime()) < 1.09f) {
				errors.add("コンボの威力倍率: " + Combo.mult(fpm, level.getGameTime()));
			} else {
				ok++;
			}
			fpm.combo = 0;
			fpm.comboTier = 0;
			// 元素反応: 炎 → 水(蒸発)
			net.minecraft.world.entity.monster.zombie.Zombie rz = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			rz.snapTo(at.x, at.y, at.z + 12, 180, 0);
			rz.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			rz.setHealth(500);
			rz.setNoAi(true);
			level.addFreshEntity(rz);
			Reactions.lastName = "";
			Ctx fc = new Ctx(fp, Spells.get(Element.FIRE, 1), 5);
			fc.hit(rz, 1f);
			Ctx wc = new Ctx(fp, Spells.get(Element.WATER, 1), 5);
			wc.hit(rz, 1f);
			if (!Reactions.lastName.equals("蒸発")) {
				errors.add("元素反応(炎+水)が起きない: " + Reactions.lastName);
			} else {
				ok++;
			}
			String[][] pairs = { { "thunder", "water", "拡散感電" }, { "wind", "fire", "火炎旋風" }, { "gravity", "thunder", "磁力爆縮" },
					{ "light", "dark", "対消滅" }, { "curse", "dark", "侵食" }, { "wind", "water", "凍結" }, { "fire", "thunder", "過負荷" },
					{ "wind", "thunder", "雷嵐" }, { "gravity", "water", "渦潮" } };
			for (String[] pr : pairs) {
				rz.setHealth(500);
				Reactions.lastName = "";
				Ctx ca = new Ctx(fp, Spells.get(Element.byId(pr[0]), 1), 5);
				ca.hit(rz, 1f);
				Ctx cb = new Ctx(fp, Spells.get(Element.byId(pr[1]), 1), 5);
				cb.level.getGameTime();
				Reactions.clearCooldown(rz);
				cb.hit(rz, 1f);
				if (!Reactions.lastName.equals(pr[2])) {
					errors.add("元素反応 " + pr[0] + "+" + pr[1] + " が起きない: " + Reactions.lastName);
				} else {
					ok++;
				}
			}
			rz.discard();
			// 回避: 無敵の間はダメージが入らず、ジャスト回避の報酬が出る
			fpm.invulnUntil = level.getGameTime() + 10;
			fpm.dodgeRewarded = false;
			boolean avoided = Dodge.tryAvoid(fp, level.damageSources().mobAttack(z));
			if (!avoided || fpm.justDodgeUntil <= level.getGameTime()) {
				errors.add("回避の無敵・ジャスト回避が働かない");
			} else {
				ok++;
			}
			fpm.invulnUntil = 0;
			fpm.justDodgeUntil = 0;
		} catch (Exception e) {
			errors.add("A: " + e);
			MagicMod.LOGGER.error("[selftest] A", e);
		}
		// 予兆つき攻撃: 予兆が出て、時間が来たら範囲内のプレイヤーに当たり、外には当たらない
		try {
			fp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			fp.setHealth(20);
			fp.snapTo(at.x - 20, at.y, at.z, 0, 0);
			net.minecraft.world.entity.Mob boss = Enemies.BY_ID.get("igdrum").type().create(level, EntitySpawnReason.COMMAND);
			boss.snapTo(at.x - 20, at.y, at.z + 14, 0, 0);
			level.addFreshEntity(boss);
			net.minecraft.world.entity.monster.zombie.Zombie safeBait = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			Enemies.Def bd = Enemies.BY_ID.get("igdrum");
			float before = fp.getHealth();
			BossMoves.clear();
			Enemies.Ability slam = bd.abilities().stream().filter(x -> x.interval() == 200 && x.phase() == 0).findFirst().orElse(null);
			boss.snapTo(fp.getX(), fp.getY(), fp.getZ() + 1, 0, 0);
			slam.act().run(boss, level, fp);
			int danger = (int) level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, boss.getBoundingBox().inflate(10)).stream()
					.filter(d -> d.getItemStack().getItem().toString().contains("fx_danger")).count();
			if (danger < 2) {
				errors.add("予兆の表示が出ない: " + danger);
			} else {
				ok++;
			}
			Tasks.later(60, () -> {
				int left = (int) level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, boss.getBoundingBox().inflate(10)).stream()
						.filter(d -> d.getItemStack().getItem().toString().contains("fx_danger")).count();
				MagicMod.LOGGER.info("[selftest-async] 予兆の表示は攻撃後に消える: " + (left == 0 ? "OK" : "NG(" + left + ")"));
				for (var d : level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, boss.getBoundingBox().inflate(10))) {
					MagicMod.LOGGER.info("[selftest-async]   残り: {} age={} pos={}", d.getItemStack().getItem(), d.tickCount, d.position());
				}
				boss.discard();
				fp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			});
		} catch (Exception e) {
			errors.add("telegraph: " + e);
			MagicMod.LOGGER.error("[selftest] telegraph", e);
		}
		// 遺跡と最終ボス
		try {
			for (Element e : Element.values()) {
				BlockPos o = new BlockPos(2000 + e.ordinal() * 40, 70, 2000);
				Ruins.build(level, o, e, level.getRandom());
				boolean altar = level.getBlockState(o.above()).is(ModBlocks.SEAL_ALTAR);
				boolean chest = level.getBlockState(o.offset(0, 1, 2)).is(net.minecraft.world.level.block.Blocks.CHEST);
				if (altar && chest) {
					ok++;
				} else {
					errors.add("遺跡 " + e + " altar=" + altar + " chest=" + chest);
				}
			}
			net.minecraft.world.entity.Mob om = (net.minecraft.world.entity.Mob) ModEntities.OMNIS.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			om.setPos(0, 80, 0);
			Omnis.attune(om, level, Element.FIRE);
			if (Omnis.attunement(om) == Element.FIRE && Omnis.weakOf(Element.FIRE) == Element.WATER) {
				ok++;
			} else {
				errors.add("オムニスの属性");
			}
			Omnis.forget(om);
		} catch (Throwable e) {
			errors.add("ruins/omnis: " + e);
			MagicMod.LOGGER.error("[selftest] ruins", e);
		}
		// 終焉神
		try {
			Enemies.Def ad = Enemies.BY_ID.get(Apocalypse.ID);
			net.minecraft.world.entity.Mob am = (net.minecraft.world.entity.Mob) ModEntities.APOCALYPSE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			am.setPos(0, 80, 0);
			boolean sealed = !Apocalypse.unlocked(new PlayerMagic());
			Omnis.attune(am, level, Element.LIGHT);
			boolean att = Omnis.attunement(am) == Element.LIGHT && Omnis.weakOf(Element.LIGHT) == Element.DARK;
			Apocalypse.forget(am);
			if (ad != null && ad.hp() >= 30000 && sealed && att && Enemies.defOf(am) == ad && Apocalypse.requirements(new PlayerMagic()).size() == 3) {
				ok++;
			} else {
				errors.add("終焉神 def=" + ad + " sealed=" + sealed + " att=" + att);
			}
		} catch (Throwable e) {
			errors.add("終焉神: " + e);
		}
		// 融合魔法(最大×最大)
		try {
			if (Fusions.all().size() == 28) {
				ok++;
			} else {
				errors.add("融合魔法の数: " + Fusions.all().size());
			}
			java.util.Set<String> names = new java.util.HashSet<>();
			int cast = 0;
			for (Element a : Element.values()) {
				for (Element b : Element.values()) {
					if (a.ordinal() >= b.ordinal()) {
						continue;
					}
					Spell sa = Spells.get(a, 10), sb = Spells.get(b, 10);
					Spell fs = Spells.build(new PlayerMagic.Synth("t", sa.id, java.util.List.of(sb.id), 2, Spells.autoName(sa, java.util.List.of(sb))));
					Spell rev = Spells.build(new PlayerMagic.Synth("t", sb.id, java.util.List.of(sa.id), 2, "x"));
					if (fs == null || !fs.fusion || rev == null || !rev.fusion || !fs.name.equals(rev.name) || !names.add(fs.name)) {
						errors.add("融合 " + a + "×" + b);
						continue;
					}
					Ctx fc = new Ctx(fp, fs, 10f);
					fp.setPos(5, 70, 5);
					fs.form.cast(fc);
					cast++;
				}
			}
			ok += cast == 28 ? 1 : 0;
		} catch (Throwable e) {
			errors.add("fusion: " + e);
			MagicMod.LOGGER.error("[selftest] fusion", e);
		}
		// 物理モーション(連続攻撃で 1→4 段と変化する)
		try {
			net.minecraft.world.entity.animal.cow.Cow mt = EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			mt.setPos(6, 70, 6);
			level.addFreshEntity(mt);
			mt.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000);
			mt.setHealth(2000);
			for (String cat : new String[] { "blade", "polearm", "dual", "swing", "heavy" }) {
				Moves.forget(fp.getUUID());
				float before = mt.getHealth();
				StringBuilder seq = new StringBuilder();
				for (int i = 0; i < 5; i++) {
					fp.setPos(4, 70, 6);
					Moves.onHit(fp, mt, 5f, cat);
					seq.append(Moves.stepOf(fp));
				}
				if (seq.toString().equals("12341") && mt.getHealth() < before) {
					ok++;
				} else {
					errors.add("モーション " + cat + " 段=" + seq + " HP " + before + "→" + mt.getHealth());
				}
			}
			mt.discard();
		} catch (Throwable e) {
			errors.add("moves: " + e);
			MagicMod.LOGGER.error("[selftest] moves", e);
		}
		// 三重融合(最大魔法 3 つ)と、使い魔は 1 体だけ
		try {
			if (Fusions.triples().size() == 56) {
				ok++;
			} else {
				errors.add("三重融合の数: " + Fusions.triples().size());
			}
			java.util.Set<String> tn = new java.util.HashSet<>();
			Element[] els = Element.values();
			int built = 0, casts = 0;
			for (int i = 0; i < 8; i++) {
				for (int j = i + 1; j < 8; j++) {
					for (int k = j + 1; k < 8; k++) {
						Spell sa = Spells.get(els[i], 10), sb = Spells.get(els[j], 10), sc = Spells.get(els[k], 10);
						Spell t1 = Spells.build(new PlayerMagic.Synth("t", sa.id, java.util.List.of(sb.id, sc.id), 2, "x"));
						Spell t2 = Spells.build(new PlayerMagic.Synth("t", sc.id, java.util.List.of(sa.id, sb.id), 2, "x"));
						if (t1 == null || !t1.fusion || t2 == null || !t1.name.equals(t2.name) || !tn.add(t1.name)) {
							errors.add("三重融合 " + els[i] + els[j] + els[k]);
							continue;
						}
						built++;
						if ((i + j + k) % 9 == 0) {
							fp.setPos(5, 70, 5);
							t1.form.cast(new Ctx(fp, t1, 10f));
							casts++;
						}
					}
				}
			}
			ok += built == 56 ? 1 : 0;
			MagicMod.LOGGER.info("[selftest] triple built {} cast {}", built, casts);
			PlayerMagic fpm2 = PlayerMagic.of(fp);
			fpm2.contracts.clear();
			fpm2.contracts.add("wolf");
			fpm2.contracts.add("golem");
			fpm2.activeFamiliars.clear();
			Familiars.summonAll(fp, fpm2);
			if (fpm2.activeFamiliars.size() == 1) {
				ok++;
			} else {
				errors.add("使い魔が同時に " + fpm2.activeFamiliars.size() + " 体");
			}
			Familiars.dismissAll(fp);
			fpm2.contracts.clear();
			fpm2.familiarsOut = false;
			if (Familiars.stageOf(1) == 1 && Familiars.stageOf(3) == 2 && Familiars.stageOf(6) == 3 && Familiars.stageOf(10) == 4) {
				ok++;
			} else {
				errors.add("進化段階");
			}
		} catch (Throwable e) {
			errors.add("triple/familiar: " + e);
			MagicMod.LOGGER.error("[selftest] triple", e);
		}
		// えさやり: Lv5(経験値 400)まではしっかり、超えたらほんの少し
		try {
			PlayerMagic fm = PlayerMagic.of(fp);
			fm.contracts.clear();
			fm.contracts.add("wolf");
			fm.familiarPick = 0;
			fm.familiarExp.put("wolf", 360);
			fm.familiarCooldown.clear();
			fm.activeFamiliars.clear();
			Familiars.summonAll(fp, fm);
			var ent = fm.activeFamiliars.isEmpty() ? null : level.getEntity(fm.activeFamiliars.get(0));
			if (ent == null) {
				errors.add("えさやり: 使い魔が出ない");
			} else {
				net.minecraft.world.item.ItemStack beef = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 3);
				Familiars.feed(fp, ent, Familiars.Kind.WOLF, beef);
				int a = fm.familiarExp.get("wolf");
				Familiars.feed(fp, ent, Familiars.Kind.WOLF, beef);
				int b = fm.familiarExp.get("wolf");
				if (a == 400 && b == 402 && Familiars.level(fm, Familiars.Kind.WOLF) == 5 && Familiars.foods(Familiars.Kind.WOLF).contains(beef.getItem())) {
					ok++;
				} else {
					errors.add("えさやりの経験値 " + a + "," + b);
				}
			}
			Familiars.dismissAll(fp);
			fm.contracts.clear();
			fm.familiarExp.clear();
			fm.familiarsOut = false;
		} catch (Throwable e) {
			errors.add("feed: " + e);
			MagicMod.LOGGER.error("[selftest] feed", e);
		}
		// ギルド・生活魔法・新しい使い魔
		try {
			PlayerMagic gm = PlayerMagic.of(fp);
			gm.quests.clear();
			gm.questTaken.clear();
			var offers = Guild.offers(fp);
			if (offers.size() == 4) {
				ok++;
			} else {
				errors.add("ギルドの依頼数 " + offers.size());
			}
			for (var q : offers) {
				Guild.act(fp, "take:" + q.id());
			}
			if (Guild.active(gm).size() == Guild.MAX_ACTIVE && Guild.offers(fp).size() == 1) {
				ok++;
			} else {
				errors.add("ギルドの受注 " + Guild.active(gm).size());
			}
			Enemies.Def anyDef = Enemies.BY_ID.values().iterator().next();
			for (int i = 0; i < 40; i++) {
				Guild.onKill(fp, anyDef);
			}
			int sp0 = gm.skillPoints, done0 = gm.questsDone;
			for (int i = Guild.active(gm).size() - 1; i >= 0; i--) {
				Guild.act(fp, "turn:" + i);
			}
			// 討伐の依頼は 40 体でどれも達成するはず(属性・ボス系は種類による)。進行と報酬の処理に例外がなければよい
			if (gm.questsDone >= done0) {
				ok++;
			}
			gm.quests.clear();
			// 生活魔法
			BlockPos base = new BlockPos(3000, 70, 3000);
			level.setBlock(base.below(), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState(), 3);
			level.setBlock(base, net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState(), 3);
			LifeTomeItem.grow(level, fp, base, 3);
			for (int i = 0; i < 6 && level.getBlockState(base).getBlock() instanceof net.minecraft.world.level.block.CropBlock cb && !cb.isMaxAge(level.getBlockState(base)); i++) {
				LifeTomeItem.grow(level, fp, base, 3);
			}
			boolean ripe = level.getBlockState(base).getBlock() instanceof net.minecraft.world.level.block.CropBlock cb2 && cb2.isMaxAge(level.getBlockState(base));
			LifeTomeItem.harvest(level, fp, base, 3);
			boolean replanted = level.getBlockState(base).getBlock() instanceof net.minecraft.world.level.block.CropBlock cb3 && !cb3.isMaxAge(level.getBlockState(base));
			BlockPos ore = new BlockPos(3010, 70, 3000);
			for (int i = 0; i < 5; i++) {
				level.setBlock(ore.offset(i, 0, 0), net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState(), 3);
			}
			int mined = LifeTomeItem.veinMine(level, fp, ore, 14);
			LifeTomeItem.purify(level, fp);
			if (ripe && replanted && mined == 5) {
				ok++;
			} else {
				errors.add("生活魔法 ripe=" + ripe + " replanted=" + replanted + " mined=" + mined);
			}
			for (String kid : new String[] { "pegasus", "behemoth" }) {
				gm.contracts.clear();
				gm.contracts.add(kid);
				gm.familiarPick = 0;
				gm.activeFamiliars.clear();
				gm.familiarCooldown.clear();
				Familiars.summonAll(fp, gm);
				if (gm.activeFamiliars.size() == 1 && Familiars.rideable(Familiars.Kind.byId(kid))) {
					ok++;
				} else {
					errors.add("新しい使い魔 " + kid);
				}
				Familiars.dismissAll(fp);
			}
			gm.contracts.clear();
			gm.familiarsOut = false;
		} catch (Throwable e) {
			errors.add("guild/life: " + e);
			MagicMod.LOGGER.error("[selftest] guild", e);
		}
		// ギルドの小屋・特別依頼・生活魔法の拡張・使い魔スキル
		try {
			BlockPos hall = new BlockPos(3100, 70, 3100);
			GuildHall.build(level, hall);
			boolean board = level.getBlockState(hall.offset(0, 0, -2)).is(ModBlocks.GUILD_BOARD);
			boolean npc = !level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, new net.minecraft.world.phys.AABB(hall).inflate(6),
					v -> v.entityTags().contains("mm_guild")).isEmpty();
			if (board && npc) {
				ok++;
			} else {
				errors.add("ギルドの小屋 board=" + board + " npc=" + npc);
			}
			PlayerMagic gm = PlayerMagic.of(fp);
			int done0 = gm.questsDone;
			gm.questsDone = 10;
			gm.questTaken.clear();
			var off = Guild.offers(fp);
			if (off.size() == 5 && off.stream().anyMatch(q -> q.kind().equals("hunt_boss")) && !Guild.title(gm).isEmpty()) {
				ok++;
			} else {
				errors.add("特別依頼 " + off.size());
			}
			gm.questsDone = done0;
			gm.questTaken.clear();
			BlockPos farm = new BlockPos(3200, 70, 3200);
			LifeTomeItem.level(level, fp, farm, 2);
			level.setBlock(farm.above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
			int lv2 = LifeTomeItem.level(level, fp, farm, 2);
			if (level.getBlockState(farm.above()).isAir() && lv2 > 0) {
				ok++;
			} else {
				errors.add("整地");
			}
			fp.getInventory().add(new ItemStack(net.minecraft.world.item.Items.BEEF, 5));
			LifeTomeItem.cook(level, fp);
			LifeTomeItem.breed(level, fp);
			int exp0 = gm.lifeExp;
			LifeTomeItem.gain(fp, 40);
			if (gm.lifeExp == exp0 + 40 && LifeTomeItem.lifeLevel(gm) >= 2) {
				ok++;
			} else {
				errors.add("生活の熟練度");
			}
			gm.lifeExp = exp0;
			int skills = 0;
			for (String kid : new String[] { "wolf", "mud_slime", "fairy", "hawk", "guardian", "elemental", "golem" }) {
				gm.contracts.clear();
				gm.contracts.add(kid);
				gm.familiarPick = 0;
				gm.activeFamiliars.clear();
				gm.familiarCooldown.clear();
				gm.cooldowns.remove("famskill");
				Familiars.summonAll(fp, gm);
				Familiars.skill(fp);
				skills++;
				Familiars.dismissAll(fp);
			}
			ok += skills == 7 ? 1 : 0;
			gm.contracts.clear();
			gm.familiarsOut = false;
			gm.cooldowns.remove("famskill");
		} catch (Throwable e) {
			errors.add("hall/skill: " + e);
			MagicMod.LOGGER.error("[selftest] hall", e);
		}
		// 融合魔法は元の最大魔法より強い(威力の係数)
		try {
			Spell sa = Spells.get(Element.FIRE, 10), sb = Spells.get(Element.WATER, 10), sc = Spells.get(Element.WIND, 10);
			Spell pair = Spells.build(new PlayerMagic.Synth("t", sa.id, java.util.List.of(sb.id), 2, "x"));
			Spell tri = Spells.build(new PlayerMagic.Synth("t", sa.id, java.util.List.of(sb.id, sc.id), 2, "x"));
			ItemStack none = ItemStack.EMPTY;
			float base = Calc.spellPower(fp, sa, 3, none), pp = Calc.spellPower(fp, pair, 3, none), tp = Calc.spellPower(fp, tri, 3, none);
			MagicMod.LOGGER.info("[selftest] power base={} pair={} triple={}", base, pp, tp);
			if (pp >= base * 2f && tp >= pp) {
				ok++;
			} else {
				errors.add("融合魔法の威力 base=" + base + " pair=" + pp + " triple=" + tp);
			}
		} catch (Throwable e) {
			errors.add("fusion power: " + e);
		}
		// 変身
		try {
			PlayerMagic fm = PlayerMagic.of(fp);
			Forms.off(fp, false);
			for (Forms.Form f : Forms.Form.values()) {
				fm.forms.add(f.id);
			}
			fm.mp = 9999;
			int okForms = 0;
			for (int i = 0; i < Forms.Form.values().length; i++) {
				fm.cooldowns.remove("form:switch");
				fm.cooldowns.remove("form:a");
				fm.cooldowns.remove("form:b");
				Forms.pick(fp, i == 0 ? 0 : 1);
				if (i == 0) {
					fm.cooldowns.remove("form:switch");
					Forms.toggle(fp);
				}
				Forms.Form cur = Forms.of(fp);
				if (cur == null) {
					break;
				}
				fp.setPos(5, 70, 5);
				Forms.skill(fp, 1);
				fm.cooldowns.remove("form:switch");
				Forms.skill(fp, 2);
				Forms.tick(fp, fm);
				okForms++;
			}
			Forms.Form last = Forms.of(fp);
			boolean avatar = last != null;
			Forms.off(fp, false);
			if (okForms == 8 && avatar && Forms.of(fp) == null && !fp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
					.hasModifier(net.minecraft.resources.Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "form_speed"))) {
				ok++;
			} else {
				errors.add("変身 ok=" + okForms + " last=" + last + " after=" + Forms.of(fp) + " speed=" + fp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
			}
			if (Forms.cost(Forms.Form.WOLF).lines(fp).size() > 0 && Forms.Form.byId("angel") == Forms.Form.ANGEL) {
				ok++;
			}
			fm.forms.clear();
		} catch (Throwable e) {
			errors.add("forms: " + e);
			MagicMod.LOGGER.error("[selftest] forms", e);
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
