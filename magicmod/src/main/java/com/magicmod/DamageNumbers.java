package com.magicmod;

import org.joml.Vector3f;

import com.mojang.math.Transformation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;

/** 浮かび上がって消えるダメージ数値・回復量(テキスト表示エンティティ)。 */
public final class DamageNumbers {

	public static final String TAG = "mm_number";

	private DamageNumbers() {
	}

	public static void damage(LivingEntity t, float amount, int rgb, boolean weak, boolean resist) {
		if (amount < 0.05f) {
			return;
		}
		String n = amount >= 10 ? String.valueOf(Math.round(amount)) : String.format("%.1f", amount);
		MutableComponent c = Component.literal(n).withStyle(s -> s.withColor(rgb).withBold(weak));
		if (weak) {
			c = Component.literal("弱点! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(c);
		} else if (resist) {
			c = c.append(Component.literal(" 耐性").withStyle(ChatFormatting.GRAY));
		}
		spawn(t, c, weak ? 1.25f : amount >= 20 ? 1.1f : 0.85f);
	}

	public static void heal(LivingEntity t, float amount) {
		if (amount < 0.05f) {
			return;
		}
		spawn(t, Component.literal("+" + (amount >= 10 ? String.valueOf(Math.round(amount)) : String.format("%.1f", amount)))
				.withStyle(ChatFormatting.GREEN), 0.8f);
	}

	public static void text(LivingEntity t, Component c, float scale) {
		spawn(t, c, scale);
	}

	private static void spawn(LivingEntity t, Component text, float scale) {
		if (!(t.level() instanceof ServerLevel l)) {
			return;
		}
		Display.TextDisplay d = EntityTypes.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (d == null) {
			return;
		}
		var r = l.getRandom();
		d.setPos(t.getX() + (r.nextFloat() - 0.5) * t.getBbWidth(), t.getY() + t.getBbHeight() + 0.2, t.getZ() + (r.nextFloat() - 0.5) * t.getBbWidth());
		d.setText(text);
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setBackgroundColor(0);
		d.setFlags((byte) (Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH));
		d.setViewRange(0.6f);
		d.setTransformation(new Transformation(new Vector3f(), null, new Vector3f(scale * 1.4f), null));
		d.addTag(TAG);
		FxDisplay.FRESH.add(d.getUUID());
		l.addFreshEntity(d);
		// 少し大きく出て、上へ浮きながら縮んで消える
		Tasks.later(2, () -> {
			if (d.isAlive()) {
				d.setTransformationInterpolationDelay(0);
				d.setTransformationInterpolationDuration(16);
				d.setTransformation(new Transformation(new Vector3f(0, 0.9f, 0), null, new Vector3f(scale * 0.7f), null));
			}
		});
		Tasks.later(20, d::discard);
	}
}
