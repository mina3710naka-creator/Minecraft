package com.magicmod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 刻印・装備の付け外し。 */
public final class Status {

	private Status() {
	}

	public static boolean inCombat(ServerPlayer p) {
		return p.level().getGameTime() - PlayerMagic.of(p).lastCombat < 200;
	}

	/** 戦闘中の切り替えは長いクールタイムつき(設計書 4.9)。 */
	private static boolean canSwap(ServerPlayer p, PlayerMagic pm) {
		long now = p.level().getGameTime();
		if (!inCombat(p)) {
			return true;
		}
		if (now < pm.runeSwapReady) {
			Casting.msg(p, "戦闘中は刻印を切り替えられません(あと " + (pm.runeSwapReady - now) / 20 + " 秒)", ChatFormatting.RED);
			return false;
		}
		pm.runeSwapReady = now + 20 * 60;
		return true;
	}

	public static boolean engrave(ServerPlayer p, Runes r) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (!canSwap(p, pm)) {
			return false;
		}
		if (pm.runes.contains(r.id)) {
			Casting.msg(p, "同じ刻印はすでに刻まれています(重ね掛け不可)", ChatFormatting.GRAY);
			return false;
		}
		if (r == Runes.CONQUEROR && !pm.runes.isEmpty() || r != Runes.CONQUEROR && pm.runes.contains(Runes.CONQUEROR.id)) {
			Casting.msg(p, "覇者の刻印は他の刻印と併用できません(ステータス画面 [K] で外せます)", ChatFormatting.RED);
			return false;
		}
		if (pm.runes.size() >= Runes.slots(pm)) {
			Casting.msg(p, "オーラスロットがいっぱいです(ステータス画面 [K] で外せます)", ChatFormatting.RED);
			return false;
		}
		pm.runes.add(r.id);
		p.sendSystemMessage(Component.literal(r.ja() + " を刻んだ: " + r.row().effect()).withStyle(ChatFormatting.AQUA));
		p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, p.getSoundSource(), 1f, 1.2f);
		return true;
	}

	public static void removeRune(ServerPlayer p, String id) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (!pm.runes.contains(id) || !canSwap(p, pm)) {
			return;
		}
		pm.runes.remove(id);
		Runes r = Runes.byId(id);
		if (r != null) {
			give(p, new ItemStack(ModItems.RUNES.get(r)));
		}
	}

	public static void equipGear(ServerPlayer p, InteractionHand hand) {
		ItemStack s = p.getItemInHand(hand);
		if (!(s.getItem() instanceof GearItem g)) {
			return;
		}
		PlayerMagic pm = PlayerMagic.of(p);
		if (!g.kind.canEquip(pm.archetype)) {
			Casting.msg(p, g.kind.only.ja + "(または均衡型)専用の装備です", ChatFormatting.RED);
			return;
		}
		int slot = g.kind.slot.ordinal();
		ItemStack old = gearStack(pm, slot);
		pm.accessories[slot] = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
		CompoundTag d = Books.data(s);
		pm.accessoryData[slot] = d.isEmpty() ? null : d;
		s.shrink(1);
		if (!old.isEmpty()) {
			give(p, old);
		}
		p.sendSystemMessage(Component.literal(g.kind.slot.ja + "に " + g.kind.ja + " を装備した").withStyle(ChatFormatting.AQUA));
		p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), p.getSoundSource(), 1f, 1f);
		pm.dirty = true;
	}

	public static ItemStack gearStack(PlayerMagic pm, int slot) {
		String id = pm.accessories[slot];
		if (id == null) {
			return ItemStack.EMPTY;
		}
		Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
		ItemStack s = new ItemStack(it);
		if (pm.accessoryData[slot] != null) {
			Books.setData(s, pm.accessoryData[slot].copy());
		}
		return s;
	}

	public static void unequip(ServerPlayer p, int slot) {
		PlayerMagic pm = PlayerMagic.of(p);
		ItemStack old = gearStack(pm, slot);
		pm.accessories[slot] = null;
		pm.accessoryData[slot] = null;
		if (!old.isEmpty()) {
			give(p, old);
		}
	}

	public static void give(ServerPlayer p, ItemStack s) {
		if (!p.getInventory().add(s)) {
			p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}
}
