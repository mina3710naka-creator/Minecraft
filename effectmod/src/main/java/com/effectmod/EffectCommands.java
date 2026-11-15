package com.effectmod;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** /effectmod コマンド(OP向け)。付与台を使わずに、手に持ったアイテムへ付ける・確認する。 */
final class EffectCommands {

	private EffectCommands() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("effectmod")
				.then(Commands.literal("list").executes(c -> {
					ItemStack st = c.getSource().getPlayerOrException().getMainHandItem();
					List<Entry> es = Runner.entries(st);
					if (es.isEmpty()) {
						return msg(c, "手に持ったアイテムに効果は付いていません", ChatFormatting.GRAY);
					}
					for (Entry e : es) {
						msg(c, "・" + Parser.label(e) + "  「" + e.text() + "」", ChatFormatting.LIGHT_PURPLE);
					}
					return es.size();
				}))
				.then(Commands.literal("words").executes(c -> {
					StringBuilder sb = new StringBuilder();
					for (Parser.Action a : Parser.Action.values()) {
						sb.append(a.label);
						if (!a.cats.isEmpty()) {
							sb.append("(").append(a.cats.iterator().next().label).append("専用)");
						}
						sb.append(" ");
					}
					msg(c, "効果の一覧: " + sb, ChatFormatting.AQUA);
					StringBuilder tr = new StringBuilder();
					for (Parser.Trigger t : Parser.Trigger.values()) {
						tr.append(t.label).append(" ");
					}
					return msg(c, "きっかけ: " + tr, ChatFormatting.AQUA);
				}))
				.then(Commands.literal("add").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							ItemStack st = p.getMainHandItem();
							if (st.isEmpty()) {
								return msg(c, "付けたいアイテムを手に持ってください", ChatFormatting.RED);
							}
							Parser.Result r = Parser.parse(StringArgumentType.getString(c, "text"), st);
							if (!r.ok()) {
								return msg(c, r.error(), ChatFormatting.RED);
							}
							List<Entry> cur = new ArrayList<>(Runner.entries(st));
							if (cur.size() >= 8) {
								return msg(c, "付けられる効果は8個までです", ChatFormatting.RED);
							}
							cur.add(r.entry());
							st.set(ModContent.EFFECTS, List.copyOf(cur));
							st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
							return msg(c, "付与しました(無料): " + Parser.label(r.entry()), ChatFormatting.GREEN);
						})))
				.then(Commands.literal("clear").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
					ItemStack st = c.getSource().getPlayerOrException().getMainHandItem();
					st.remove(ModContent.EFFECTS);
					st.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
					return msg(c, "効果をすべて消しました", ChatFormatting.GREEN);
				}))));
	}

	private static int msg(CommandContext<CommandSourceStack> c, String s, ChatFormatting color) {
		c.getSource().sendSuccess(() -> Component.literal(s).withStyle(color), false);
		return 1;
	}
}
