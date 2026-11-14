package com.magicmod;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

/** 属性ごとの色で、敵・使い魔の輪郭を光らせる(チームカラー + 発光)。専用モデルの代わりに、一目で属性と味方が分かるようにする。 */
public final class Glow {

	private Glow() {
	}

	private static TeamColor color(Element e) {
		return switch (e) {
			case WIND -> TeamColor.GREEN;
			case THUNDER -> TeamColor.YELLOW;
			case FIRE -> TeamColor.RED;
			case WATER -> TeamColor.AQUA;
			case GRAVITY -> TeamColor.DARK_PURPLE;
			case DARK -> TeamColor.BLUE;
			case LIGHT -> TeamColor.WHITE;
			case CURSE -> TeamColor.LIGHT_PURPLE;
		};
	}

	/** その属性のチームに入れて光らせる。 */
	public static void apply(Entity e, Element el) {
		if (!(e.level() instanceof ServerLevel l)) {
			return;
		}
		Scoreboard sb = l.getScoreboard();
		String name = "mm_" + el.id;
		PlayerTeam team = sb.getPlayerTeam(name);
		if (team == null) {
			team = sb.addPlayerTeam(name);
			team.setColor(Optional.of(color(el)));
			team.setAllowFriendlyFire(true);
			team.setSeeFriendlyInvisibles(false);
		}
		sb.addPlayerToTeam(e.getScoreboardName(), team);
		e.setGlowingTag(true);
	}
}
