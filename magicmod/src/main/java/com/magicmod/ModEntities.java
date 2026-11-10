package com.magicmod;

import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import com.magicmod.MobClasses.*;

/** エンティティの種類の登録。 */
public final class ModEntities {

	/** id → 日本語名(言語ファイル生成用)。 */
	public static final Map<String, String> NAMES = new LinkedHashMap<>();
	/** id → 見た目を借りるバニラのエンティティ(クライアントの描画登録用)。 */
	public static final Map<EntityType<?>, EntityType<?>> LOOKS = new LinkedHashMap<>();
	/** id → 登録した敵・使い魔の種類(独自モデルの登録用)。 */
	public static final Map<String, EntityType<?>> TYPES = new LinkedHashMap<>();

	public static final EntityType<MagicBolt> MAGIC_BOLT = Registry.register(BuiltInRegistries.ENTITY_TYPE, key("magic_bolt"),
			EntityType.Builder.<MagicBolt>of(MagicBolt::new, MobCategory.MISC).noLootTable().sized(0.3f, 0.3f).clientTrackingRange(6)
					.updateInterval(2).build(key("magic_bolt")));

	// ---- 使い魔 ----
	public static final EntityType<FamWolf> FAM_WOLF = mob("familiar_wolf", "使い魔のウルフ", FamWolf::new, MobCategory.MISC, EntityTypes.WOLF);
	public static final EntityType<FamWolf> FAM_SPIRIT_WOLF = mob("familiar_spirit_wolf", "使い魔のスピリットウルフ", FamWolf::new, MobCategory.MISC,
			EntityTypes.WOLF);
	public static final EntityType<FamParrot> FAM_PHOENIX = mob("familiar_phoenix", "使い魔のフェニックス", FamParrot::new, MobCategory.MISC,
			EntityTypes.PARROT);
	public static final EntityType<FamGhast> FAM_PEGASUS = mob("familiar_pegasus", "使い魔のペガサス", FamGhast::new, MobCategory.MISC, EntityTypes.GHAST);
	public static final EntityType<FamWolf> FAM_BEHEMOTH = mob("familiar_behemoth", "使い魔のベヒーモス", FamWolf::new, MobCategory.MISC, EntityTypes.WOLF);
	public static final EntityType<FamSlime> FAM_SLIME = mob("familiar_mud_slime", "使い魔のマッドスライム", FamSlime::new, MobCategory.MISC,
			EntityTypes.SLIME);
	public static final EntityType<FamAllay> FAM_FAIRY = mob("familiar_fairy", "使い魔のフェアリー", FamAllay::new, MobCategory.MISC,
			EntityTypes.ALLAY);
	public static final EntityType<FamParrot> FAM_PARROT = mob("familiar_bird", "使い魔の鳥", FamParrot::new, MobCategory.MISC,
			EntityTypes.PARROT);
	public static final EntityType<FamGolem> FAM_GOLEM = mob("familiar_golem", "使い魔のゴーレム", FamGolem::new, MobCategory.MISC,
			EntityTypes.IRON_GOLEM);
	public static final EntityType<FamBlaze> FAM_ELEMENTAL = mob("familiar_elemental", "使い魔のエレメンタル", FamBlaze::new, MobCategory.MISC,
			EntityTypes.BLAZE);
	public static final EntityType<FamVex> FAM_GUARDIAN = mob("familiar_guardian", "使い魔のガーディアン", FamVex::new, MobCategory.MISC,
			EntityTypes.VEX);
	public static final EntityType<FamGhast> FAM_DRAGON = mob("familiar_ancient_dragon", "エンシェントドラゴン", FamGhast::new, MobCategory.MISC,
			EntityTypes.GHAST);

	// ---- 変身した姿(見た目だけのエンティティ) ----
	public static final EntityType<FormAvatar> FORM_VAMPIRE = mob("form_vampire", "変身(ドラキュラ)", FormAvatar::new, MobCategory.MISC, EntityTypes.ZOMBIE);
	public static final EntityType<FormAvatar> FORM_WOLF = mob("form_wolf", "変身(ウルフ)", FormAvatar::new, MobCategory.MISC, EntityTypes.ZOMBIE);
	public static final EntityType<FormAvatar> FORM_KONG = mob("form_kong", "変身(コング)", FormAvatar::new, MobCategory.MISC, EntityTypes.ZOMBIE);
	public static final EntityType<FormAvatar> FORM_DRAGONOID = mob("form_dragonoid", "変身(ドラゴン人)", FormAvatar::new, MobCategory.MISC, EntityTypes.ZOMBIE);
	public static final EntityType<FormAvatar> FORM_ANGEL = mob("form_angel", "変身(天使)", FormAvatar::new, MobCategory.MISC, EntityTypes.ZOMBIE);
	public static final EntityType<FormAvatar> FORM_SHADOW = mob("form_shadow", "変身(影)", FormAvatar::new, MobCategory.MISC, EntityTypes.ZOMBIE);
	public static final EntityType<FormAvatar> FORM_GOLEM = mob("form_golem", "変身(ゴーレム)", FormAvatar::new, MobCategory.MISC, EntityTypes.ZOMBIE);
	public static final EntityType<FormAvatar> FORM_THUNDER = mob("form_thunder", "変身(雷神)", FormAvatar::new, MobCategory.MISC, EntityTypes.ZOMBIE);

	// ---- 敵(雑魚) ----
	public static final EntityType<EBreeze> WIND_SPRITE = mob("wind_sprite", "ウィンドスプライト", EBreeze::new, MobCategory.MONSTER, EntityTypes.BREEZE);
	public static final EntityType<EPhantom> TORNADO_BAT = mob("tornado_bat", "トルネードバット", EPhantom::new, MobCategory.MONSTER, EntityTypes.PHANTOM);
	public static final EntityType<ESlime> SPARK_JELLY = mob("spark_jelly", "スパークジェリー", ESlime::new, MobCategory.MONSTER, EntityTypes.SLIME);
	public static final EntityType<ESilverfish> VOLT_RAT = mob("volt_rat", "ボルトラット", ESilverfish::new, MobCategory.MONSTER, EntityTypes.SILVERFISH);
	public static final EntityType<EBlaze> FLAME_IMP = mob("flame_imp", "フレイムインプ", EBlaze::new, MobCategory.MONSTER, EntityTypes.BLAZE);
	public static final EntityType<ERavager> MAGMA_HOUND = mob("magma_hound", "マグマハウンド", ERavager::new, MobCategory.MONSTER, EntityTypes.RAVAGER);
	public static final EntityType<EDrowned> TIDELING = mob("tideling", "タイドリング", EDrowned::new, MobCategory.MONSTER, EntityTypes.DROWNED);
	public static final EntityType<EGuardian> KRAKEN_SPAWN = mob("kraken_spawn", "クラーケンスポーン", EGuardian::new, MobCategory.MONSTER,
			EntityTypes.GUARDIAN);
	public static final EntityType<EShulker> GRAVITY_MIMIC = mob("gravity_mimic", "グラビティミミック", EShulker::new, MobCategory.MONSTER,
			EntityTypes.SHULKER);
	public static final EntityType<EPhantom> VOID_BAT = mob("void_bat", "ボイドバット", EPhantom::new, MobCategory.MONSTER, EntityTypes.PHANTOM);
	public static final EntityType<EEnderman> SHADOW_STALKER = mob("shadow_stalker", "シャドウストーカー", EEnderman::new, MobCategory.MONSTER,
			EntityTypes.ENDERMAN);
	public static final EntityType<EPhantom> NIGHTMARE_BAT = mob("nightmare_bat", "ナイトメアバット", EPhantom::new, MobCategory.MONSTER,
			EntityTypes.PHANTOM);
	public static final EntityType<EVex> LUMIN_SPRITE = mob("lumin_sprite", "ルミンスプライト", EVex::new, MobCategory.MONSTER, EntityTypes.VEX);
	public static final EntityType<EGolem> HOLY_GUARDIAN = mob("holy_guardian", "ホーリーガーディアン(小型)", EGolem::new, MobCategory.MONSTER,
			EntityTypes.IRON_GOLEM);
	public static final EntityType<ESkeleton> CURSED_PUPPET = mob("cursed_puppet", "カースドパペット", ESkeleton::new, MobCategory.MONSTER,
			EntityTypes.SKELETON);
	public static final EntityType<EVex> JINX_MOTH = mob("jinx_moth", "ジンクスモス", EVex::new, MobCategory.MONSTER, EntityTypes.VEX);

	// ---- 中ボス ----
	public static final EntityType<EBreeze> GALE_HARPY = mob("gale_harpy", "ゲイルハルピー", EBreeze::new, MobCategory.MONSTER, EntityTypes.BREEZE);
	public static final EntityType<ERavager> THUNDER_WOLF_ALPHA = mob("thunder_wolf_alpha", "サンダーウルフ・アルファ", ERavager::new,
			MobCategory.MONSTER, EntityTypes.RAVAGER);
	public static final EntityType<EMagmaCube> SALAMANDER_KING = mob("salamander_king", "サラマンダーキング", EMagmaCube::new, MobCategory.MONSTER,
			EntityTypes.MAGMA_CUBE);
	public static final EntityType<EElderGuardian> LEVIATHAN_JUVENILE = mob("leviathan_juvenile", "レヴィアタンの幼体", EElderGuardian::new,
			MobCategory.MONSTER, EntityTypes.ELDER_GUARDIAN);
	public static final EntityType<EGolem> ORBIT_GOLEM = mob("orbit_golem", "オービットゴーレム", EGolem::new, MobCategory.MONSTER,
			EntityTypes.IRON_GOLEM);
	public static final EntityType<EWitherSkeleton> ABYSS_EXECUTOR = mob("abyss_executor", "深淵の執行官", EWitherSkeleton::new, MobCategory.MONSTER,
			EntityTypes.WITHER_SKELETON);
	public static final EntityType<EVindicator> HOLY_KNIGHT = mob("holy_knight_remnant", "聖騎士の残影", EVindicator::new, MobCategory.MONSTER,
			EntityTypes.VINDICATOR);
	public static final EntityType<EWitch> CURSE_WITCH = mob("curse_witch", "呪詛の魔女", EWitch::new, MobCategory.MONSTER, EntityTypes.WITCH);

	// ---- 属性超越ボス ----
	public static final EntityType<EPhantom> ZEPHARION = mob("zepharion", "天空の暴君ゼファリオン", EPhantom::new, MobCategory.MONSTER, EntityTypes.PHANTOM);
	public static final EntityType<EBlaze> GARDONIUS = mob("gardonius", "雷神ガルドニウス", EBlaze::new, MobCategory.MONSTER, EntityTypes.BLAZE);
	public static final EntityType<EGhast> IGDRUM = mob("igdrum", "業火の古竜イグドラム", EGhast::new, MobCategory.MONSTER, EntityTypes.GHAST);
	public static final EntityType<EDrowned> MELUSINE = mob("melusine", "深淵の女王メリュジーヌ", EDrowned::new, MobCategory.MONSTER, EntityTypes.DROWNED);
	public static final EntityType<EEnderman> SINGULARIS = mob("singularis", "特異点の番人シンギュラリス", EEnderman::new, MobCategory.MONSTER,
			EntityTypes.ENDERMAN);
	public static final EntityType<EEvoker> NECROVANT = mob("necrovant", "虚無卿ネクロヴァント", EEvoker::new, MobCategory.MONSTER, EntityTypes.EVOKER);
	public static final EntityType<EVex> SERAPHIEL = mob("seraphiel", "大天使セラフィエル", EVex::new, MobCategory.MONSTER, EntityTypes.VEX);
	public static final EntityType<EGolem> OMNIS = mob("omnis", "万象の魔王オムニス", EGolem::new, MobCategory.MONSTER, EntityTypes.IRON_GOLEM);
	public static final EntityType<EGolem> APOCALYPSE = mob("apocalypse", "終焉神アポカリプス", EGolem::new, MobCategory.MONSTER, EntityTypes.IRON_GOLEM);
	public static final EntityType<ERavager> MORODIS = mob("morodis", "災厄の化身モロディス", ERavager::new, MobCategory.MONSTER, EntityTypes.RAVAGER);

	private ModEntities() {
	}

	/** 独自モデルの見た目に合わせた当たり判定(tools/gen_models.py が JSON に書く)。なければ元のモブの大きさ。 */
	private static float[] hitbox(String id) {
		try (java.io.InputStream in = ModEntities.class.getResourceAsStream("/assets/magicmod/models/custom/" + id + ".json")) {
			if (in == null) {
				return null;
			}
			var o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			if (!o.has("hitbox")) {
				return null;
			}
			var a = o.getAsJsonArray("hitbox");
			return new float[] { a.get(0).getAsFloat(), a.get(1).getAsFloat() };
		} catch (Exception e) {
			return null;
		}
	}

	private static ResourceKey<EntityType<?>> key(String id) {
		return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, id));
	}

	@SuppressWarnings("unchecked")
	private static <T extends LivingEntity> EntityType<T> mob(String id, String ja, EntityType.EntityFactory<T> f, MobCategory cat,
			EntityType<?> look) {
		EntityDimensions d = look.getDimensions();
		float[] hb = hitbox(id);
		if (hb != null) {
			d = EntityDimensions.scalable(hb[0], hb[1]).withEyeHeight(hb[1] * 0.85f);
		}
		EntityType.Builder<T> b = EntityType.Builder.of(f, cat).sized(d.width(), d.height()).eyeHeight(d.eyeHeight()).clientTrackingRange(10);
		if (look.fireImmune()) {
			b = b.fireImmune();
		}
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key(id), b.build(key(id)));
		FabricDefaultAttributeRegistry.register(type, DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) look));
		NAMES.put(id, ja);
		LOOKS.put(type, look);
		TYPES.put(id, type);
		return type;
	}

	public static void init() {
	}
}
