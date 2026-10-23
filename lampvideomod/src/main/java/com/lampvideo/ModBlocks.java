package com.lampvideo;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 光らない専用ブロック。見た目はレッドストーンランプの消灯/点灯と同じ(テクスチャは本物のランプのものを使う)だが、
 * 光を出さず、光を通さない性質も同じなので、切り替えても光の計算が走らない。画面用ブロックの中でいちばん軽い。
 */
public final class ModBlocks {

	public static final Block LAMP_OFF = register("lamp_off");
	public static final Block LAMP_ON = register("lamp_on");

	private ModBlocks() {
	}

	private static Block register(String path) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
				Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		Block block = new Block(BlockBehaviour.Properties.of().setId(key).strength(0.3F).sound(SoundType.GLASS));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	/** クラスを読み込んで登録を走らせる。 */
	public static void init() {
	}
}
