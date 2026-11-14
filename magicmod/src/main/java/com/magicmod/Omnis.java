package com.magicmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * 最終ボス「万象の魔王オムニス」。8 体の属性超越ボスをすべて倒すと封印の祭壇から呼び出せる。
 * 15 秒ごとに「同調」する属性が変わり、その属性は半減(免疫)、反対の属性が弱点になる。同調中は、その属性の技を使う。
 */
public final class Omnis {

	public static final String ID = "omnis";
	private static final Map<UUID, Element> ATTUNED = new HashMap<>();
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	public static final int PERIOD = 300;

	private Omnis() {
	}

	/** いまの同調属性(まだ無ければ null)。 */
	public static @Nullable Element attunement(LivingEntity e) {
		return ATTUNED.get(e.getUUID());
	}

	/** その属性の反対(弱点)。 */
	public static Element weakOf(Element a) {
		return switch (a) {
			case WIND -> Element.GRAVITY;
			case THUNDER -> Element.WATER;
			case FIRE -> Element.WATER;
			case WATER -> Element.THUNDER;
			case GRAVITY -> Element.WIND;
			case DARK -> Element.LIGHT;
			case LIGHT -> Element.DARK;
			case CURSE -> Element.LIGHT;
		};
	}

	/** 同調する敵(オムニスと終焉神)。 */
	public static boolean attuner(String id) {
		return id.equals(ID) || id.equals(Apocalypse.ID);
	}

	public static boolean unlocked(PlayerMagic pm) {
		for (Enemies.Def d : Enemies.BY_ID.values()) {
			if (d.tier() == Enemies.Tier.BOSS && !d.id().equals(ID) && !d.id().equals(Apocalypse.ID) && !pm.defeated.contains(d.id())) {
				return false;
			}
		}
		return true;
	}

	public static int bossesDefeated(PlayerMagic pm) {
		int n = 0;
		for (Enemies.Def d : Enemies.BY_ID.values()) {
			if (d.tier() == Enemies.Tier.BOSS && !d.id().equals(ID) && !d.id().equals(Apocalypse.ID) && pm.defeated.contains(d.id())) {
				n++;
			}
		}
		return n;
	}

	/** 同調を切り替える(最初の 1 回も)。 */
	public static void attune(Mob m, ServerLevel l, @Nullable Element force) {
		Element cur = ATTUNED.get(m.getUUID());
		Element[] all = Element.values();
		Element next = force;
		while (next == null || next == cur) {
			next = all[l.getRandom().nextInt(all.length)];
		}
		ATTUNED.put(m.getUUID(), next);
		NEXT.put(m.getUUID(), l.getGameTime() + (m.getType() == ModEntities.APOCALYPSE ? 200 : PERIOD));
		Glow.apply(m, next);
		Element w = weakOf(next);
		BossFx.title(l, m.position(), 64, Component.literal("同調: " + next.ja).withStyle(next.fmt, ChatFormatting.BOLD),
				Component.literal(next.ja + "は半減  /  弱点は " + w.ja).withStyle(ChatFormatting.GRAY), 30);
		int[] pal = Vfx.palette(next);
		FxDisplay.circle(l, m.position(), next, m.getBbWidth() + 6, 40, 8f);
		FxDisplay.ring(l, m.position(), next, 12, 10);
		Vfx.burstOut(l, m.position().add(0, m.getBbHeight() * 0.5, 0), Vfx.fade(0xFFFFFF, pal[0], 2.5f), 50, 0.7);
		Vfx.sound(l, m.position(), ModSounds.cast(next), 2f, 0.7f);
		Vfx.shake(l, m.position(), 48, 0.8f, 12);
	}

	/** 毎 tick(Enemies から)。時間が来たら同調を切り替える。 */
	static void tick(Mob m, ServerLevel l) {
		if (!ATTUNED.containsKey(m.getUUID())) {
			attune(m, l, null);
			return;
		}
		if (l.getGameTime() >= NEXT.getOrDefault(m.getUUID(), 0L)) {
			attune(m, l, null);
		}
	}

	static void forget(Mob m) {
		ATTUNED.remove(m.getUUID());
		NEXT.remove(m.getUUID());
	}

	/** 同調している属性の技を出す(円・ドーナツ・直線・十字・連続落下から)。 */
	static Enemies.Act moves() {
		return (m, l, t) -> {
			Element el = attunement(m);
			if (el == null || t == null) {
				return;
			}
			boolean p2 = m.getHealth() <= m.getMaxHealth() * 0.5f;
			List<Enemies.Act> pool = List.of(BossMoves.strike(el, 4.5, 1.0, 3.2f), BossMoves.slam(el, 7, 1.3, 3.4f), BossMoves.rain(el, p2 ? 9 : 6, 3, 1.1, 3.0f),
					BossMoves.donut(el, 3.5, 13, 1.5, 3.4f), BossMoves.cross(el, 16, 2.5, 1.3, 3.2f), BossMoves.line(el, 18, 3, 1.2, 3.2f, true));
			pool.get(l.getRandom().nextInt(pool.size())).run(m, l, t);
		};
	}

	static Enemies.Act spellFlurry() {
		return (m, l, t) -> {
			Element el = attunement(m);
			if (el != null && t != null) {
				for (int no : new int[] { 1, 3, 5 }) {
					Tasks.later(no * 3, () -> Casting.mobCast(m, Spells.get(el, no)));
				}
			}
		};
	}

	public static void reward(net.minecraft.server.level.ServerPlayer p, PlayerMagic pm, List<net.minecraft.world.item.ItemStack> drops, ServerLevel level) {
		drops.add(new net.minecraft.world.item.ItemStack(ModItems.OMNIS_HEART));
		for (Element e : Element.values()) {
			drops.add(new net.minecraft.world.item.ItemStack(ModItems.essence(e), 4));
		}
		drops.add(new net.minecraft.world.item.ItemStack(ModItems.STARDUST_SHARD, 4));
		boolean first = pm.defeated.add(ID);
		pm.skillPoints += first ? 10 : 3;
		for (net.minecraft.server.level.ServerPlayer o : level.players()) {
			o.sendSystemMessage(Component.literal(p.getName().getString() + " が万象の魔王オムニスを討ち取った!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
	}
}
