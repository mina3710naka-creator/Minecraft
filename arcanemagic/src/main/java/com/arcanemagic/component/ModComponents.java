package com.arcanemagic.component;

import com.arcanemagic.ArcaneMagic;

import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModComponents {

	public static final ComponentType<WandData> WAND_DATA = register("wand_data",
			ComponentType.<WandData>builder().codec(WandData.CODEC).packetCodec(WandData.PACKET_CODEC).build());

	public static final ComponentType<SpellbookData> SPELLBOOK_DATA = register("spellbook_data",
			ComponentType.<SpellbookData>builder().codec(SpellbookData.CODEC).packetCodec(SpellbookData.PACKET_CODEC).build());

	private ModComponents() {
	}

	private static <T> ComponentType<T> register(String path, ComponentType<T> type) {
		return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(ArcaneMagic.MOD_ID, path), type);
	}

	public static void init() {
		// クラスロード時にstaticフィールドが登録されるため、呼び出すだけでよい。
	}
}
