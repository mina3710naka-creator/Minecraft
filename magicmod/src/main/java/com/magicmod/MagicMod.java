package com.magicmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * 魔法MOD(設計書 v5 + 数値バランス表 v3)。
 * 魔法の本 × 杖 × 魔法テーブル × 装備 × スキルツリー、物理わざ、アーキタイプ、使い魔、刻印、合成、武器合体、敵。
 */
public class MagicMod implements ModInitializer {

	public static final String MOD_ID = "magicmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Balance.get();
		ModEffects.init();
		ModEntities.init();
		ModBlocks.init();
		ModItems.init();
		Object attach = PlayerMagic.TYPE;
		Spells.init();
		Techniques.init();
		Enemies.init();
		Tasks.init();
		Combat.init();
		StatusVfx.init();
		Net.init();
		for (String f : new String[] { "ore_crystal", "ore_crystal_deep", "ore_silver" }) {
			BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES,
					ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(MOD_ID, f)));
		}
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("magic")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("learnall").executes(c -> {
					PlayerMagic pm = PlayerMagic.of(c.getSource().getPlayerOrException());
					Spells.all().forEach(s -> pm.learned.add(s.id));
					Techniques.CATEGORIES.forEach(cat -> Techniques.of(cat).forEach(t -> pm.techLearned.add(t.id)));
					return ok(c, "すべての魔法・物理わざを習得しました");
				}))
				.then(Commands.literal("sp").then(Commands.argument("n", IntegerArgumentType.integer(0, 999)).executes(c -> {
					PlayerMagic.of(c.getSource().getPlayerOrException()).skillPoints += IntegerArgumentType.getInteger(c, "n");
					return ok(c, "スキルポイントを追加しました");
				})))
				.then(Commands.literal("archetype").then(Commands.argument("type", StringArgumentType.word()).executes(c -> {
					Archetype a = Archetype.byId(StringArgumentType.getString(c, "type"));
					PlayerMagic.of(c.getSource().getPlayerOrException()).archetype = a;
					return ok(c, "アーキタイプ: " + a.ja + "(physical / magic / balanced)");
				})))
				.then(Commands.literal("mastery").then(Commands.argument("n", IntegerArgumentType.integer(0, 99999)).executes(c -> {
					PlayerMagic pm = PlayerMagic.of(c.getSource().getPlayerOrException());
					Techniques.CATEGORIES.forEach(cat -> pm.mastery.put(cat, IntegerArgumentType.getInteger(c, "n")));
					return ok(c, "全カテゴリの熟練度EXPを設定しました");
				})))
				.then(Commands.literal("kit").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Status.give(p, new ItemStack(ModItems.WANDS.get(4)));
					for (Element e : Element.values()) {
						CompoundTag b = Books.newBook(e);
						b.putInt("lv", 5);
						b.putInt("exp", Books.EXP_TO_LEVEL[4]);
						Books.setSlots(b, Books.slots(b));
						Status.give(p, Books.toItem(b));
					}
					for (var it : ModBlocks.ITEMS.values()) {
						Status.give(p, new ItemStack(it));
					}
					Status.give(p, new ItemStack(ModItems.MAGIC_GUIDE));
					return ok(c, "テスト用セット(星屑の杖・Lv5の本×8・テーブルと祭壇)を渡しました");
				}))
				.then(Commands.literal("fill").executes(c -> {
					PlayerMagic pm = PlayerMagic.of(c.getSource().getPlayerOrException());
					pm.mp = Calc.maxMp(pm);
					pm.stamina = Calc.maxStamina(pm);
					pm.cooldowns.clear();
					pm.overheat.clear();
					return ok(c, "MP・スタミナ・クールタイムを回復しました");
				}))
				.then(Commands.literal("ui").then(Commands.argument("screen", StringArgumentType.word()).executes(c -> {
					Ui.open(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "screen"),
							c.getSource().getPlayerOrException().blockPosition());
					return 1;
				})))
				.then(Commands.literal("cast").executes(c -> {
					Casting.castHeld(c.getSource().getPlayerOrException(), null);
					return 1;
				}))
				.then(Commands.literal("selftest").executes(c -> SelfTest.run(c.getSource())))
				.then(Commands.literal("reset").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Familiars.dismissAll(p);
					p.setAttached(PlayerMagic.TYPE, new PlayerMagic());
					return ok(c, "魔法MODのデータをリセットしました");
				}))));
		LOGGER.info("[magicmod] 魔法 {} 種・物理わざ {} 種・敵 {} 種を読み込みました", Spells.all().size(), 63, Enemies.BY_ID.size());
		if (attach == null) {
			LOGGER.warn("attachment not registered");
		}
	}

	private static int ok(CommandContext<CommandSourceStack> c, String s) {
		c.getSource().sendSuccess(() -> Component.literal(s).withStyle(ChatFormatting.AQUA), false);
		return 1;
	}
}
