package com.magicmod;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 生活の魔導書。戦いではなく暮らしのための魔法(5 つ)。しゃがみ+右クリックで切り替え、右クリックで使う。
 * 成長(周囲の作物・草木を育てる)/収穫(実った作物を刈り取って植え直す)/灯火(明かりを置く)/浄化(状態異常と火を消す)/採掘補助(つながった鉱石をまとめて掘る)。
 */
public class LifeTomeItem extends Item {

	public static final String[] NAMES = { "成長", "収穫", "灯火", "浄化", "採掘補助" };
	private static final float[] COST = { 8, 6, 3, 10, 12 };

	public LifeTomeItem(Properties p) {
		super(p);
	}

	private static int mode(ItemStack s) {
		var d = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		return d == null ? 0 : Math.floorMod(d.copyTag().getIntOr("mode", 0), NAMES.length);
	}

	private static void setMode(ItemStack s, int m) {
		net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
		t.putInt("mode", m);
		s.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(t));
	}

	private static boolean pay(ServerPlayer p, int mode) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (p.isCreative()) {
			return true;
		}
		if (pm.mp < COST[mode]) {
			Casting.msg(p, "MP が足りません(" + Math.round(COST[mode]) + " 必要)", ChatFormatting.BLUE);
			return false;
		}
		pm.mp -= COST[mode];
		pm.dirty = true;
		return true;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack s = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			int m = (mode(s) + 1) % NAMES.length;
			setMode(s, m);
			Casting.msg(sp, "生活魔法: " + NAMES[m], ChatFormatting.GREEN);
			level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), player.getSoundSource(), 0.4f, 1.5f);
			return InteractionResult.SUCCESS;
		}
		int m = mode(s);
		ServerLevel sl = (ServerLevel) level;
		switch (m) {
			case 0 -> {
				if (pay(sp, m)) {
					grow(sl, sp, player.blockPosition(), 4);
				}
			}
			case 1 -> {
				if (pay(sp, m)) {
					harvest(sl, sp, player.blockPosition(), 5);
				}
			}
			case 3 -> {
				if (pay(sp, m)) {
					purify(sl, sp);
				}
			}
			default -> Casting.msg(sp, NAMES[m] + "は、ブロックに向けて右クリック", ChatFormatting.GRAY);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext c) {
		Level level = c.getLevel();
		if (level.isClientSide() || !(c.getPlayer() instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (sp.isShiftKeyDown()) {
			return use(level, sp, c.getHand());
		}
		int m = mode(c.getItemInHand());
		ServerLevel sl = (ServerLevel) level;
		BlockPos pos = c.getClickedPos();
		switch (m) {
			case 0 -> {
				if (pay(sp, m)) {
					grow(sl, sp, pos, 4);
				}
			}
			case 1 -> {
				if (pay(sp, m)) {
					harvest(sl, sp, pos, 5);
				}
			}
			case 2 -> {
				BlockPos at = pos.relative(c.getClickedFace());
				if (level.getBlockState(at).isAir() && Blocks.TORCH.defaultBlockState().canSurvive(level, at) && pay(sp, m)) {
					level.setBlock(at, c.getClickedFace() == net.minecraft.core.Direction.UP ? Blocks.TORCH.defaultBlockState()
							: Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, c.getClickedFace()), 3);
					sl.sendParticles(ParticleTypes.FLAME, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 8, 0.15, 0.15, 0.15, 0.01);
					sl.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, sp.getSoundSource(), 1f, 1.5f);
				}
			}
			case 3 -> {
				if (pay(sp, m)) {
					purify(sl, sp);
				}
			}
			default -> {
				BlockState st = level.getBlockState(pos);
				if (isOre(st) && pay(sp, m)) {
					int n = veinMine(sl, sp, pos, 14);
					Casting.msg(sp, "採掘補助: " + n + " 個", ChatFormatting.GREEN);
				} else if (!isOre(st)) {
					Casting.msg(sp, "鉱石に向けて使います", ChatFormatting.GRAY);
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	static boolean isOre(BlockState st) {
		return st.is(BlockTags.ORES) || st.is(Blocks.NETHER_QUARTZ_ORE) || st.is(Blocks.NETHER_GOLD_ORE) || st.is(Blocks.ANCIENT_DEBRIS)
				|| ModBlocks.ALL.get("crystal_ore") == st.getBlock() || ModBlocks.ALL.get("deepslate_crystal_ore") == st.getBlock()
				|| ModBlocks.ALL.get("silver_ore") == st.getBlock() || ModBlocks.ALL.get("deepslate_silver_ore") == st.getBlock();
	}

	/** 周囲の作物・苗木・草を育てる(骨粉と同じ効果)。 */
	static void grow(ServerLevel l, ServerPlayer p, BlockPos c, int r) {
		int n = 0;
		for (BlockPos b : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
			BlockState st = l.getBlockState(b);
			if (st.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock bm && bm.isValidBonemealTarget(l, b, st, BonemealSource.INTERACTION)) {
				if (n < 24 && l.getRandom().nextFloat() < 0.8f) {
					bm.performBonemeal(l, l.getRandom(), b, st, BonemealSource.INTERACTION);
					l.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.getX() + 0.5, b.getY() + 0.8, b.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0);
					n++;
				}
			}
		}
		Vfx.ring(l, Vec3.atBottomCenterOf(c).add(0, 0.2, 0), r, ParticleTypes.HAPPY_VILLAGER, 40);
		l.playSound(null, c, SoundEvents.BONE_MEAL_USE, p.getSoundSource(), 1f, 1f);
		Casting.msg(p, "成長: " + n + " 箇所", ChatFormatting.GREEN);
	}

	/** 実った作物を収穫して植え直す(収穫物は足元に落ちる)。 */
	static void harvest(ServerLevel l, ServerPlayer p, BlockPos c, int r) {
		int n = 0;
		for (BlockPos b : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
			BlockState st = l.getBlockState(b);
			if (st.getBlock() instanceof CropBlock crop && crop.isMaxAge(st)) {
				Block.dropResources(st, l, b, null, p, ItemStack.EMPTY);
				l.setBlock(b, crop.getStateForAge(0), 3);
				l.sendParticles(ParticleTypes.COMPOSTER, b.getX() + 0.5, b.getY() + 0.7, b.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
				n++;
			}
		}
		l.playSound(null, c, SoundEvents.CROP_BREAK, p.getSoundSource(), 1f, 1.1f);
		Casting.msg(p, "収穫: " + n + " 個", ChatFormatting.GREEN);
	}

	/** 自分と近くの味方の状態異常を消し、火を消す。 */
	static void purify(ServerLevel l, ServerPlayer p) {
		Ctx.cleanse(p);
		for (var e : l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(6), x -> Combat.isAlly(p, x))) {
			Ctx.cleanse(e);
		}
		Terrain.douse(l, p.position(), 5);
		p.getFoodData().eat(2, 0.3f);
		Vfx.pillar(l, p.position(), 3, 0.8, ParticleTypes.END_ROD, 4);
		l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, p.getSoundSource(), 0.8f, 1.6f);
		Casting.msg(p, "浄化", ChatFormatting.AQUA);
	}

	/** つながった同じ鉱石をまとめて掘る(上限あり)。 */
	static int veinMine(ServerLevel l, ServerPlayer p, BlockPos start, int max) {
		Block type = l.getBlockState(start).getBlock();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		q.add(start);
		seen.add(start);
		int n = 0;
		while (!q.isEmpty() && n < max) {
			BlockPos b = q.poll();
			if (l.getBlockState(b).getBlock() != type) {
				continue;
			}
			l.destroyBlock(b, true, p);
			n++;
			for (BlockPos d : BlockPos.betweenClosed(b.offset(-1, -1, -1), b.offset(1, 1, 1))) {
				BlockPos i = d.immutable();
				if (seen.add(i) && l.getBlockState(i).getBlock() == type) {
					q.add(i);
				}
			}
		}
		return n;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack s, TooltipContext ctx, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
		out.accept(Component.literal("いまの魔法: " + NAMES[mode(s)]).withStyle(ChatFormatting.GREEN));
		out.accept(Component.literal("成長・収穫・灯火・浄化・採掘補助").withStyle(ChatFormatting.GRAY));
		out.accept(Component.literal("しゃがみ+右クリックで切り替え / 右クリックで使う(MP を消費)").withStyle(ChatFormatting.DARK_GRAY));
	}
}
