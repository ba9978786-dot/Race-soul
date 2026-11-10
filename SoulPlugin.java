package com.racesoul;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class SoulPlugin extends JavaPlugin {

    private NamespacedKey soulKey;
    private NamespacedKey baseKey;
    private NamespacedKey hateUnlockKey;
    private final Map<String, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        soulKey = new NamespacedKey(this, "soul");
        baseKey = new NamespacedKey(this, "base_soul");
        hateUnlockKey = new NamespacedKey(this, "hate_unlocked");
        Bukkit.getPluginManager().registerEvents(new SoulListener(this), this);
        SoulCommand cmd = new SoulCommand(this);
        getCommand("linhhon").setExecutor(cmd);
        getCommand("linhhon").setTabCompleter(cmd);
        getLogger().info("RaceSoul đã bật.");
    }

    // ---------- Linh hồn (lưu trong dữ liệu người chơi, giữ qua restart) ----------

    public Soul getSoul(Player p) {
        String id = p.getPersistentDataContainer().get(soulKey, PersistentDataType.STRING);
        return id == null ? null : Soul.byId(id);
    }

    /** Random 1 linh hồn thường cho người chơi chưa có linh hồn. Trả về null nếu đã có rồi. */
    public Soul rollSoul(Player p) {
        if (getSoul(p) != null) return null;
        Soul s = Soul.random();
        p.getPersistentDataContainer().set(soulKey, PersistentDataType.STRING, s.id);
        p.sendMessage(Component.text("Linh hồn của bạn là: Linh hồn " + s.title + "!", s.color));
        p.sendMessage(Component.text("Gõ /linhhon để xem kỹ năng.", NamedTextColor.GRAY));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 8, 0.4, 0.4, 0.4, 0);
        return s;
    }

    /** Chỉ dùng cho lệnh admin khẩn cấp: xóa linh hồn (kể cả bản gốc đang lưu khi dùng Hate). */
    public void resetSoul(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(soulKey);
        pdc.remove(baseKey);
    }

    // ---------- Linh hồn ẩn: Căm Hận ----------

    public boolean isHateUnlocked(Player p) {
        return p.getPersistentDataContainer().has(hateUnlockKey, PersistentDataType.BYTE);
    }

    public void setHateUnlocked(Player p, boolean unlocked) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (unlocked) {
            pdc.set(hateUnlockKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(hateUnlockKey);
            // Khoá lại: nếu đang dùng Hate thì trả về linh hồn gốc
            if (getSoul(p) == Soul.HATE) restoreBase(p);
        }
    }

    /** Chuyển qua lại giữa Căm Hận và linh hồn gốc. Trả về linh hồn hiện tại sau khi chuyển. */
    public Soul toggleHate(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Soul cur = getSoul(p);
        if (cur == Soul.HATE) {
            restoreBase(p);
        } else {
            if (cur != null) pdc.set(baseKey, PersistentDataType.STRING, cur.id);
            pdc.set(soulKey, PersistentDataType.STRING, Soul.HATE.id);
        }
        return getSoul(p);
    }

    private void restoreBase(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String id = pdc.get(baseKey, PersistentDataType.STRING);
        Soul base = id == null ? null : Soul.byId(id);
        if (base == null || base.hidden) base = Soul.random();
        pdc.set(soulKey, PersistentDataType.STRING, base.id);
        pdc.remove(baseKey);
    }

    // ---------- Hồi chiêu ----------

    public long cdLeftMs(Player p, String key) {
        Long t = cooldowns.get(p.getUniqueId() + ":" + key);
        if (t == null) return 0;
        return Math.max(0, t - System.currentTimeMillis());
    }

    public void setCd(Player p, String key, long seconds) {
        cooldowns.put(p.getUniqueId() + ":" + key, System.currentTimeMillis() + seconds * 1000L);
    }

    /** Nếu đang hồi chiêu thì báo trên action bar và trả về true. */
    public boolean onCooldown(Player p, String key) {
        long left = cdLeftMs(p, key);
        if (left > 0) {
            p.sendActionBar(Component.text(
                    "Hồi chiêu: " + Util.secondsLeft(left) + " giây",
                    NamedTextColor.GRAY));
            return true;
        }
        return false;
    }
}
