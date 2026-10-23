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
	}

	/** 画面の面の、ブロック中心からの距離(厚さ2/16の板の、外側の面。少し浮かせてチラつきを防ぐ)。 */
	private static final float PLANE = -0.375f + 0.002f;
	/** 画面の半分の大きさ(枠の内側 14/16 の半分)。 */
	private static final float HALF = 7.0f / 16.0f;

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
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.texture == null) {
			return;
		}
		Direction f = state.facing;
		float nx = f.getStepX();
		float ny = f.getStepY();
		float nz = f.getStepZ();
		// 画面の「上」の向き。壁なら真上、床・天井なら北。「右」は、見ている人の右(= (-n) x up)
		float ux = 0;
		float uy = 1;
		float uz = 0;
		if (f.getAxis() == Direction.Axis.Y) {
			uy = 0;
			uz = -1;
		}
		float rx = -ny * uz + nz * uy;
		float ry = -nz * ux + nx * uz;
		float rz = -nx * uy + ny * ux;
		float cx = 0.5f + nx * PLANE;
		float cy = 0.5f + ny * PLANE;
		float cz = 0.5f + nz * PLANE;
		final float[][] corner = { { -1, -1, 0, 1 }, { 1, -1, 1, 1 }, { 1, 1, 1, 0 }, { -1, 1, 0, 0 } };
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(state.texture), (pose, buffer) -> {
			for (float[] c : corner) {
				float x = cx + (rx * c[0] + ux * c[1]) * HALF;
				float y = cy + (ry * c[0] + uy * c[1]) * HALF;
				float z = cz + (rz * c[0] + uz * c[1]) * HALF;
				buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(c[2], c[3])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
			}
		});
	}
}
