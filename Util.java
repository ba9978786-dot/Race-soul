package com.racesoul;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class Util {
    private Util() {}

    private static String entry(Entity e) {
        return e instanceof Player p ? p.getName() : e.getUniqueId().toString();
    }

    /** Đồng minh = cùng Team scoreboard (giống quy ước của RaceFantasy). */
    public static boolean isAlly(Entity a, Entity b) {
        if (a.equals(b)) return true;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team ta = sb.getEntryTeam(entry(a));
        Team tb = sb.getEntryTeam(entry(b));
        return ta != null && tb != null && ta.getName().equals(tb.getName());
    }

    /** Có thể bị kỹ năng nhắm trúng không (không phải bản thân, đồng minh, armor stand, người chơi creative/spectator). */
    public static boolean canTarget(Player self, Entity e) {
        if (!(e instanceof LivingEntity le) || le.isDead() || e instanceof ArmorStand) return false;
        if (isAlly(self, e)) return false;
        if (e instanceof Player pl) {
            GameMode g = pl.getGameMode();
            return g == GameMode.SURVIVAL || g == GameMode.ADVENTURE;
        }
        return true;
    }

    /** Mục tiêu để kỹ năng tự dò: chỉ người chơi hoặc quái thù địch. */
    public static boolean canLockOn(Player self, Entity e) {
        return canTarget(self, e) && (e instanceof Player || e instanceof Enemy);
    }

    public static long secondsLeft(long ms) {
        return (ms + 999) / 1000;
    }
}
