package com.magicmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 通信(状態の同期・キー操作・画面)。 */
public final class Net {

	/** クライアント側で合成魔法の名前を引くための表(サーバーからの同期で埋まる)。 */
	public static final Map<String, String> CLIENT_SYNTH_NAMES = new HashMap<>();

	private static Identifier id(String s) {
		return Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, s);
	}

	/** サーバー → クライアント: HUD 用の状態。 */
	public record SyncPayload(CompoundTag tag) implements CustomPacketPayload {
		public static final Type<SyncPayload> TYPE = new Type<>(id("sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.COMPOUND_TAG, SyncPayload::tag, SyncPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** サーバー → クライアント: 画面(テーブル・祭壇・ステータス)を開く/更新する。 */
	public record UiPayload(CompoundTag tag) implements CustomPacketPayload {
		public static final Type<UiPayload> TYPE = new Type<>(id("ui"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UiPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.COMPOUND_TAG, UiPayload::tag, UiPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** サーバー → クライアント: 画面の揺れ・光。 */
	public record ScreenFxPayload(String kind, float strength, int ticks, int color) implements CustomPacketPayload {
		public static final Type<ScreenFxPayload> TYPE = new Type<>(id("screen_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScreenFxPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ScreenFxPayload::kind, ByteBufCodecs.FLOAT, ScreenFxPayload::strength,
				ByteBufCodecs.VAR_INT, ScreenFxPayload::ticks, ByteBufCodecs.INT, ScreenFxPayload::color, ScreenFxPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** クライアント → サーバー: キー操作。 */
	public record KeyPayload(String action, int value) implements CustomPacketPayload {
		public static final Type<KeyPayload> TYPE = new Type<>(id("key"));
		public static final StreamCodec<RegistryFriendlyByteBuf, KeyPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, KeyPayload::action, ByteBufCodecs.VAR_INT, KeyPayload::value, KeyPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** クライアント → サーバー: 画面のボタン。 */
	public record UiActionPayload(String screen, String action) implements CustomPacketPayload {
		public static final Type<UiActionPayload> TYPE = new Type<>(id("ui_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UiActionPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, UiActionPayload::screen, ByteBufCodecs.STRING_UTF8, UiActionPayload::action,
				UiActionPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private Net() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UiPayload.TYPE, UiPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScreenFxPayload.TYPE, ScreenFxPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(KeyPayload.TYPE, KeyPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UiActionPayload.TYPE, UiActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(KeyPayload.TYPE, (pl, ctx) -> {
			ServerPlayer p = ctx.player();
			ctx.server().execute(() -> onKey(p, pl.action(), pl.value()));
		});
		ServerPlayNetworking.registerGlobalReceiver(UiActionPayload.TYPE, (pl, ctx) -> {
			ServerPlayer p = ctx.player();
			ctx.server().execute(() -> Ui.action(p, pl.screen(), pl.action()));
		});
	}

	private static void onKey(ServerPlayer p, String action, int value) {
		if (!p.isAlive()) {
			return;
		}
		switch (action) {
			case "cast" -> Casting.castHeld(p, null);
			case "cycle" -> Casting.cycle(p, value);
			case "seal" -> Casting.sealStrike(p);
			case "tech" -> Techniques.use(p);
			case "tech_cycle" -> Techniques.cycle(p, value);
			case "familiar" -> Familiars.toggle(p);
			case "status" -> Ui.open(p, "status", null);
			case "double_jump" -> Techniques.doubleJump(p);
			case "dodge" -> Dodge.use(p, value);
			case "select" -> {
				ItemStack w = Casting.heldCaster(p);
				if (!w.isEmpty() && value >= 0 && value < Books.entries(w).size()) {
					Books.setSelected(w, value);
					Spell s = Books.resolve(p, Books.entries(w).get(value).id());
					if (s != null) {
						Casting.msg(p, "▶ " + s.name + "(" + s.element.ja + "・" + s.rankName() + ")", s.element.fmt);
					}
					PlayerMagic.of(p).dirty = true;
				}
			}
			default -> {
			}
		}
	}

	public static String spellName(String id) {
		if (id.startsWith("syn:")) {
			return "◆" + CLIENT_SYNTH_NAMES.getOrDefault(id.substring(4), "合成魔法");
		}
		Spell s = Spells.get(id);
		return s == null ? id : s.name;
	}

	// ---------------------------------------------------------------------------------------------

	static void sync(ServerPlayer p, PlayerMagic pm) {
		if (p.level().getGameTime() % 2 != 0 && !pm.dirty) {
			return;
		}
		pm.dirty = false;
		CompoundTag t = new CompoundTag();
		t.putInt("mp", Math.round(pm.mp * 10));
		t.putInt("maxMp", Math.round(Calc.maxMp(pm)));
		t.putInt("st", Math.round(pm.stamina * 10));
		t.putInt("maxSt", Math.round(Calc.maxStamina(pm)));
		t.putString("arch", pm.archetype.ja);
		t.putInt("sp", pm.skillPoints);
		ItemStack wand = Casting.heldCaster(p);
		if (!wand.isEmpty()) {
			List<Books.Entry> es = Books.entries(wand);
			Books.Entry e = Books.selectedEntry(wand);
			if (e != null) {
				Spell s = Books.resolve(p, e.id());
				if (s != null) {
					t.putString("spell", s.name);
					t.putString("spellEl", s.element.id);
					t.putString("icon", Ui.spellIcon(s));
					t.putLong("spellCd", pm.cooldownEnd(s.id));
					t.putInt("spellCt", Math.round(Calc.spellCt(p, s, wand) * 20));
					t.putInt("spellMp", Math.round(Calc.spellMp(p, s, e.bookLevel(), wand)));
					t.putInt("count", es.size());
					t.putInt("index", Math.floorMod(Books.selected(wand), es.size()) + 1);
					t.putFloat("heat", pm.overheat(s.element));
				}
			}
		}
		if (!pm.castingSpell.isEmpty()) {
			t.putInt("castT", pm.castTicks);
			t.putInt("castTotal", pm.castTotal);
		}
		Techniques.Tech tech = Techniques.selected(p);
		if (tech != null) {
			t.putString("tech", tech.name);
			t.putLong("techCd", pm.cooldownEnd("t:" + tech.id));
			t.putInt("techSt", Math.round(Calc.techStamina(p, tech, p.getMainHandItem())));
		}
		if (!wand.isEmpty()) {
			ListTag wheel = new ListTag();
			for (Books.Entry we : Books.entries(wand)) {
				Spell ws = Books.resolve(p, we.id());
				if (ws == null) {
					continue;
				}
				CompoundTag wc = new CompoundTag();
				wc.putString("n", ws.name);
				wc.putString("i", Ui.spellIcon(ws));
				wc.putString("e", ws.element.id);
				wc.putInt("mp", Math.round(Calc.spellMp(p, ws, we.bookLevel(), wand)));
				wc.putBoolean("s", ws.synthesized);
				wheel.add(wc);
			}
			t.put("wheel", wheel);
			t.putInt("sel", Math.floorMod(Books.selected(wand), Math.max(1, wheel.size())));
		}
		long nowT = p.level().getGameTime();
		if (pm.combo > 0 && nowT <= pm.comboUntil) {
			t.putInt("combo", pm.combo);
			t.putInt("comboLeft", (int) (pm.comboUntil - nowT));
			t.putInt("comboTier", pm.comboTier);
		}
		if (nowT < pm.justDodgeUntil) {
			t.putInt("justLeft", (int) (pm.justDodgeUntil - nowT));
		}
		t.putLong("dodgeReady", pm.dodgeReadyAt);
		ListTag fam = new ListTag();
		for (String f : Familiars.describeActive(p, pm)) {
			fam.add(StringTag.valueOf(f));
		}
		t.put("fam", fam);
		CompoundTag syn = new CompoundTag();
		pm.synths.forEach((k, v) -> syn.putString(k, v.name()));
		t.put("syn", syn);
		int h = t.hashCode();
		if (h != pm.syncHash || p.level().getGameTime() % 40 == 0) {
			pm.syncHash = h;
			ServerPlayNetworking.send(p, new SyncPayload(t));
		}
	}

	static void screenFx(ServerPlayer p, String kind, float strength, int ticks, int color) {
		if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer || !ServerPlayNetworking.canSend(p, ScreenFxPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(p, new ScreenFxPayload(kind, strength, ticks, color));
	}

	static void sendUi(ServerPlayer p, CompoundTag tag) {
		ServerPlayNetworking.send(p, new UiPayload(tag));
	}
}
