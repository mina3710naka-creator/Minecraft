package com.magicmod.client;

import com.magicmod.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * 物理武器の振りの動き(一人称の手元と、三人称の腕)。武器の種類 × 連携の段(1〜4)ごとに別のキーフレームを持つ。
 * 段はサーバーが当たるたびに送ってくる(Net.MotionPayload)。次の振りは「前の段の続き」か「1 段目」。
 */
public final class WeaponMotion {

	/** 時刻 t(0〜1)での手の姿勢: 平行移動 x,y,z と回転 x,y,z(度)。 */
	private record Key(float t, float tx, float ty, float tz, float rx, float ry, float rz) {
	}

	private static Key k(float t, float tx, float ty, float tz, float rx, float ry, float rz) {
		return new Key(t, tx, ty, tz, rx, ry, rz);
	}

	// ---- 連携の状態 ----
	private static int lastStep;
	private static long lastTime = -1000;
	private static String lastCat = "";
	private static int swingStep = 1;
	private static boolean wasSwinging;

	public static void onServerStep(int step, String cat) {
		lastStep = step;
		lastCat = cat;
		Minecraft mc = Minecraft.getInstance();
		lastTime = mc.level == null ? 0 : mc.level.getGameTime();
	}

	private static int window(String cat) {
		return "heavy".equals(cat) ? 40 : "swing".equals(cat) ? 30 : 24;
	}

	/** 描画のたびに呼ぶ。振りが始まった瞬間に、この振りの段を決める。 */
	private static int stepFor(String cat, float swing) {
		Minecraft mc = Minecraft.getInstance();
		boolean swinging = swing > 0.001f;
		if (swinging && !wasSwinging) {
			long now = mc.level == null ? 0 : mc.level.getGameTime();
			swingStep = cat.equals(lastCat) && now - lastTime <= window(cat) && lastStep < 4 ? lastStep + 1 : 1;
		}
		wasSwinging = swinging;
		return swingStep;
	}

	public static String category(ItemStack s) {
		String c = ModItems.category(s);
		return c != null && ("blade".equals(c) || "polearm".equals(c) || "dual".equals(c) || "swing".equals(c) || "heavy".equals(c)) ? c : null;
	}

	// ---- キーフレーム(右手基準。左手は x と y・z の回転を反転) ----
	private static Key[] keys(String cat, int step) {
		return switch (cat) {
			case "blade" -> switch (step) {
				// 1 横薙ぎ: 右肩の後ろから左へ水平に振り抜く
				case 1 -> new Key[] { k(0, 0.3f, 0, 0.1f, 10, 70, -20), k(0.25f, 0.4f, 0.05f, 0.1f, 5, 75, -25), k(0.6f, -0.3f, 0, -0.45f, -20, -50, 15),
						k(1, 0, 0, 0, 0, 0, 0) };
				// 2 逆袈裟: 左下から右上へ斬り上げる
				case 2 -> new Key[] { k(0, -0.3f, -0.3f, 0.1f, 40, -30, 55), k(0.25f, -0.35f, -0.35f, 0.1f, 50, -35, 60), k(0.6f, 0.3f, 0.3f, -0.4f, -70, 30, -40),
						k(1, 0, 0, 0, 0, 0, 0) };
				// 3 切り上げ: 低い位置から真上へ(相手を打ち上げる)
				case 3 -> new Key[] { k(0, 0.1f, -0.45f, 0.2f, 70, 0, 0), k(0.25f, 0.1f, -0.5f, 0.2f, 85, 0, 0), k(0.6f, 0.1f, 0.35f, -0.5f, -110, 0, 0),
						k(1, 0, 0, 0, 0, 0, 0) };
				// 4 回転斬り: 大きく溜めて体ごと回りながら振る
				default -> new Key[] { k(0, 0.4f, 0, 0.2f, 0, 100, -30), k(0.3f, 0.5f, -0.05f, 0.25f, 0, 110, -30), k(0.55f, 0f, 0.05f, -0.5f, -30, -80, 20),
						k(0.8f, -0.4f, 0, -0.2f, -10, -230, 10), k(1, 0, 0, 0, 0, 0, 0) };
			};
			case "polearm" -> switch (step) {
				// 1 突き: 引いて、まっすぐ突く
				case 1 -> new Key[] { k(0, 0, 0, 0.35f, 5, 0, 0), k(0.3f, 0, 0, 0.45f, 8, 0, 0), k(0.5f, 0, 0, -0.9f, -5, 0, 0), k(1, 0, 0, 0, 0, 0, 0) };
				// 2 二段突き: 素早く 2 回
				case 2 -> new Key[] { k(0, 0, 0, 0.2f, 0, 0, 0), k(0.2f, 0, 0, -0.8f, -4, 4, 0), k(0.4f, 0, 0, 0.1f, 0, 0, 0), k(0.6f, 0, 0, -1.0f, -6, -4, 0),
						k(1, 0, 0, 0, 0, 0, 0) };
				// 3 薙ぎ払い: 穂先を水平に大きく払う
				case 3 -> new Key[] { k(0, 0.4f, 0, 0.1f, 0, 80, 0), k(0.3f, 0.45f, 0, 0.1f, 0, 85, 0), k(0.65f, -0.4f, 0, -0.5f, -10, -70, 0), k(1, 0, 0, 0, 0, 0, 0) };
				// 4 貫通突進: 腰だめに構えて、体ごと突っ込む
				default -> new Key[] { k(0, 0.2f, -0.1f, 0.6f, 10, 15, 0), k(0.35f, 0.25f, -0.1f, 0.7f, 12, 18, 0), k(0.55f, 0, 0, -1.6f, -8, -3, 0),
						k(0.8f, 0, 0, -1.2f, -4, 0, 0), k(1, 0, 0, 0, 0, 0, 0) };
			};
			case "dual" -> switch (step) {
				case 1 -> new Key[] { k(0, 0.2f, 0, 0, 5, 40, -10), k(0.3f, -0.25f, 0, -0.4f, -15, -35, 10), k(1, 0, 0, 0, 0, 0, 0) };
				case 2 -> new Key[] { k(0, -0.2f, 0, 0, 5, -40, 10), k(0.3f, 0.25f, 0, -0.4f, -15, 35, -10), k(1, 0, 0, 0, 0, 0, 0) };
				// 3 X 字: 右上から左下、すぐ左上から右下
				case 3 -> new Key[] { k(0, 0.3f, 0.2f, 0, 30, 30, -40), k(0.25f, -0.3f, -0.25f, -0.4f, -40, -20, 30), k(0.5f, -0.3f, 0.2f, 0, 30, -30, 40),
						k(0.75f, 0.3f, -0.25f, -0.4f, -40, 20, -30), k(1, 0, 0, 0, 0, 0, 0) };
				// 4 乱舞: 細かく何度も斬る
				default -> new Key[] { k(0, 0.2f, 0, -0.1f, 0, 30, -20), k(0.15f, -0.2f, 0.1f, -0.3f, -20, -30, 20), k(0.3f, 0.2f, -0.1f, -0.1f, 20, 30, -20),
						k(0.45f, -0.2f, 0.1f, -0.3f, -20, -30, 20), k(0.6f, 0.2f, -0.1f, -0.1f, 20, 30, -20), k(0.8f, 0, 0, -0.4f, -30, 0, 0), k(1, 0, 0, 0, 0, 0, 0) };
			};
			case "swing" -> switch (step) {
				// 1 回し打ち: 手首で円を描く
				case 1 -> new Key[] { k(0, 0.3f, 0.1f, 0, 0, 60, 0), k(0.5f, 0, 0.2f, -0.4f, -40, -20, 20), k(1, 0, 0, 0, 0, 0, 0) };
				// 2 大回転: 頭の上で大きく
				case 2 -> new Key[] { k(0, 0.3f, 0.3f, 0, 60, 80, -20), k(0.5f, 0, 0.4f, -0.3f, 80, -120, 20), k(0.75f, -0.3f, 0.2f, -0.3f, 20, -260, 0), k(1, 0, 0, 0, 0, 0, 0) };
				// 3 引き寄せ: 前へ投げて手前に引く
				case 3 -> new Key[] { k(0, 0, 0, 0.3f, 10, 0, 0), k(0.3f, 0, 0.1f, -0.9f, -20, 0, 0), k(0.7f, 0, 0, 0.4f, 25, 0, 0), k(1, 0, 0, 0, 0, 0, 0) };
				// 4 遠心撃: 体ごと 1 回転
				default -> new Key[] { k(0, 0.4f, 0.1f, 0.2f, 0, 120, -20), k(0.3f, 0.4f, 0.1f, 0.2f, 0, 150, -20), k(0.55f, 0, 0.1f, -0.6f, -20, -90, 10),
						k(0.85f, -0.4f, 0, -0.1f, 0, -330, 0), k(1, 0, 0, 0, 0, 0, 0) };
			};
			default -> switch (step) {
				// 大型武器: 高く振りかぶって(ゆっくり)、一気に叩きつける
				case 1 -> new Key[] { k(0, 0.1f, 0.1f, 0.1f, 20, 0, 0), k(0.45f, 0.1f, 0.45f, 0.25f, 115, 0, 0), k(0.65f, 0.1f, 0.05f, -0.7f, -95, 0, 0), k(1, 0, 0, 0, 0, 0, 0) };
				case 2 -> new Key[] { k(0, 0.5f, 0, 0.2f, 0, 90, 0), k(0.5f, 0.55f, 0, 0.25f, 0, 100, 0), k(0.7f, -0.45f, 0, -0.4f, -15, -80, 10), k(1, 0, 0, 0, 0, 0, 0) };
				case 3 -> new Key[] { k(0, 0, 0.1f, 0.2f, 20, 0, 0), k(0.4f, 0, 0.55f, 0.3f, 125, 0, 0), k(0.6f, 0, -0.1f, -1.0f, -100, 0, 0), k(0.8f, 0, -0.1f, -1.0f, -100, 0, 0),
						k(1, 0, 0, 0, 0, 0, 0) };
				default -> new Key[] { k(0, 0.1f, 0.2f, 0.2f, 40, 0, 0), k(0.55f, 0.1f, 0.7f, 0.4f, 150, 0, 0), k(0.72f, 0.1f, -0.2f, -1.2f, -115, 0, 0), k(0.9f, 0.1f, -0.2f, -1.2f, -115, 0, 0),
						k(1, 0, 0, 0, 0, 0, 0) };
			};
		};
	}

	private static float[] sample(Key[] ks, float a) {
		Key prev = ks[0], next = ks[ks.length - 1];
		for (int i = 0; i < ks.length - 1; i++) {
			if (a >= ks[i].t && a <= ks[i + 1].t) {
				prev = ks[i];
				next = ks[i + 1];
				break;
			}
		}
		float span = Math.max(1.0E-4f, next.t - prev.t);
		float u = Mth.clamp((a - prev.t) / span, 0f, 1f);
		u = u * u * (3 - 2 * u);
		return new float[] { Mth.lerp(u, prev.tx, next.tx), Mth.lerp(u, prev.ty, next.ty), Mth.lerp(u, prev.tz, next.tz), Mth.lerp(u, prev.rx, next.rx),
				Mth.lerp(u, prev.ry, next.ry), Mth.lerp(u, prev.rz, next.rz) };
	}

	// ---- 一人称 ----

	/** 一人称の振り。武器の種類があれば true(元の動きを置き換える)。 */
	public static boolean firstPerson(PoseStack ps, int invert, float swing, ItemStack held) {
		String cat = category(held);
		if (cat == null || swing <= 0.001f) {
			return false;
		}
		int step = stepFor(cat, swing);
		float[] p = sample(keys(cat, step), swing);
		// 画面から武器が外れないよう、振れ幅は少し抑える
		ps.translate(invert * p[0] * 0.55f, p[1] * 0.7f, p[2] * 0.8f);
		ps.rotateDegrees(Axis.YP, invert * p[4] * 0.4f);
		ps.rotateDegrees(Axis.ZP, invert * p[5] * 0.5f);
		ps.rotateDegrees(Axis.XP, p[3] * 0.7f);
		// 一人称の持ち手は、武器が手前を向くように少しだけ傾ける
		ps.rotateDegrees(Axis.XP, -25f * Mth.sin(swing * Mth.PI));
		return true;
	}

	/** 一人称・三人称の両方で使う、現在の振りの段(表示用)。 */
	public static int currentStep() {
		return swingStep;
	}

	// ---- 三人称(自分と他のプレイヤーの腕) ----

	/** 腕に足す回転(ラジアン): [x, y, z] と、体のひねり。 */
	public static float[] thirdPerson(ItemStack held, float swing, HumanoidArm arm) {
		String cat = category(held);
		if (cat == null || swing <= 0.001f) {
			return null;
		}
		int step = stepFor(cat, swing);
		float[] p = sample(keys(cat, step), swing);
		float inv = arm == HumanoidArm.RIGHT ? 1f : -1f;
		float x = (float) Math.toRadians(p[3]) * 0.9f + p[2] * 0.9f;
		float y = (float) Math.toRadians(p[4]) * 0.5f * inv;
		float z = (float) Math.toRadians(p[5]) * 0.5f * inv;
		return new float[] { x - 0.4f * Mth.sin(swing * Mth.PI), y, z, (float) Math.toRadians(p[4]) * 0.22f * inv };
	}
}
