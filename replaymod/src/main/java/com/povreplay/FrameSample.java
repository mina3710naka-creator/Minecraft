package com.povreplay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 描画 1 フレームぶんの、プレイヤー視点の状態。ゲームが描く直前に決まる値(視点の位置と向き、視野角、手の動き、歩くときの揺れなど)を、
 * そのまま記録しておき、リプレイの再生では、同じ値で上書きして、記録したときと同じ視点・手の動きにする。
 */
public final class FrameSample {

	/** 記録の始まりからの時刻(ミリ秒)。 */
	public int time;

	// 視点
	public double camX, camY, camZ;
	public float camYRot, camXRot, fov, hudFov;
	/** 歩くときの画面の揺れ。 */
	public float bob, walkDistBack;

	// 手(一人称)
	public byte attackHand;
	public float viewXRot, viewYRot, xBob, yBob;
	public boolean isScoping;
	public int useItemRemainingTicks;
	public byte handSelection;
	public float mainHandHeight, oldMainHandHeight, offHandHeight, oldOffHandHeight;

	// 体(腕の動きに使われる)
	public float swingAnimation, ticksUsingItem;
	public boolean isUsingItem;
	public byte useItemHand;

	// 画面の効果
	public float portalEffectIntensity, nauseaEffectIntensity, spinningEffectAngle;
	public boolean isUnderWater;
	public double eyePositionY;

	/** 手に持っているスロット(ホットバーの何番目か)。 */
	public byte selectedSlot;

	public byte[] toBytes() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(160);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(time);
			out.writeDouble(camX);
			out.writeDouble(camY);
			out.writeDouble(camZ);
			out.writeFloat(camYRot);
			out.writeFloat(camXRot);
			out.writeFloat(fov);
			out.writeFloat(hudFov);
			out.writeFloat(bob);
			out.writeFloat(walkDistBack);
			out.writeByte(attackHand);
			out.writeFloat(viewXRot);
			out.writeFloat(viewYRot);
			out.writeFloat(xBob);
			out.writeFloat(yBob);
			out.writeBoolean(isScoping);
			out.writeInt(useItemRemainingTicks);
			out.writeByte(handSelection);
			out.writeFloat(mainHandHeight);
			out.writeFloat(oldMainHandHeight);
			out.writeFloat(offHandHeight);
			out.writeFloat(oldOffHandHeight);
			out.writeFloat(swingAnimation);
			out.writeFloat(ticksUsingItem);
			out.writeBoolean(isUsingItem);
			out.writeByte(useItemHand);
			out.writeFloat(portalEffectIntensity);
			out.writeFloat(nauseaEffectIntensity);
			out.writeFloat(spinningEffectAngle);
			out.writeBoolean(isUnderWater);
			out.writeDouble(eyePositionY);
			out.writeByte(selectedSlot);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return bytes.toByteArray();
	}

	public static FrameSample fromBytes(byte[] data) {
		FrameSample s = new FrameSample();
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			s.time = in.readInt();
			s.camX = in.readDouble();
			s.camY = in.readDouble();
			s.camZ = in.readDouble();
			s.camYRot = in.readFloat();
			s.camXRot = in.readFloat();
			s.fov = in.readFloat();
			s.hudFov = in.readFloat();
			s.bob = in.readFloat();
			s.walkDistBack = in.readFloat();
			s.attackHand = in.readByte();
			s.viewXRot = in.readFloat();
			s.viewYRot = in.readFloat();
			s.xBob = in.readFloat();
			s.yBob = in.readFloat();
			s.isScoping = in.readBoolean();
			s.useItemRemainingTicks = in.readInt();
			s.handSelection = in.readByte();
			s.mainHandHeight = in.readFloat();
			s.oldMainHandHeight = in.readFloat();
			s.offHandHeight = in.readFloat();
			s.oldOffHandHeight = in.readFloat();
			s.swingAnimation = in.readFloat();
			s.ticksUsingItem = in.readFloat();
			s.isUsingItem = in.readBoolean();
			s.useItemHand = in.readByte();
			s.portalEffectIntensity = in.readFloat();
			s.nauseaEffectIntensity = in.readFloat();
			s.spinningEffectAngle = in.readFloat();
			s.isUnderWater = in.readBoolean();
			s.eyePositionY = in.readDouble();
			s.selectedSlot = in.readByte();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return s;
	}

	/**
	 * a(時刻が前)と b(時刻が後)の間の、時刻 t の状態。連続する値(位置・向き・揺れなど)は、なめらかにつなぎ、
	 * 切り替わる値(手・スロットなど)は、前の方をそのまま使う。
	 */
	public static FrameSample between(FrameSample a, FrameSample b, int t) {
		if (a == b || b.time <= a.time) {
			return a;
		}
		float f = Math.max(0F, Math.min(1F, (t - a.time) / (float) (b.time - a.time)));
		FrameSample s = copy(a);
		s.time = t;
		s.camX = lerp(a.camX, b.camX, f);
		s.camY = lerp(a.camY, b.camY, f);
		s.camZ = lerp(a.camZ, b.camZ, f);
		s.camYRot = lerpAngle(a.camYRot, b.camYRot, f);
		s.camXRot = lerp(a.camXRot, b.camXRot, f);
		s.fov = lerp(a.fov, b.fov, f);
		s.hudFov = lerp(a.hudFov, b.hudFov, f);
		s.bob = lerp(a.bob, b.bob, f);
		s.walkDistBack = lerp(a.walkDistBack, b.walkDistBack, f);
		s.viewXRot = lerp(a.viewXRot, b.viewXRot, f);
		s.viewYRot = lerpAngle(a.viewYRot, b.viewYRot, f);
		s.xBob = lerp(a.xBob, b.xBob, f);
		s.yBob = lerp(a.yBob, b.yBob, f);
		s.mainHandHeight = lerp(a.mainHandHeight, b.mainHandHeight, f);
		s.oldMainHandHeight = lerp(a.oldMainHandHeight, b.oldMainHandHeight, f);
		s.offHandHeight = lerp(a.offHandHeight, b.offHandHeight, f);
		s.oldOffHandHeight = lerp(a.oldOffHandHeight, b.oldOffHandHeight, f);
		s.swingAnimation = lerp(a.swingAnimation, b.swingAnimation, f);
		s.ticksUsingItem = lerp(a.ticksUsingItem, b.ticksUsingItem, f);
		s.portalEffectIntensity = lerp(a.portalEffectIntensity, b.portalEffectIntensity, f);
		s.nauseaEffectIntensity = lerp(a.nauseaEffectIntensity, b.nauseaEffectIntensity, f);
		s.spinningEffectAngle = lerp(a.spinningEffectAngle, b.spinningEffectAngle, f);
		s.eyePositionY = lerp(a.eyePositionY, b.eyePositionY, f);
		return s;
	}

	private static FrameSample copy(FrameSample a) {
		return fromBytes(a.toBytes());
	}

	private static double lerp(double a, double b, float f) {
		return a + (b - a) * f;
	}

	private static float lerp(float a, float b, float f) {
		return a + (b - a) * f;
	}

	/** 角度(度)を、いちばん近い向きでつなぐ(359 度と 1 度の間は、2 度ぶんだけ回る)。 */
	private static float lerpAngle(float a, float b, float f) {
		float d = ((b - a) % 360F + 540F) % 360F - 180F;
		return a + d * f;
	}
}
