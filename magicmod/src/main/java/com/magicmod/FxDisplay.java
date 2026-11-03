package com.magicmod;

import java.util.EnumMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.math.Transformation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * MOD 独自の大きな演出(地面に広がる魔法陣・衝撃波の輪・斬撃の三日月・光の柱・稲妻・球)。
 * 自前の絵を貼った平面をアイテム表示エンティティで出し、補間で回したり広げたりする(クライアント側の追加コード不要)。
 */
public final class FxDisplay {

	public static final String TAG = "mm_fx";

	static final Map<Element, Item> CIRCLE = new EnumMap<>(Element.class);
	static final Map<Element, Item> RING = new EnumMap<>(Element.class);
	static final Map<Element, Item> SLASH = new EnumMap<>(Element.class);
	static final Map<Element, Item> BEAM = new EnumMap<>(Element.class);
	static Item steelSlash, steelRing, bolt, orbDark, orbLight;

	private FxDisplay() {
	}

	/** 演出用のアイテム(クリエイティブタブには出さない)を登録する。 */
	static void registerItems() {
		for (Element e : Element.values()) {
			CIRCLE.put(e, ModItems.item("fx_circle_" + e.id, "演出", "fx", Item::new));
			RING.put(e, ModItems.item("fx_ring_" + e.id, "演出", "fx", Item::new));
			SLASH.put(e, ModItems.item("fx_slash_" + e.id, "演出", "fx", Item::new));
			BEAM.put(e, ModItems.item("fx_beam_" + e.id, "演出", "fx", Item::new));
		}
		steelSlash = ModItems.item("fx_slash_steel", "演出", "fx", Item::new);
		steelRing = ModItems.item("fx_ring_steel", "演出", "fx", Item::new);
		bolt = ModItems.item("fx_bolt", "演出", "fx", Item::new);
		orbDark = ModItems.item("fx_orb_dark", "演出", "fx", Item::new);
		orbLight = ModItems.item("fx_orb_light", "演出", "fx", Item::new);
	}

	private static Transformation tf(Vector3f t, float yawDeg, float tiltDeg, float sx, float sy, float sz) {
		Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(-yawDeg)).rotateZ((float) Math.toRadians(tiltDeg));
		return new Transformation(t, q, new Vector3f(sx, sy, sz), null);
	}

	private static Display.@Nullable ItemDisplay spawn(ServerLevel l, Vec3 pos, Item item, Transformation start, Display.BillboardConstraints bb) {
		Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (d == null) {
			return null;
		}
		d.setPos(pos.x, pos.y, pos.z);
		d.setItemStack(new ItemStack(item));
		d.setItemTransform(ItemDisplayContext.FIXED);
		d.setBrightnessOverride(new Brightness(15, 15));
		d.setViewRange(3f);
		d.setBillboardConstraints(bb);
		d.setTransformation(start);
		d.addTag(TAG);
		l.addFreshEntity(d);
		return d;
	}

	private static void to(Display.ItemDisplay d, int delay, int duration, Transformation t) {
		Tasks.later(delay, () -> {
			if (d.isAlive()) {
				d.setTransformationInterpolationDelay(0);
				d.setTransformationInterpolationDuration(duration);
				d.setTransformation(t);
			}
		});
	}

	private static void end(Display.ItemDisplay d, int at) {
		Tasks.later(at, d::discard);
	}

	/** 地面の魔法陣: 広がって回り、最後に縮んで消える。 */
	public static void circle(ServerLevel l, Vec3 pos, @Nullable Element e, double radius, int life, float spinPerTick) {
		Item it = CIRCLE.get(e == null ? Element.LIGHT : e);
		float s = (float) (radius * 2);
		Display.ItemDisplay d = spawn(l, pos.add(0, 0.06, 0), it, tf(new Vector3f(), 0, 0, 0.05f, 1, 0.05f), Display.BillboardConstraints.FIXED);
		if (d == null) {
			return;
		}
		float yaw = 0;
		to(d, 1, 6, tf(new Vector3f(), yaw += spinPerTick * 6, 0, s, 1, s));
		int t = 7;
		while (t < life - 6) {
			int step = Math.min(10, life - 6 - t);
			to(d, t, step, tf(new Vector3f(), yaw += spinPerTick * step, 0, s, 1, s));
			t += step;
		}
		to(d, Math.max(7, life - 6), 6, tf(new Vector3f(), yaw + spinPerTick * 6, 0, 0.02f, 1, 0.02f));
		end(d, life + 1);
	}

	/** 地面を走る衝撃波の輪。 */
	public static void ring(ServerLevel l, Vec3 pos, @Nullable Element e, double radius, int ticks) {
		Item it = e == null ? steelRing : RING.get(e);
		Display.ItemDisplay d = spawn(l, pos.add(0, 0.08, 0), it, tf(new Vector3f(), 0, 0, 0.3f, 1, 0.3f), Display.BillboardConstraints.FIXED);
		if (d == null) {
			return;
		}
		float s = (float) (radius * 2.1);
		to(d, 1, ticks, tf(new Vector3f(), 30, 0, s, 1, s));
		end(d, ticks + 2);
	}

	/** 斬撃の三日月(術者の向きに、横薙ぎで振り抜く)。tilt で縦斬りにも傾ける。 */
	public static void slash(LivingEntity c, Vec3 dir, @Nullable Element e, double size, float tiltDeg) {
		ServerLevel l = (ServerLevel) c.level();
		Item it = e == null ? steelSlash : SLASH.get(e);
		Vec3 f = new Vec3(dir.x, 0, dir.z);
		if (f.lengthSqr() < 1.0E-4) {
			f = new Vec3(0, 0, 1);
		}
		f = f.normalize();
		float yaw = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
		Vec3 pos = c.position().add(0, c.getBbHeight() * 0.6, 0).add(f.scale(0.4));
		float s = (float) size * 2;
		Display.ItemDisplay d = spawn(l, pos, it, tf(new Vector3f(), yaw - 50, tiltDeg, s * 0.7f, 1, s * 0.7f), Display.BillboardConstraints.FIXED);
		if (d == null) {
			return;
		}
		to(d, 1, 3, tf(new Vector3f(), yaw + 35, tiltDeg, s, 1, s));
		to(d, 4, 2, tf(new Vector3f(), yaw + 55, tiltDeg, s * 1.05f, 1, s * 1.05f));
		end(d, 7);
	}

	/** 光の柱・稲妻(縦の板。プレイヤーの方を向く)。 */
	public static void beam(ServerLevel l, Vec3 base, @Nullable Element e, double width, double height, int life) {
		Item it = e == Element.THUNDER ? bolt : BEAM.get(e == null ? Element.LIGHT : e);
		float w = (float) width, h = (float) height;
		Display.ItemDisplay d = spawn(l, base, it, new Transformation(new Vector3f(0, h / 2, 0), null, new Vector3f(0.05f, h, 1), null),
				Display.BillboardConstraints.VERTICAL);
		if (d == null) {
			return;
		}
		to(d, 1, 3, new Transformation(new Vector3f(0, h / 2, 0), null, new Vector3f(w, h, 1), null));
		to(d, Math.max(4, life - 5), 5, new Transformation(new Vector3f(0, h / 2, 0), null, new Vector3f(0.02f, h, 1), null));
		end(d, life + 1);
	}

	/** 宙に浮かぶ球(特異点・光の玉)。grow tick かけて大きくなる。 */
	public static void orb(ServerLevel l, Vec3 pos, boolean dark, double size, int grow, int life) {
		Display.ItemDisplay d = spawn(l, pos, dark ? orbDark : orbLight, tf(new Vector3f(), 0, 0, 0.1f, 0.1f, 0.1f),
				Display.BillboardConstraints.CENTER);
		if (d == null) {
			return;
		}
		float s = (float) size;
		to(d, 1, Math.max(1, grow), tf(new Vector3f(), 0, 0, s, s, s));
		to(d, Math.max(2, life - 3), 3, tf(new Vector3f(), 0, 0, s * 1.6f, s * 1.6f, s * 1.6f));
		end(d, life + 1);
	}
}
