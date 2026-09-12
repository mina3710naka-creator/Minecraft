package com.arcanemagic;

import com.arcanemagic.block.ModBlocks;
import com.arcanemagic.item.ModItems;
import com.arcanemagic.network.ModNetworking;
import com.arcanemagic.screen.ModMenus;
import com.arcanemagic.spell.SpellRegistry;

import net.fabricmc.api.ModInitializer;

public class ArcaneMagic implements ModInitializer {

	public static final String MOD_ID = "arcanemagic";

	@Override
	public void onInitialize() {
		SpellRegistry.init();
		ModBlocks.init();
		ModItems.init();
		ModMenus.init();

		ModNetworking.registerCommon();
		ModNetworking.registerServer();
	}
}
