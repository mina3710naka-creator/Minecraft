package com.magicmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 冒険者ギルド(掲示板ブロック)。毎日 4 件の依頼が出て、3 件まで同時に受けられる。
 * 討伐(属性・中ボス以上・なんでも)と納品があり、報酬は素材・使い魔のえさ・スキルポイント。
 * 達成数でランクが上がり、報酬が少し増える。
 */
public final class Guild {

	public static final int MAX_ACTIVE = 3;

	/** 依頼 1 件。報酬は「sp:1;item:magicmod:fire_crystal:5;…」の文字列で持つ。 */
	public record Quest(String id, String kind, String arg, int need, int have, String reward, String label) {

		String encode() {
			return String.join("|", id, kind, arg, String.valueOf(need), String.valueOf(have), reward, label);
		}

		static Quest decode(String s) {
			String[] a = s.split("\\|", 7);
			return new Quest(a[0], a[1], a[2], Integer.parseInt(a[3]), Integer.parseInt(a[4]), a[5], a[6]);
		}

		boolean done(ServerPlayer p) {
			return kind.equals("collect") ? count(p, item(arg)) >= need : have >= need;
		}

		Quest with(int newHave) {
			return new Quest(id, kind, arg, need, newHave, reward, label);
		}
	}

	private Guild() {
	}

	static long day(ServerPlayer p) {
		return p.level().getGameTime() / 24000L;
	}

	public static int rank(PlayerMagic pm) {
		return 1 + pm.questsDone / 5;
	}

	static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}

	static String itemId(Item i) {
		return BuiltInRegistries.ITEM.getKey(i).toString();
	}

	static int count(ServerPlayer p, Item it) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(it)) {
				n += s.getCount();
			}
		}
		return n;
	}

	static void take(ServerPlayer p, Item it, int n) {
		for (int i = 0; i < p.getInventory().getContainerSize() && n > 0; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(it)) {
				int k = Math.min(n, s.getCount());
				s.shrink(k);
				n -= k;
			}
		}
	}

	public static List<Quest> active(PlayerMagic pm) {
		List<Quest> l = new ArrayList<>();
		for (String s : pm.quests) {
			try {
				l.add(Quest.decode(s));
			} catch (RuntimeException e) {
				// 壊れた依頼は無視する
			}
		}
		return l;
	}

	static void save(PlayerMagic pm, List<Quest> l) {
		pm.quests.clear();
		l.forEach(q -> pm.quests.add(q.encode()));
		pm.dirty = true;
	}

	/** 今日の依頼(プレイヤーと日付で決まる。受注・達成済みは除く)。 */
	public static List<Quest> offers(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		long day = day(p);
		pm.questTaken.removeIf(s -> !s.startsWith("d" + day + "#"));
		Random r = new Random(p.getUUID().getLeastSignificantBits() * 31 + day * 7919);
		int rank = rank(pm);
		float mult = 1 + 0.1f * Math.min(10, rank - 1);
		Element[] el = Element.values();
		List<Quest> all = new ArrayList<>();
		// 1: 属性の敵を倒す
		Element e = el[r.nextInt(8)];
		int n = 6 + r.nextInt(7);
		all.add(new Quest("d" + day + "#0", "hunt_el", e.id, n, 0,
				"item:" + itemId(ModItems.crystal(e)) + ":" + Math.round((3 + n / 2) * mult) + ";item:" + itemId(ModItems.CRYSTAL_SHARD) + ":" + Math.round(4 * mult),
				e.ja + "属性の敵を " + n + " 体倒す"));
		// 2: 中ボス以上を倒す
		Element e2 = el[r.nextInt(8)];
		int n2 = 1 + r.nextInt(2);
		all.add(new Quest("d" + day + "#1", "hunt_tier", "mid", n2, 0,
				"sp:1;item:" + itemId(ModItems.essence(e2)) + ":" + Math.round(n2 * mult) + ";item:" + itemId(ModItems.RUNE_STONE) + ":" + (1 + n2),
				"中ボス以上を " + n2 + " 体倒す"));
		// 3: 素材の納品
		Item[] goods = { ModItems.CRYSTAL_SHARD, ModItems.SILVER_INGOT, net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.BONE,
				net.minecraft.world.item.Items.FEATHER };
		Item g = goods[r.nextInt(goods.length)];
		int n3 = 12 + r.nextInt(13);
		Familiars.Kind fk = Familiars.Kind.values()[r.nextInt(Familiars.Kind.values().length)];
		Item food = Familiars.foods(fk).get(0);
		all.add(new Quest("d" + day + "#2", "collect", itemId(g), n3, 0,
				"item:" + itemId(food) + ":" + Math.round(10 * mult) + ";item:" + itemId(ModItems.SILVER_INGOT) + ":" + Math.round(3 * mult),
				g.getDefaultInstance().getHoverName().getString() + " を " + n3 + " 個納品する"));
		// 4: なんでもいいから敵を倒す
		int n4 = 12 + r.nextInt(9);
		all.add(new Quest("d" + day + "#3", "hunt_any", "any", n4, 0,
				"item:" + itemId(ModItems.SYNTH_CATALYST) + ":" + Math.round(2 * mult) + ";item:" + itemId(ModItems.CRYSTAL_SHARD) + ":" + Math.round(6 * mult),
				"魔法の敵を " + n4 + " 体倒す"));
		all.removeIf(q -> pm.questTaken.contains(q.id));
		return all;
	}

	public static String rewardText(String reward) {
		StringBuilder sb = new StringBuilder();
		for (String part : reward.split(";")) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append("、");
			}
			if (part.startsWith("sp:")) {
				sb.append("スキルポイント ").append(part.substring(3));
			} else if (part.startsWith("item:")) {
				int c = part.lastIndexOf(':');
				sb.append(item(part.substring(5, c)).getDefaultInstance().getHoverName().getString()).append(" ×").append(part.substring(c + 1));
			}
		}
		return sb.toString();
	}

	static void give(ServerPlayer p, String reward) {
		for (String part : reward.split(";")) {
			if (part.startsWith("sp:")) {
				PlayerMagic.of(p).skillPoints += Integer.parseInt(part.substring(3));
			} else if (part.startsWith("item:")) {
				int c = part.lastIndexOf(':');
				Status.give(p, new ItemStack(item(part.substring(5, c)), Integer.parseInt(part.substring(c + 1))));
			}
		}
	}

	/** 敵(MOD のモンスター)を倒したときの進捗。 */
	public static void onKill(ServerPlayer p, Enemies.Def d) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (pm.quests.isEmpty()) {
			return;
		}
		List<Quest> l = active(pm);
		boolean changed = false;
		for (int i = 0; i < l.size(); i++) {
			Quest q = l.get(i);
			if (q.kind.equals("collect") || q.have >= q.need) {
				continue;
			}
			boolean match = switch (q.kind) {
				case "hunt_el" -> d.element().id.equals(q.arg);
				case "hunt_tier" -> d.tier() == Enemies.Tier.MIDBOSS || d.tier() == Enemies.Tier.BOSS;
				default -> true;
			};
			if (match) {
				Quest n = q.with(q.have + 1);
				l.set(i, n);
				changed = true;
				Casting.msg(p, "依頼「" + q.label + "」 " + n.have + "/" + q.need + (n.have >= q.need ? "  ギルドで報告しよう" : ""),
						n.have >= q.need ? ChatFormatting.GOLD : ChatFormatting.YELLOW);
			}
		}
		if (changed) {
			save(pm, l);
		}
	}

	/** ギルド画面の操作。 */
	public static String act(ServerPlayer p, String act) {
		PlayerMagic pm = PlayerMagic.of(p);
		List<Quest> l = active(pm);
		if (act.startsWith("take:")) {
			String id = act.substring(5);
			if (l.size() >= MAX_ACTIVE) {
				return "同時に受けられる依頼は " + MAX_ACTIVE + " 件までです";
			}
			for (Quest q : offers(p)) {
				if (q.id.equals(id)) {
					l.add(q);
					pm.questTaken.add(id);
					save(pm, l);
					p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, p.getSoundSource(), 1f, 1f);
					return null;
				}
			}
			return "その依頼はもうありません";
		}
		if (act.startsWith("turn:") || act.startsWith("drop:")) {
			int i = Integer.parseInt(act.substring(5));
			if (i < 0 || i >= l.size()) {
				return null;
			}
			Quest q = l.get(i);
			if (act.startsWith("drop:")) {
				l.remove(i);
				save(pm, l);
				return null;
			}
			if (!q.done(p)) {
				return "まだ達成していません";
			}
			if (q.kind.equals("collect")) {
				take(p, item(q.arg), q.need);
			}
			l.remove(i);
			save(pm, l);
			give(p, q.reward);
			int before = rank(pm);
			pm.questsDone++;
			p.sendSystemMessage(Component.literal("依頼完了!「" + q.label + "」  報酬: " + rewardText(q.reward)).withStyle(ChatFormatting.GOLD));
			if (rank(pm) > before) {
				p.sendSystemMessage(Component.literal("ギルドランクが " + rank(pm) + " になった!(報酬が増えます)").withStyle(ChatFormatting.AQUA));
			}
			p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, p.getSoundSource(), 0.8f, 1.2f);
			pm.dirty = true;
			return null;
		}
		return null;
	}

	static Ui.Model screen(ServerPlayer p) {
		Ui.Model m = new Ui.Model("guild", "冒険者ギルド");
		PlayerMagic pm = PlayerMagic.of(p);
		m.info("毎日 4 件の依頼が出ます。同時に " + MAX_ACTIVE + " 件まで受けられます。達成するとランクが上がり、報酬が増えます。");
		m.info("§7ギルドランク " + rank(pm) + " (達成 " + pm.questsDone + " 件)");
		List<Quest> l = active(pm);
		m.info("§6受注中の依頼");
		if (l.isEmpty()) {
			m.info("  §8(なし)");
		}
		for (int i = 0; i < l.size(); i++) {
			Quest q = l.get(i);
			int have = q.kind.equals("collect") ? Math.min(q.need, count(p, item(q.arg))) : q.have;
			var row = m.row("§f" + q.label + " §7(" + have + "/" + q.need + ")", List.of("§6報酬: " + rewardText(q.reward)), "報告", "turn:" + i, q.done(p));
			m.row2(row, "破棄", "drop:" + i, true);
		}
		m.info("§e今日の依頼");
		for (Quest q : offers(p)) {
			m.row("§f" + q.label, List.of("§6報酬: " + rewardText(q.reward)), "受注", "take:" + q.id, l.size() < MAX_ACTIVE);
		}
		return m;
	}
}
