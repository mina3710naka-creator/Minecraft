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
		/** 床・天井のパネルの向き(UP / DOWN)と、画面の上の向き。水平なら null。 */
		Direction vertical;
		Direction spin = Direction.NORTH;
		/** 水平な向きの yaw(0〜7。奇数は斜め)。床・天井なら -1。 */
		int yaw = -1;
		boolean stand;
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
		/** グループにスタンドがあるか(画面の上下の空間を、スタンドにそろえる)。 */
		boolean hasStand;
		int texW;
		int texH;
	}

	private static final Identifier FRAME = Identifier.fromNamespaceAndPath("lampvideo", "textures/block/display_frame.png");
	private static final Identifier SCREEN_OFF = Identifier.fromNamespaceAndPath("lampvideo", "textures/block/display_screen_off.png");

	/** 画面の面の、ブロック中心からの距離(厚さ2/16の板の、外側の面)。 */
	private static final float PLANE = -0.375f;

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
		state.vertical = DisplayGeometry.verticalFacing(bs);
		state.spin = DisplayGeometry.spin(bs);
		state.yaw = DisplayGeometry.yaw(bs);
		state.stand = bs.getBlock() instanceof StandDisplayBlock;
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
		state.hasStand = be.hasStand;
		state.texW = be.texW;
		state.texH = be.texH;
	}

	/** ブロック内の座標(x: 右、y: 上、z: 手前)を、ワールド座標に直す。 */
	private static final class Frame {
		final float ox, oy, oz;
		final float rx, ry, rz;
		final float ux, uy, uz;
		final float nx, ny, nz;
		final float yOrigin;
		final float scale;
		/** 右の向きにだけ掛ける倍率(斜めの画面は √2。ブロックの対角線いっぱいに広げて、斜めの隣とつなぐ)。 */
		final float rScale;
		/** 奥行き(手前の向き)のずらし。斜めの画面は、背板がブロックの外に出ないよう、中心に寄せる。 */
		final float zShift;

		Frame(float ox, float oy, float oz, float[] r, float[] u, float[] n, float yOrigin, float scale, float rScale, float zShift) {
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
			this.rScale = rScale;
			this.zShift = zShift;
			this.rScale = rScale;
			this.zShift = zShift;
		}

		float[] at(float x, float y, float z) {
			float a = (x - 0.5f) * scale * rScale;
			float b = (y - yOrigin) * scale;
			float c = (z + zShift) * scale;
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
		final boolean stand = state.stand;
		final boolean diag = state.yaw >= 0 && (state.yaw & 1) == 1;
		final Frame frame;
		if (state.yaw >= 0) {
			// 水平な向き(壁・スタンド)。斜め(奇数)は、幅を √2 倍にして、背板を中心に寄せる
			double th = Math.toRadians(state.yaw * 45.0);
			float nx = (float) -Math.sin(th);
			float nz = (float) Math.cos(th);
			boolean diagonal = (state.yaw & 1) == 1;
			float[] r = { nz, 0, -nx };
			float[] u = { 0, 1, 0 };
			float[] n = { nx, 0, nz };
			float rScale = diagonal ? (float) Math.sqrt(2.0) : 1f;
			float zShift = diagonal ? 0.4375f : 0f;
			frame = stand ? new Frame(0.5f, 0f, 0.5f, r, u, n, 0f, 1f, rScale, zShift)
					: new Frame(0.5f, 0.5f, 0.5f, r, u, n, 0.5f, 1f, rScale, zShift);
		} else {
			Direction f = state.vertical == null ? Direction.UP : state.vertical;
			Direction u = DisplayGeometry.up(f, state.spin);
			Direction r = DisplayGeometry.right(f, state.spin);
			frame = new Frame(0.5f, 0.5f, 0.5f, new float[] { r.getStepX(), r.getStepY(), r.getStepZ() },
					new float[] { u.getStepX(), u.getStepY(), u.getStepZ() }, new float[] { f.getStepX(), f.getStepY(), f.getStepZ() },
					0.5f, 1f, 1f, 0f);
		}

		// 画面と枠の範囲(ブロック内の座標)。隣とつながる辺は、ブロックの端まで。
		// 上下は、枠を太くするのではなく、空間を開ける(スタンドを含むグループは、スタンドの高さにそろえる)。
		final boolean framed = !state.frameless;
		final float m = DisplayGeometry.margin(state.frameless);
		final float bi = DisplayGeometry.bottomInset(state.frameless, stand || state.hasStand);
		final float ti = DisplayGeometry.topInset(state.frameless, stand || state.hasStand);
		final float plateLo = state.down ? 0f : bi;
		final float plateHi = state.up ? 1f : 1f - ti;
		final float x0 = state.left ? 0f : m;
		final float x1 = state.right ? 1f : 1f - m;
		final float y0 = state.down ? 0f : bi + m;
		final float y1 = state.up ? 1f : 1f - ti - m;
		// 映像は、グループ全体の「画面の見える範囲」に、テクスチャ全体を貼る(そうすると、映像の全体が収まる)
		final float[] vis = DisplayGeometry.visibleSize(state.cols, state.rows, state.frameless, stand || state.hasStand);
		final int col = state.col;
		final int row = state.row;
		final int light = state.lightCoords;
		final float z = PLANE;

		// 枠・背板(パネルは枠ありのとき)・スタンドの台と支柱
		if (framed || stand) {
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(FRAME), (pose, vc) -> {
				if (stand) {
					if (diag) {
						// 斜めのスタンドは、台がブロックの外に出ないよう、小さめ
						box(vc, pose, frame, 0.2f, 0.0f, -0.5f, 0.8f, 1.0f / 16f, -0.1f, light);
						box(vc, pose, frame, 0.4375f, 1.0f / 16f, -0.5f, 0.5625f, plateLo, -0.2f, light);
					} else {
						box(vc, pose, frame, 0.0f, 0.0f, -0.5f, 1.0f, 1.0f / 16f, 0.25f, light);
						box(vc, pose, frame, 0.4375f, 1.0f / 16f, -0.5f, 0.5625f, plateLo, -0.25f, light);
					}
				}
				// 背板(厚さ 1/8)
				box(vc, pose, frame, 0.0f, plateLo, -0.5f, 1.0f, plateHi, z - 0.002f, light);
				// 枠は、無地の金属。テクスチャの中央の、模様のない所だけを使う
				float[] uv = { 0.25f, 0.75f, 0.75f, 0.75f, 0.75f, 0.25f, 0.25f, 0.25f };
				float e = z + 0.001f;
				if (!state.down) {
					rect(vc, pose, frame, 0f, 1f, plateLo, y0, e, uv, light);
				}
				if (!state.up) {
					rect(vc, pose, frame, 0f, 1f, y1, plateHi, e, uv, light);
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
		// 端の画素が、反対側の端の色と混ざらないように、半画素だけ内側から読む
		final float eu = playing ? 0.5f / Math.max(1, state.texW) : 0f;
		final float ev = playing ? 0.5f / Math.max(1, state.texH) : 0f;
		final var type = playing ? RenderTypes.entityTranslucentEmissive(tex) : RenderTypes.entityCutout(tex);
		collector.submitCustomGeometry(poseStack, type, (pose, vc) -> {
			float[][] c = { { x0, y0 }, { x1, y0 }, { x1, y1 }, { x0, y1 } };
			float[][] pts = new float[4][];
			float[] uv = new float[8];
			for (int i = 0; i < 4; i++) {
				pts[i] = frame.at(c[i][0], c[i][1], z + 0.002f);
				float tu = (col + c[i][0] - m) / vis[0];
				float tv = (row + (1f - c[i][1]) - (ti + m)) / vis[1];
				uv[i * 2] = Math.max(eu, Math.min(1f - eu, tu));
				uv[i * 2 + 1] = Math.max(ev, Math.min(1f - ev, tv));
			}
			quad(vc, pose, pts[0], pts[1], pts[2], pts[3], uv, frame.nx, frame.ny, frame.nz, playing ? 0xF000F0 : light);
		});
	}
}
