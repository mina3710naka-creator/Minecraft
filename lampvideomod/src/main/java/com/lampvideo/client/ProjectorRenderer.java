package com.lampvideo.client;

import com.lampvideo.display.DisplayBlockEntity;
import com.lampvideo.display.ProjectorBlock;
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
import net.minecraft.world.phys.Vec3;

/**
 * プロジェクターの映像: 向いている方向の壁に、16:9 の映像を貼り、レンズから映像の四隅へ、うっすらと光の筋を描く。
 * 映像は、プロジェクターのブロックの外(壁)に出るので、画面の外に出ていても描くようにしている。
 */
public class ProjectorRenderer implements BlockEntityRenderer<DisplayBlockEntity, ProjectorRenderer.State> {

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		Identifier texture;
		/** {壁の中心の x,y,z(ワールド)、壁の法線の x,z、映像の幅}。映る場所がなければ null。 */
		double[] proj;
		int blockX;
		int blockY;
		int blockZ;
	}

	private static final Identifier BEAM = Identifier.fromNamespaceAndPath("lampvideo", "textures/block/projector_beam.png");

	public ProjectorRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public void extractRenderState(DisplayBlockEntity be, State state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(be, state, crumbling);
		state.facing = be.getBlockState().getValue(ProjectorBlock.FACING);
		state.texture = DisplayClient.textureFor(be);
		state.proj = be.proj;
		state.blockX = be.getBlockPos().getX();
		state.blockY = be.getBlockPos().getY();
		state.blockZ = be.getBlockPos().getZ();
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color,
			float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
				.setNormal(pose, nx, ny, nz);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.texture == null || state.proj == null) {
			return;
		}
		final double[] p = state.proj;
		final float nx = (float) p[3];
		final float nz = (float) p[4];
		final float width = (float) p[5];
		final float height = width * 9f / 16f;
		// 壁の上の、右向き(映像を見る側から見て右)= 光の向き x 上。光は、法線の反対向き
		final float rx = nz;   // (-n) x up = (-nx,0,-nz) x (0,1,0) = (nz, 0, -nx)... のうち、光の進む向き f = -n として f x up
		final float rz = -nx;
		// 壁の中心(ブロック基準の座標)
		final float cx = (float) (p[0] - state.blockX);
		final float cy = (float) (p[1] - state.blockY);
		final float cz = (float) (p[2] - state.blockZ);
		final float hw = width / 2f;
		final float hh = height / 2f;
		final float[][] corner = {
				{ cx - rx * hw, cy - hh, cz - rz * hw }, { cx + rx * hw, cy - hh, cz + rz * hw },
				{ cx + rx * hw, cy + hh, cz + rz * hw }, { cx - rx * hw, cy + hh, cz - rz * hw } };
		final float[][] uv = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 0, 0 } };
		final Direction f = state.facing;
		final float lx = 0.5f + f.getStepX() * 0.46f;
		final float ly = 0.28f;
		final float lz = 0.5f + f.getStepZ() * 0.46f;

		// 光の筋(うっすら)
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(BEAM), (pose, vc) -> {
			final int color = 0x12CFE8FF;
			for (int i = 0; i < 4; i++) {
				float[] a = corner[i];
				float[] b = corner[(i + 1) % 4];
				vertex(vc, pose, lx, ly, lz, 0f, 0f, color, 0f, 1f, 0f);
				vertex(vc, pose, lx, ly, lz, 1f, 0f, color, 0f, 1f, 0f);
				vertex(vc, pose, b[0], b[1], b[2], 1f, 1f, color, 0f, 1f, 0f);
				vertex(vc, pose, a[0], a[1], a[2], 0f, 1f, color, 0f, 1f, 0f);
			}
		});
		// 壁に映った映像
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(state.texture), (pose, vc) -> {
			final int color = 0xEBFFFFFF;
			for (int i = 0; i < 4; i++) {
				vertex(vc, pose, corner[i][0], corner[i][1], corner[i][2], uv[i][0], uv[i][1], color, nx, 0f, nz);
			}
		});
	}
}
