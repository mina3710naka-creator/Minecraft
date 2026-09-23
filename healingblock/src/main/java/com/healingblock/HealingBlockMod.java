package com.healingblock;

import com.healingblock.block.ModBlocks;
import com.healingblock.item.group.ModItemGroup;

import net.fabricmc.api.ModInitializer;

public class HealingBlockMod implements ModInitializer {

	public static final String MOD_ID = "healingblock";

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItemGroup.init();
	}
}
