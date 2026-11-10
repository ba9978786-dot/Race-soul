package com.racesoul;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class SoulCommand implements CommandExecutor, TabCompleter {
    private final SoulPlugin plugin;

    public SoulCommand(SoulPlugin plugin) {
        this.plugin = plugin;
    }

    private static void msg(CommandSender s, String text, NamedTextColor c) {
        s.sendMessage(Component.text(text, c));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase() : "";

        // ----- Lệnh admin: reset / unlock / lock -----
        if (sub.equals("reset") || sub.equals("unlock") || sub.equals("lock")) {
            if (!sender.hasPermission("racesoul.admin")) {
                msg(sender, "Bạn không có quyền.", NamedTextColor.RED);
                return true;
            }
            if (args.length < 2) {
                msg(sender, "Dùng: /linhhon " + sub + " <người chơi>", NamedTextColor.RED);
                return true;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                msg(sender, "Người chơi không online.", NamedTextColor.RED);
                return true;
            }
            switch (sub) {
                case "reset" -> {
                    plugin.resetSoul(t);
                    Soul n = plugin.rollSoul(t);
                    msg(sender, "Đã xóa linh hồn của " + t.getName() + " và random lại: "
                            + (n == null ? "?" : n.title) + ".", NamedTextColor.YELLOW);
                }
                case "unlock" -> {
                    plugin.setHateUnlocked(t, true);
                    msg(sender, "Đã mở khoá linh hồn Căm Hận cho " + t.getName() + ".", NamedTextColor.YELLOW);
                    t.sendMessage(Component.text("Bạn đã mở khoá linh hồn ẩn: Căm Hận.", NamedTextColor.DARK_GRAY));
                    t.sendMessage(Component.text("Gõ /linhhon hate để chuyển sang dùng.", NamedTextColor.GRAY));
                }
                default -> {
                    plugin.setHateUnlocked(t, false);
                    msg(sender, "Đã khoá lại linh hồn Căm Hận của " + t.getName() + ".", NamedTextColor.YELLOW);
                }
            }
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Chỉ người chơi mới dùng được lệnh này.");
            return true;
        }

        // ----- Chưa có linh hồn: random -----
        Soul s = plugin.getSoul(p);
        if (s == null) {
            plugin.rollSoul(p);
            return true;
        }

        // ----- /linhhon hate: chuyển qua lại Căm Hận <-> linh hồn gốc -----
        if (sub.equals("hate")) {
            if (!plugin.isHateUnlocked(p)) {
                msg(p, "Bạn chưa mở khoá linh hồn này.", NamedTextColor.RED);
                return true;
            }
            int cdSec = plugin.getConfig().getInt("hate.switch-cooldown-seconds", 60);
            if (plugin.onCooldown(p, "hate-switch")) return true;
            Soul now = plugin.toggleHate(p);
            plugin.setCd(p, "hate-switch", cdSec);
            msg(p, "Bạn đã chuyển sang Linh hồn " + now.title + ".", now.color);
            return true;
        }

        // ----- Xem thông tin -----
        msg(p, "Linh hồn của bạn: " + s.title, s.color);
        for (String l : s.describe(plugin.getConfig())) {
            msg(p, " - " + l, NamedTextColor.GRAY);
        }
        if (plugin.isHateUnlocked(p) && s != Soul.HATE) {
            msg(p, "Bạn đã mở khoá linh hồn ẩn! Gõ /linhhon hate để chuyển.", NamedTextColor.DARK_GRAY);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("racesoul.admin");
        if (args.length == 1) {
            out.add("info");
            if (sender instanceof Player p && plugin.isHateUnlocked(p)) out.add("hate");
            if (admin) {
                out.add("reset");
                out.add("unlock");
                out.add("lock");
            }
        } else if (args.length == 2 && admin
                && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("unlock")
                || args[0].equalsIgnoreCase("lock"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        return out;
    }
}
