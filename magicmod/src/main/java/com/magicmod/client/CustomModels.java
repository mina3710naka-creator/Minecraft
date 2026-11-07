package com.magicmod.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.magicmod.MagicMod;
import com.magicmod.ModEntities;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * 敵・使い魔の独自 3D モデル。形は tools/gen_models.py が作る JSON(部品・箱・動き)で、ここで読み込んで組み立てる。
 * 動きは JSON の anim 名(head / legA / legB / armA / armB / wingL / wingR / tail / bob / spin / pulse)で決まる。
 */
public final class CustomModels {

	private static final Map<String, JsonObject> SPECS = new HashMap<>();

	private CustomModels() {
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, path);
	}

	static ModelLayerLocation layer(String id) {
		return new ModelLayerLocation(id(id), "main");
	}

	private static JsonObject spec(String id) {
		return SPECS.computeIfAbsent(id, k -> {
			try (InputStream in = CustomModels.class.getResourceAsStream("/assets/magicmod/models/custom/" + k + ".json")) {
				if (in == null) {
					return null;
				}
				return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				MagicMod.LOGGER.error("独自モデルを読めません: " + k, e);
				return null;
			}
		});
	}

	private static LayerDefinition build(JsonObject sp) {
		MeshDefinition mesh = new MeshDefinition();
		Map<String, PartDefinition> defs = new HashMap<>();
		Map<String, List<JsonObject>> cubes = new HashMap<>();
		for (JsonElement e : sp.getAsJsonArray("cubes")) {
			JsonObject c = e.getAsJsonObject();
			cubes.computeIfAbsent(c.get("part").getAsString(), k -> new ArrayList<>()).add(c);
		}
		for (JsonElement e : sp.getAsJsonArray("parts")) {
			JsonObject p = e.getAsJsonObject();
			String name = p.get("name").getAsString();
			CubeListBuilder b = CubeListBuilder.create();
			for (JsonObject c : cubes.getOrDefault(name, List.of())) {
				JsonArray uv = c.getAsJsonArray("uv"), bx = c.getAsJsonArray("box");
				b.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).addBox(bx.get(0).getAsFloat(), bx.get(1).getAsFloat(), bx.get(2).getAsFloat(),
						bx.get(3).getAsFloat(), bx.get(4).getAsFloat(), bx.get(5).getAsFloat(), new CubeDeformation(c.get("infl").getAsFloat()));
			}
			JsonArray pv = p.getAsJsonArray("pivot");
			PartPose pose = PartPose.offset(pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat());
			PartDefinition parent = p.get("parent").isJsonNull() ? mesh.getRoot() : defs.get(p.get("parent").getAsString());
			defs.put(name, parent.addOrReplaceChild(name, b, pose));
		}
		int t = sp.get("tex").getAsInt();
		return LayerDefinition.create(mesh, t, t);
	}

	/** 登録した敵・使い魔のうち、独自モデルがあるものすべてに描画を割り当てる。 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void registerAll() {
		for (Map.Entry<String, EntityType<?>> en : ModEntities.TYPES.entrySet()) {
			String id = en.getKey();
			JsonObject sp = spec(id);
			if (sp == null) {
				continue;
			}
			ModelLayerRegistry.registerModelLayer(layer(id), () -> build(sp));
			EntityRendererProvider provider = ctx -> new Renderer(ctx, id, sp);
			EntityRendererRegistry.register((EntityType) en.getValue(), provider);
		}
	}

	// ---------------------------------------------------------------------------------------------

	private record Anim(ModelPart part, String name, String kind, float amp, float phase, float rest, float spd) {
	}

	private record StagePart(ModelPart part, int lo, int hi) {
	}

	/** 描画の状態: 使い魔の進化段階(1〜4)。 */
	public static final class State extends LivingEntityRenderState {
		public int stage = 1;
		/** 攻撃の振り(0〜1)。0 のときは攻撃していない。 */
		public float attack;
	}

	public static final class Model extends EntityModel<LivingEntityRenderState> {
		private final List<Anim> anims = new ArrayList<>();
		private final List<StagePart> stages = new ArrayList<>();

		Model(ModelPart root, JsonObject sp) {
			super(root, RenderTypes::entityCutout);
			Function<String, ModelPart> find = root.createPartLookup();
			for (JsonElement e : sp.getAsJsonArray("parts")) {
				JsonObject p = e.getAsJsonObject();
				ModelPart part = find.apply(p.get("name").getAsString());
				if (part != null && p.has("stage")) {
					JsonArray st = p.getAsJsonArray("stage");
					if (st.get(0).getAsInt() > 1 || st.get(1).getAsInt() < 9) {
						stages.add(new StagePart(part, st.get(0).getAsInt(), st.get(1).getAsInt()));
					}
				}
				if (part != null && !p.get("anim").isJsonNull()) {
					anims.add(new Anim(part, p.get("name").getAsString(), p.get("anim").getAsString(), p.get("amp").getAsFloat(), p.get("phase").getAsFloat(),
							p.has("rest") && !p.get("rest").isJsonNull() ? p.get("rest").getAsFloat() : 0.15f,
							p.has("spd") && !p.get("spd").isJsonNull() ? p.get("spd").getAsFloat() : 0f));
				}
			}
		}

		/** 攻撃モーション: 全身が踏み込み、頭が噛みつき、前脚・腕が振り下ろされ、翼が打ち、尾が払う。 */
		private void attackPose(float a) {
			float env = Mth.sin(a * Mth.PI);
			float quick = Mth.sin(Math.min(1f, a * 1.6f) * Mth.PI);
			float late = Mth.sin(Math.max(0f, a - 0.18f) / 0.82f * Mth.PI);
			root().z -= 3.5f * env;
			root().xRot -= 0.12f * env;
			for (Anim an : anims) {
				ModelPart p = an.part;
				switch (an.kind) {
					case "head" -> p.xRot += 0.75f * quick;
					case "armA" -> p.xRot -= 1.7f * quick;
					case "armB" -> p.xRot -= 1.7f * late;
					case "legA", "legB" -> {
						if (an.name.startsWith("legF")) {
							p.xRot -= (an.kind.equals("legA") ? 1.3f : 1.0f) * (an.kind.equals("legA") ? quick : late);
						}
					}
					case "wingL" -> p.zRot += 0.9f * env;
					case "wingR" -> p.zRot -= 0.9f * env;
					case "tail" -> p.yRot += 0.9f * Mth.sin(a * Mth.TWO_PI);
					case "spin" -> p.yRot += 5f * a;
					case "pulse" -> {
						p.xScale += 0.25f * env;
						p.zScale += 0.25f * env;
					}
					default -> {
					}
				}
			}
		}

		@Override
		public void setupAnim(LivingEntityRenderState s) {
			super.setupAnim(s);
			int stage = s instanceof State st ? st.stage : 1;
			for (StagePart sp : stages) {
				sp.part.visible = stage >= sp.lo && stage <= sp.hi;
			}
			float t = s.ageInTicks, pos = s.walkAnimationPos, sp = Math.min(1f, s.walkAnimationSpeed * 1.5f);
			for (Anim a : anims) {
				ModelPart p = a.part;
				switch (a.kind) {
					case "head" -> {
						p.yRot = s.yRot * Mth.DEG_TO_RAD * Math.min(1f, a.amp);
						p.xRot = s.xRot * Mth.DEG_TO_RAD * Math.min(1f, a.amp);
					}
					case "sway" -> p.yRot = Mth.sin(t * 0.045f + a.phase) * 0.18f * a.amp;
					case "legA", "armB" -> p.xRot = Mth.cos(pos * 0.6662f) * 1.4f * sp * a.amp;
					case "legB", "armA" -> p.xRot = Mth.cos(pos * 0.6662f + Mth.PI) * 1.4f * sp * a.amp;
					case "wingL" -> p.zRot = Mth.sin(t * (a.spd > 0 ? a.spd : 0.7f) + a.phase) * 0.7f * a.amp + a.rest;
					case "wingR" -> p.zRot = -Mth.sin(t * (a.spd > 0 ? a.spd : 0.7f) + a.phase) * 0.7f * a.amp - a.rest;
					case "tail" -> p.yRot = Mth.sin(t * 0.12f + a.phase) * 0.4f * a.amp + Mth.sin(pos * 0.6662f) * 0.5f * sp;
					case "bob" -> p.y += Mth.sin(t * (a.spd > 0 ? a.spd : 0.09f) + a.phase) * 1.6f * a.amp;
					case "spin" -> p.yRot = t * 0.05f * a.amp + a.phase;
					case "pulse" -> {
						float k = Mth.sin(t * 0.15f) * 0.06f * a.amp;
						p.xScale = 1 + k;
						p.zScale = 1 + k;
						p.yScale = 1 - k;
					}
					default -> {
					}
				}
			}
			float atk = s instanceof State st2 ? st2.attack : 0f;
			if (atk > 0.001f) {
				attackPose(atk);
			}
			// 腕はじっとしているときも少し揺らす
			for (Anim a : anims) {
				if (a.kind.startsWith("arm")) {
					a.part.zRot += (a.kind.equals("armA") ? 1 : -1) * (0.05f + Mth.sin(t * 0.09f) * 0.03f);
				}
			}
		}
	}

	public static final class Renderer extends MobRenderer<Mob, LivingEntityRenderState, Model> {
		private final Identifier tex, glow;

		Renderer(EntityRendererProvider.Context ctx, String id, JsonObject sp) {
			super(ctx, new Model(ctx.bakeLayer(layer(id)), sp), 0.5f);
			this.tex = id("textures/entity/" + id + ".png");
			this.glow = id("textures/entity/" + id + "_glow.png");
			RenderType rt = RenderTypes.eyes(glow);
			addLayer(new EyesLayer<LivingEntityRenderState, Model>(this) {
				@Override
				public RenderType renderType() {
					return rt;
				}
			});
		}

		@Override
		public Identifier getTextureLocation(LivingEntityRenderState state) {
			return tex;
		}

		@Override
		public LivingEntityRenderState createRenderState() {
			return new State();
		}

		@Override
		public void extractRenderState(Mob entity, LivingEntityRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			if (state instanceof State st) {
				st.attack = entity.getSwingAnimation(partialTicks);
				st.stage = 1;
				var name = entity.getCustomName();
				if (name != null) {
					var m = java.util.regex.Pattern.compile("Lv(\\d+)").matcher(name.getString());
					if (m.find()) {
						st.stage = com.magicmod.Familiars.stageOf(Integer.parseInt(m.group(1)));
					}
				}
			}
		}
	}
}
