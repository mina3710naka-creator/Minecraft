package com.magicmod;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** 魔法を撃つ流れ(MP・クールタイム・詠唱・オーバーヒート)。 */
public final class Casting {

	private Casting() {
	}

	public static void msg(ServerPlayer p, String s, ChatFormatting f) {
		p.sendOverlayMessage(Component.literal(s).withStyle(f));
	}

	/** 手に持っている杖(または魔法剣)を返す。どちらの手にも無ければ空。 */
	public static ItemStack heldCaster(ServerPlayer p) {
		ItemStack m = p.getMainHandItem();
		if (Books.isCaster(m)) {
			return m;
		}
		ItemStack o = p.getOffhandItem();
		return Books.isCaster(o) ? o : ItemStack.EMPTY;
	}

	public static void castHeld(ServerPlayer p, @Nullable InteractionHand hand) {
		ItemStack wand = hand == null ? heldCaster(p) : p.getItemInHand(hand);
		if (!Books.isCaster(wand)) {
			wand = heldCaster(p);
		}
		if (wand.isEmpty()) {
			msg(p, "杖を持っていません", ChatFormatting.GRAY);
			return;
		}
		PlayerMagic pm = PlayerMagic.of(p);
		if (!pm.castingSpell.isEmpty()) {
			return;
		}
		Books.Entry e = Books.selectedEntry(wand);
		if (e == null) {
			msg(p, "杖に魔法がありません(魔法テーブルで本に魔法を装着し、本を杖にセット)", ChatFormatting.GRAY);
			return;
		}
		Spell s = Books.resolve(p, e.id());
		if (s == null) {
			msg(p, "この魔法は使えません", ChatFormatting.RED);
			return;
		}
		if (p.hasEffect(ModEffects.SILENCE)) {
			msg(p, "詠唱不可の状態です", ChatFormatting.DARK_PURPLE);
			return;
		}
		if (e.bookLevel() < s.requiredBookLevel()) {
			msg(p, s.name + " には本Lv" + s.requiredBookLevel() + " が必要です", ChatFormatting.RED);
			return;
		}
		long now = p.level().getGameTime();
		long cd = pm.cooldownEnd(s.id);
		if (now < cd) {
			msg(p, s.name + ": クールタイム " + String.format("%.1f", (cd - now) / 20f) + "秒", ChatFormatting.GRAY);
			return;
		}
		float mp = Calc.spellMp(p, s, e.bookLevel(), wand);
		if (!p.isCreative() && pm.mp < mp) {
			msg(p, "MPが足りません(" + Math.round(pm.mp) + " / " + Math.round(mp) + ")  殴ってMPを回復しよう", ChatFormatting.BLUE);
			return;
		}
		int ticks = Calc.castTicks(p, s, wand);
		if (ticks > 0) {
			pm.castingSpell = e.id();
			pm.castTicks = ticks;
			pm.castTotal = ticks;
			pm.castSlotIndex = Books.selected(wand);
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks + 2, 1, false, false));
			FxDisplay.circle((net.minecraft.server.level.ServerLevel) p.level(), p.position(), s.element, 1.4 + ticks * 0.08, ticks + 8, 6f);
			pm.dirty = true;
			return;
		}
		execute(p, wand, e, s);
	}

	/** 詠唱の進行(毎 tick)。 */
	static void tickCast(ServerPlayer p, PlayerMagic pm) {
		if (pm.castingSpell.isEmpty()) {
			return;
		}
		ItemStack wand = heldCaster(p);
		Books.Entry e = wand.isEmpty() ? null : Books.selectedEntry(wand);
		if (e == null || !e.id().equals(pm.castingSpell) || p.hasEffect(ModEffects.SILENCE)
				|| p.hasEffect(ModEffects.STUN) || p.hasEffect(ModEffects.PARALYSIS)) {
			pm.castingSpell = "";
			msg(p, "詠唱が中断されました", ChatFormatting.GRAY);
			pm.dirty = true;
			return;
		}
		pm.castTicks--;
		Spell cs = Books.resolve(p, e.id());
		Vfx.channel(p, cs == null ? null : cs.element, 1f - pm.castTicks / (float) Math.max(1, pm.castTotal));
		if (pm.castTicks <= 0) {
			pm.castingSpell = "";
			Spell s = Books.resolve(p, e.id());
			if (s != null) {
				execute(p, wand, e, s);
			}
			pm.dirty = true;
		}
	}

	static void execute(ServerPlayer p, ItemStack wand, Books.Entry e, Spell s) {
		PlayerMagic pm = PlayerMagic.of(p);
		long now = p.level().getGameTime();
		float mp = Calc.spellMp(p, s, e.bookLevel(), wand);
		if (!p.isCreative()) {
			if (pm.mp < mp) {
				msg(p, "MPが足りません", ChatFormatting.BLUE);
				return;
			}
			pm.mp -= mp;
		}
		float ct = Calc.spellCt(p, s, wand);
		pm.cooldowns.put(s.id, now + Math.round(ct * 20));
		// オーバーヒート(設計書 10.2)
		boolean recent = now - pm.lastCast.getOrDefault(s.element, -1000L) < 60;
		float add = s.id.equals(pm.lastSpell) && now - pm.lastSpellTick < 60 ? 2f : recent ? 1f : 0f;
		pm.overheat.put(s.element, Math.min(10f, pm.overheat(s.element) + add));
		pm.lastCast.put(s.element, now);
		pm.lastSpell = s.id;
		pm.lastSpellTick = now;
		pm.fightingSpiritUntil = 0;
		pm.lastCombat = now;

		Vfx.cast(p, s.element, s.fusion ? "max" : s.synthesized ? "upper" : s.rank);
		Ctx c = new Ctx(p, s, Calc.spellPower(p, s, e.bookLevel(), wand));
		c.durMult = Calc.durationMult(p, s, wand);
		c.setChanceMult(Calc.chanceMult(p, s));
		s.form.cast(c);
		pm.pendingBookExp.merge(e.bookElement(), 2f, Float::sum);
		pm.dirty = true;
	}

	/** 魔法型の基本攻撃「魔封じの型」(MP・CT 消費なし、当たると MP 少し回復。設計書 13.2)。 */
	public static void sealStrike(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (pm.archetype != Archetype.MAGIC) {
			return;
		}
		ItemStack wand = heldCaster(p);
		Element el = wand.isEmpty() ? null : Books.firstElement(wand);
		if (el == null) {
			return;
		}
		long now = p.level().getGameTime();
		if (now < pm.cooldownEnd("seal")) {
			return;
		}
		if (p.hasEffect(ModEffects.SILENCE)) {
			return;
		}
		Spell s = Spells.rapidOf(el);
		pm.cooldowns.put("seal", now + Math.max(6, Math.round(s.ct * Calc.arch(pm).spellCt() * 20)));
		Vfx.cast(p, s.element, "rapid");
		Ctx c = new Ctx(p, s, Calc.spellPower(p, s, 1, wand));
		c.seal = true;
		s.form.cast(c);
		pm.lastCombat = now;
	}

	public static void cycle(ServerPlayer p, int dir) {
		ItemStack wand = heldCaster(p);
		if (wand.isEmpty()) {
			return;
		}
		List<Books.Entry> l = Books.entries(wand);
		if (l.isEmpty()) {
			return;
		}
		int i = Math.floorMod(Books.selected(wand) + dir, l.size());
		Books.setSelected(wand, i);
		Spell s = Books.resolve(p, l.get(i).id());
		if (s != null) {
			msg(p, "▶ " + s.name + "(" + s.element.ja + "・" + s.rankName() + ")", s.element.fmt);
		}
		p.level().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), p.getSoundSource(), 0.3f, 1.6f);
		PlayerMagic.of(p).dirty = true;
	}

	/** 敵(MOD のモンスター)が魔法を撃つ。 */
	public static void mobCast(LivingEntity mob, Spell s) {
		if (s == null || mob.hasEffect(ModEffects.SILENCE) || mob.hasEffect(ModEffects.STUN)) {
			return;
		}
		Vfx.cast(mob, s.element, s.rank);
		Ctx c = new Ctx(mob, s, Calc.spellPower(mob, s, 1, ItemStack.EMPTY));
		s.form.cast(c);
	}
}
