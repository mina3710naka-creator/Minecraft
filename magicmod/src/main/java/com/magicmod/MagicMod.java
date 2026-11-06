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
import net.minecraft.core.BlockPos;
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
		Settings.get();
		ModEffects.init();
		ModParticles.init();
		ModSounds.init();
		ModEntities.init();
		ModBlocks.init();
		ModItems.init();
		Object attach = PlayerMagic.TYPE;
		Ruins.register();
		GuildHall.register();
		Guild.init();
		Spells.init();
		Techniques.init();
		Enemies.init();
		Tasks.init();
		Combat.init();
		StatusVfx.init();
		Familiars.initPersistence();
		Net.init();
		registerRuinSpawns();
		BiomeModifications.addFeature(BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_FOREST).or(BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.PLAINS,
				net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS, net.minecraft.world.level.biome.Biomes.TAIGA, net.minecraft.world.level.biome.Biomes.SAVANNA)),
				GenerationStep.Decoration.SURFACE_STRUCTURES, ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(MOD_ID, "guild_hall")));
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
				.then(Commands.literal("loadout").then(Commands.argument("element", StringArgumentType.word())
						.then(Commands.argument("from", IntegerArgumentType.integer(1, 13)).executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							Element e = Element.byId(StringArgumentType.getString(c, "element"));
							if (e == null) {
								return 0;
							}
							PlayerMagic pm = PlayerMagic.of(p);
							Spells.all().forEach(s -> pm.learned.add(s.id));
							CompoundTag b = Books.newBook(e);
							b.putInt("lv", 5);
							java.util.List<String> slots = new java.util.ArrayList<>();
							int from = IntegerArgumentType.getInteger(c, "from");
							for (int i = 0; i < 6; i++) {
								slots.add(from + i <= 13 ? Spells.id(e, from + i) : "");
							}
							Books.setSlots(b, slots);
							ItemStack wand = new ItemStack(ModItems.WANDS.get(4));
							Books.setWandBooks(wand, java.util.List.of(b));
							p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);
							return ok(c, e.ja + " の魔法 " + from + "〜" + (from + 5) + " を入れた杖を持たせました");
						}))))
				.then(Commands.literal("select").then(Commands.argument("i", IntegerArgumentType.integer(0, 30)).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					ItemStack w = Casting.heldCaster(p);
					Books.setSelected(w, IntegerArgumentType.getInteger(c, "i"));
					PlayerMagic.of(p).cooldowns.clear();
					return 1;
				})))
				.then(Commands.literal("familiar").then(Commands.argument("kind", StringArgumentType.word())
						.then(Commands.argument("lv", IntegerArgumentType.integer(1, 10)).then(Commands.argument("ride", IntegerArgumentType.integer(0, 1))
								.executes(c -> {
									var p = c.getSource().getPlayerOrException();
									PlayerMagic pm = PlayerMagic.of(p);
									Familiars.Kind k = Familiars.Kind.byId(StringArgumentType.getString(c, "kind"));
									if (k == null) {
										return ok(c, "kind: wolf mud_slime fairy hawk golem spirit_wolves elemental guardian phoenix ancient_dragon");
									}
									Familiars.dismissAll(p);
									pm.contracts.clear();
									pm.contracts.add(k.id);
									pm.familiarPick = 0;
									pm.familiarExp.put(k.id, (IntegerArgumentType.getInteger(c, "lv") - 1) * 100);
									pm.mp = 9999;
									Familiars.summonAll(p, pm);
									if (IntegerArgumentType.getInteger(c, "ride") == 1) {
										for (var id : pm.activeFamiliars) {
											var e = ((net.minecraft.server.level.ServerLevel) p.level()).getEntity(id);
											if (e != null) {
												Familiars.mount(p, e, k);
											}
										}
									}
									return ok(c, "使い魔 " + k.ja());
								})))))
				.then(Commands.literal("contract").then(Commands.argument("kind", StringArgumentType.word()).executes(c -> {
					var p = c.getSource().getPlayerOrException();
					PlayerMagic pm = PlayerMagic.of(p);
					Familiars.Kind k = Familiars.Kind.byId(StringArgumentType.getString(c, "kind"));
					if (k == null) {
						return ok(c, "kind が違います");
					}
					if (!pm.contracts.contains(k.id)) {
						pm.contracts.add(k.id);
					}
					pm.mp = 9999;
					return ok(c, "契約: " + pm.contracts);
				})))
				.then(Commands.literal("fusiontest").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Fusions.balanceTest(p);
					return ok(c, "融合魔法の威力測定を始めました(ログに出ます)");
				}))
				.then(Commands.literal("guildhall").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					BlockPos at = p.blockPosition().relative(p.getDirection(), 8);
					GuildHall.build((net.minecraft.server.level.ServerLevel) p.level(), at);
					return ok(c, "ギルドの小屋を建てました");
				}))
				.then(Commands.literal("ruin").then(Commands.argument("element", StringArgumentType.word()).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Element e = Element.byId(StringArgumentType.getString(c, "element"));
					if (e == null) {
						return 0;
					}
					net.minecraft.core.BlockPos at = p.blockPosition().offset(0, 0, 14);
					Ruins.build(p.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null, at, e, p.getRandom());
					return ok(c, e.ja + " の遺跡を、前方 14 ブロックに建てました");
				})))
				.then(Commands.literal("act").then(Commands.argument("n", IntegerArgumentType.integer(0, 30)).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					var list = p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40), m -> Enemies.defOf(m) != null);
					if (list.isEmpty()) {
						return ok(c, "近くに MOD の敵がいません");
					}
					var m = list.get(0);
					var abilities = Enemies.defOf(m).abilities();
					int n = IntegerArgumentType.getInteger(c, "n");
					if (n >= abilities.size()) {
						return ok(c, Enemies.defOf(m).name() + " の技は 0〜" + (abilities.size() - 1) + " 番");
					}
					abilities.get(n).act().run(m, (net.minecraft.server.level.ServerLevel) p.level(), p);
					return ok(c, Enemies.defOf(m).name() + " の技 " + n + " を出しました");
				})))
				.then(Commands.literal("cast").executes(c -> {
					Casting.castHeld(c.getSource().getPlayerOrException(), null);
					return 1;
				}))
				.then(Commands.literal("settings").executes(c -> ok(c, Settings.get().summary()))
						.then(Commands.literal("reload").executes(c -> ok(c, "設定を読み直しました: " + Settings.load().summary()))))
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

	/** 属性ごとの遺跡が生えるバイオーム。 */
	private static void registerRuinSpawns() {
		java.util.function.Predicate<net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext> fire = BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_BADLANDS)
				.or(BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.DESERT));
		var wind = BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_HILL);
		var thunder = BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_SAVANNA).or(BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.PLAINS));
		var water = BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_BEACH).or(BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_RIVER));
		var gravity = BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_MOUNTAIN);
		var dark = BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.DARK_FOREST);
		var light = BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.MEADOW, net.minecraft.world.level.biome.Biomes.CHERRY_GROVE,
				net.minecraft.world.level.biome.Biomes.FLOWER_FOREST);
		var curse = BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.SWAMP, net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP);
		var map = java.util.Map.of("fire", fire, "wind", wind, "thunder", thunder, "water", water, "gravity", gravity, "dark", dark, "light", light,
				"curse", curse);
		map.forEach((id, sel) -> BiomeModifications.addFeature(sel, GenerationStep.Decoration.SURFACE_STRUCTURES,
				ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(MOD_ID, "ruin_" + id))));
	}

	private static int ok(CommandContext<CommandSourceStack> c, String s) {
		c.getSource().sendSuccess(() -> Component.literal(s).withStyle(ChatFormatting.AQUA), false);
		return 1;
	}
}
