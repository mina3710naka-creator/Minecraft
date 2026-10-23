package com.lampvideo.client;

import com.lampvideo.display.DisplayBlock;
import com.lampvideo.display.DisplayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;

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
import net.minecraft.world.phys.Vec3;

/** ディスプレイの映像を、ブロックの前面に、1枚の四角として描く(ブロックの中のランプではなく、動的テクスチャ)。 */
public class DisplayRenderer implements BlockEntityRenderer<DisplayBlockEntity, DisplayRenderer.State> {

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.UP;
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

	/** 画面の面の、ブロック中心からの距離(厚さ2/16の板の、外側の面。少し浮かせてチラつきを防ぐ)。 */
	private static final float PLANE = -0.375f + 0.002f;
	/** 枠の太さ。 */
	private static final float MARGIN = 1.0f / 16.0f;

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
		state.facing = be.getBlockState().getValue(DisplayBlock.FACING);
		state.texture = DisplayClient.textureFor(be);
		state.col = be.col;
		state.row = be.row;
		state.cols = be.cols;
		state.rows = be.rows;
		state.left = be.edges[0] > 0;
		state.right = be.edges[1] > 0;
		state.up = be.edges[2] > 0;
		state.down = be.edges[3] > 0;
		state.frameless = be.getBlockState().getBlock() instanceof com.lampvideo.display.LargeDisplayBlock;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.texture == null) {
			return;
		}
		Direction f = state.facing;
		final float nx = f.getStepX();
		final float ny = f.getStepY();
		final float nz = f.getStepZ();
		// 画面の「上」の向き。壁なら真上、床・天井なら北。「右」は、見ている人の右(= (-n) x up)
		final boolean flat = f.getAxis() == Direction.Axis.Y;
		final float ux = 0;
		final float uy = flat ? 0 : 1;
		final float uz = flat ? -1 : 0;
		final float rx = -ny * uz + nz * uy;
		final float ry = -nz * ux + nx * uz;
		final float rz = -nx * uy + ny * ux;
		final float cx = 0.5f + nx * PLANE;
		final float cy = 0.5f + ny * PLANE;
		final float cz = 0.5f + nz * PLANE;
		// このパネルが映す範囲(ブロック内)。枠が残る辺は 1/16 だけ内側、隣とつながる辺はブロックの端まで
		final float m = state.frameless ? 0f : MARGIN;
		final float x0 = state.left ? 0f : m;
		final float x1 = state.right ? 1f : 1f - m;
		final float y0 = state.down ? 0f : m;
		final float y1 = state.up ? 1f : 1f - m;
		final int col = state.col;
		final int row = state.row;
		final int cols = state.cols;
		final int rows = state.rows;
		final float[][] corner = { { x0, y0 }, { x1, y0 }, { x1, y1 }, { x0, y1 } };
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(state.texture), (pose, buffer) -> {
			for (float[] c : corner) {
				float dx = c[0] - 0.5f;
				float dy = c[1] - 0.5f;
				float x = cx + rx * dx + ux * dy;
				float y = cy + ry * dx + uy * dy;
				float z = cz + rz * dx + uz * dy;
				float tu = (col + c[0]) / cols;
				float tv = (row + (1f - c[1])) / rows;
				buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(tu, tv)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
			}
		});
	}
}
