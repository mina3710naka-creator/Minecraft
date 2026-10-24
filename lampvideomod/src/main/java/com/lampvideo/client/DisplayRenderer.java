package com.lampvideo.client;

import com.lampvideo.display.DisplayBlockEntity;
import com.lampvideo.display.DisplayGeometry;
import com.lampvideo.display.StandDisplayBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * ディスプレイの枠と映像を描く(ブロックの中のランプではなく、動的テクスチャ)。
 * パネル(壁・天井・床)は、モデルの板の上に、枠(隣とつながる辺は枠なし)と映像を描く。
 * スタンドは、台・支柱・画面を全部ここで描くので、斜めの向きにも置ける。
 */
public class DisplayRenderer implements BlockEntityRenderer<DisplayBlockEntity, DisplayRenderer.State> {

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.UP;
		Direction spin = Direction.NORTH;
		/** スタンドの向き(0〜7)。スタンドでなければ -1。 */
		int standRot = -1;
		Identifier texture;
		int col;
		int row;
		int cols = 1;
		int rows = 1;
		/** 隣に続くパネルがあるか: 左・右・上・下。 */
		boolean left;
		boolean right;
		boolean up;
		boolean down;
		/** 枠なし(大型ディスプレイ)か。 */
		boolean frameless;
	}

	private static final Identifier FRAME = Identifier.fromNamespaceAndPath("lampvideo", "textures/block/display_frame.png");
	private static final Identifier SCREEN_OFF = Identifier.fromNamespaceAndPath("lampvideo", "textures/block/display_screen_off.png");

	/** 画面の面の、ブロック中心からの距離(厚さ2/16の板の、外側の面)。 */
	private static final float PLANE = -0.375f;
	/** 左右の枠の太さ。 */
	private static final float MX = 1.0f / 16.0f;
	/** 上下の枠の太さ(単独のとき、画面がパソコンのモニターの比率 16:9 になる太さ)。 */
	private static final float MY = (1.0f - (1.0f - 2 * MX) * 9.0f / 16.0f) / 2.0f;
	private static final float STAND_BODY_BOTTOM = 6.0f / 16.0f;
	/** 斜めのスタンドは、ブロックからはみ出さないよう小さくする。 */
	private static final float DIAGONAL_SCALE = 0.72f;

	public DisplayRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(DisplayBlockEntity be, State state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(be, state, crumbling);
		BlockState bs = be.getBlockState();
		Direction f = DisplayGeometry.facing(bs);
		state.facing = f == null ? Direction.NORTH : f;
		state.spin = DisplayGeometry.spin(bs);
		state.standRot = bs.getBlock() instanceof StandDisplayBlock ? bs.getValue(StandDisplayBlock.ROT) : -1;
		state.texture = DisplayClient.textureFor(be);
		state.col = be.col;
		state.row = be.row;
		state.cols = be.cols;
		state.rows = be.rows;
		state.left = be.edges[0] > 0;
		state.right = be.edges[1] > 0;
		state.up = be.edges[2] > 0;
		state.down = be.edges[3] > 0;
		state.frameless = DisplayGeometry.frameless(bs);
	}

	/** ブロック内の座標(x: 右、y: 上、z: 手前)を、ワールド座標に直す。 */
	private static final class Frame {
		final float ox, oy, oz;
		final float rx, ry, rz;
		final float ux, uy, uz;
		final float nx, ny, nz;
		final float yOrigin;
		final float scale;

		Frame(float ox, float oy, float oz, float[] r, float[] u, float[] n, float yOrigin, float scale) {
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			this.rx = r[0];
			this.ry = r[1];
			this.rz = r[2];
			this.ux = u[0];
			this.uy = u[1];
			this.uz = u[2];
			this.nx = n[0];
			this.ny = n[1];
			this.nz = n[2];
			this.yOrigin = yOrigin;
			this.scale = scale;
		}

		float[] at(float x, float y, float z) {
			float a = (x - 0.5f) * scale;
			float b = (y - yOrigin) * scale;
			float c = z * scale;
			return new float[] { ox + rx * a + ux * b + nx * c, oy + ry * a + uy * b + ny * c, oz + rz * a + uz * b + nz * c };
		}
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, float[] a, float[] b, float[] c, float[] d,
			float[] uv, float nx, float ny, float nz, int light) {
		float[][] p = { a, b, c, d };
		for (int i = 0; i < 4; i++) {
			vc.addVertex(pose, p[i][0], p[i][1], p[i][2]).setColor(0xFFFFFFFF).setUv(uv[i * 2], uv[i * 2 + 1])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		}
	}

	/** 面(手前から見た四角)を、ブロック内の座標 [xa,xb] x [ya,yb] で描く。uv は、その範囲の割合をそのまま使う。 */
	private static void rect(VertexConsumer vc, PoseStack.Pose pose, Frame f, float xa, float xb, float ya, float yb,
			float z, float[] uv, int light) {
		quad(vc, pose, f.at(xa, ya, z), f.at(xb, ya, z), f.at(xb, yb, z), f.at(xa, yb, z), uv, f.nx, f.ny, f.nz, light);
	}

	/** 直方体(スタンドの台・支柱・背板)を描く。手前が +z。 */
	private static void box(VertexConsumer vc, PoseStack.Pose pose, Frame f, float x0, float y0, float z0, float x1,
			float y1, float z1, int light) {
		float[] uvAll = { 0, 1, 1, 1, 1, 0, 0, 0 };
		// 手前・奥
		quad(vc, pose, f.at(x0, y0, z1), f.at(x1, y0, z1), f.at(x1, y1, z1), f.at(x0, y1, z1), uvAll, f.nx, f.ny, f.nz, light);
		quad(vc, pose, f.at(x1, y0, z0), f.at(x0, y0, z0), f.at(x0, y1, z0), f.at(x1, y1, z0), uvAll, -f.nx, -f.ny, -f.nz, light);
		// 左・右
		quad(vc, pose, f.at(x0, y0, z0), f.at(x0, y0, z1), f.at(x0, y1, z1), f.at(x0, y1, z0), uvAll, -f.rx, -f.ry, -f.rz, light);
		quad(vc, pose, f.at(x1, y0, z1), f.at(x1, y0, z0), f.at(x1, y1, z0), f.at(x1, y1, z1), uvAll, f.rx, f.ry, f.rz, light);
		// 上・下
		quad(vc, pose, f.at(x0, y1, z1), f.at(x1, y1, z1), f.at(x1, y1, z0), f.at(x0, y1, z0), uvAll, f.ux, f.uy, f.uz, light);
		quad(vc, pose, f.at(x0, y0, z0), f.at(x1, y0, z0), f.at(x1, y0, z1), f.at(x0, y0, z1), uvAll, -f.ux, -f.uy, -f.uz, light);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		final boolean stand = state.standRot >= 0;
		final Frame frame;
		if (stand) {
			double th = Math.toRadians(state.standRot * 45.0);
			float nx = (float) -Math.sin(th);
			float nz = (float) Math.cos(th);
			boolean diagonal = (state.standRot & 1) == 1;
			frame = new Frame(0.5f, 0f, 0.5f, new float[] { nz, 0, -nx }, new float[] { 0, 1, 0 }, new float[] { nx, 0, nz },
					0f, diagonal ? DIAGONAL_SCALE : 1f);
		} else {
			Direction f = state.facing;
			Direction u = DisplayGeometry.up(f, state.spin);
			Direction r = DisplayGeometry.right(f, state.spin);
			frame = new Frame(0.5f, 0.5f, 0.5f, new float[] { r.getStepX(), r.getStepY(), r.getStepZ() },
					new float[] { u.getStepX(), u.getStepY(), u.getStepZ() }, new float[] { f.getStepX(), f.getStepY(), f.getStepZ() },
					0.5f, 1f);
		}

		// 画面と枠の範囲(ブロック内の座標)。隣とつながる辺は、ブロックの端まで
		final boolean framed = !state.frameless;
		final float x0 = !framed || state.left ? 0f : MX;
		final float x1 = !framed || state.right ? 1f : 1f - MX;
		final float bodyBottom = stand ? STAND_BODY_BOTTOM : 0f;
		final float y0;
		final float y1;
		if (stand) {
			y0 = 7f / 16f;
			y1 = state.up ? 1f : 15f / 16f;
		} else {
			y0 = !framed || state.down ? 0f : MY;
			y1 = !framed || state.up ? 1f : 1f - MY;
		}
		// 単独の枠ありパネル・スタンドは、画面の四角にテクスチャ全体を貼る。それ以外は、ブロック全体の座標でつなぐ
		final boolean single = state.cols == 1 && state.rows == 1 && framed;
		final float mx0 = single ? x0 : 0f;
		final float mx1 = single ? x1 : 1f;
		final float my0 = single ? y0 : 0f;
		final float my1 = single ? y1 : 1f;
		final int col = state.col;
		final int row = state.row;
		final int cols = state.cols;
		final int rows = state.rows;
		final int light = state.lightCoords;
		final float z = PLANE;

		// 枠(と、スタンドの台・支柱・背板)
		if (framed || stand) {
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(FRAME), (pose, vc) -> {
				if (stand) {
					box(vc, pose, frame, 0.0f, 0.0f, -0.5f, 1.0f, 1.0f / 16f, 0.25f, light);
					box(vc, pose, frame, 0.4375f, 1.0f / 16f, -0.5f, 0.5625f, STAND_BODY_BOTTOM, -0.25f, light);
					box(vc, pose, frame, 0.0f, bodyBottom, -0.5f, 1.0f, 1.0f, z - 0.002f, light);
				}
				float[] uv = { 0, 1, 1, 1, 1, 0, 0, 0 };
				float e = z + 0.001f;
				if (stand || !state.down) {
					rect(vc, pose, frame, 0f, 1f, bodyBottom, y0, e, uv, light);
				}
				if (!state.up) {
					rect(vc, pose, frame, 0f, 1f, y1, 1f, e, uv, light);
				}
				if (!state.left) {
					rect(vc, pose, frame, 0f, x0, y0, y1, e, uv, light);
				}
				if (!state.right) {
					rect(vc, pose, frame, x1, 1f, y0, y1, e, uv, light);
				}
			});
		}

		// 映像(まだ映像が来ていなければ、消灯の画面)
		final boolean playing = state.texture != null;
		final Identifier tex = playing ? state.texture : SCREEN_OFF;
		final float eu = playing ? 0.5f / (cols * 128f) : 0f;
		final float ev = playing ? 0.5f / (rows * 128f) : 0f;
		final var type = playing ? RenderTypes.entityTranslucentEmissive(tex) : RenderTypes.entityCutout(tex);
		collector.submitCustomGeometry(poseStack, type, (pose, vc) -> {
			float[][] c = { { x0, y0 }, { x1, y0 }, { x1, y1 }, { x0, y1 } };
			float[][] pts = new float[4][];
			float[] uv = new float[8];
			for (int i = 0; i < 4; i++) {
				pts[i] = frame.at(c[i][0], c[i][1], z + 0.002f);
				float tu = (col + (c[i][0] - mx0) / (mx1 - mx0)) / cols;
				float tv = (row + (1f - (c[i][1] - my0) / (my1 - my0))) / rows;
				uv[i * 2] = Math.max(eu, Math.min(1f - eu, tu));
				uv[i * 2 + 1] = Math.max(ev, Math.min(1f - ev, tv));
			}
			quad(vc, pose, pts[0], pts[1], pts[2], pts[3], uv, frame.nx, frame.ny, frame.nz, playing ? 0xF000F0 : light);
		});
	}
}
