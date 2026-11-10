package com.racesoul;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SoulListener implements Listener {
    private final SoulPlugin plugin;

    private record Save(Location loc, double health, long expire) {}

    private static final class Stack {
        int n;
        long expire;
    }

    /** Đỏ: điểm SAVE. */
    private final Map<UUID, Save> saves = new HashMap<>();
    /** Xanh lá: thời điểm hết khiên (ms). */
    private final Map<UUID, Long> shieldUntil = new HashMap<>();
    /** Xanh dương: mục tiêu bị ép xuống đất (không nhảy được). */
    private final Map<UUID, Long> pinned = new HashMap<>();
    /** Tím: số tầng Bất Khuất. */
    private final Map<UUID, Stack> stacks = new HashMap<>();
    /** Đen: đang trong đợt bùng nổ (để không hút máu thường chồng lên). */
    private boolean hateBlast = false;

    public SoulListener(SoulPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ===================== Nhận linh hồn (random khi vào server) =====================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg().getBoolean("roll-on-join", true)) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && plugin.getSoul(p) == null) plugin.rollSoul(p);
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        saves.remove(id);
        shieldUntil.remove(id);
        pinned.remove(id);
        stacks.remove(id);
    }

    // ===================== Nhận sát thương: Khiên Tử Tế, miễn rơi (Xanh dương), Bất Khuất (Tím) =====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Soul s = plugin.getSoul(p);
        if (s == null) return;

        DamageCause c = e.getCause();
        if (c == DamageCause.VOID || c == DamageCause.KILL || c == DamageCause.SUICIDE
                || c == DamageCause.WORLD_BORDER || c == DamageCause.STARVATION) return;

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();

        if (s == Soul.GREEN) {
            Long until = shieldUntil.get(id);
            if (until != null && now < until) {
                e.setCancelled(true);
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f);
                return;
            }
        } else if (s == Soul.BLUE) {
            if (c == DamageCause.FALL) {
                e.setCancelled(true);
            }
        } else if (s == Soul.HATE) {
            double red = Math.max(0, Math.min(0.9, cfg().getDouble("hate.damage-reduction", 0.25)));
            e.setDamage(e.getDamage() * (1 - red));
        } else if (s == Soul.PURPLE) {
            if (e.getFinalDamage() <= 0) return;
            int max = Math.max(1, cfg().getInt("purple.max-stacks", 5));
            long keep = cfg().getInt("purple.stack-seconds", 10) * 1000L;
            Stack st = stacks.computeIfAbsent(id, k -> new Stack());
            if (now > st.expire) st.n = 0;
            st.n = Math.min(max, st.n + 1);
            st.expire = now + keep;
            applyStacks(p, st.n, (int) (keep / 50L));
        }
    }

    private void applyStacks(Player p, int n, int ticks) {
        if (n >= 3) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 1, true, false, true));
        } else if (n >= 1) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 0, true, false, true));
        }
        if (n >= 5) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 1, true, false, true));
        } else if (n >= 3) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0, true, false, true));
        }
        p.sendActionBar(Component.text("Bất Khuất x" + n, NamedTextColor.DARK_PURPLE));
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0,
                new Particle.DustOptions(Color.PURPLE, 1.2f));
    }

    /** Đen: đánh thường hút máu. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHateMelee(EntityDamageByEntityEvent e) {
        if (hateBlast || !(e.getDamager() instanceof Player p)) return;
        if (plugin.getSoul(p) != Soul.HATE) return;
        if (!(e.getEntity() instanceof LivingEntity le) || !Util.canTarget(p, le)) return;
        double heal = e.getFinalDamage() * cfg().getDouble("hate.melee-lifesteal", 0.2);
        if (heal <= 0) return;
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + heal));
    }

    /** Xanh dương: mục tiêu bị ép xuống đất không nhảy lên được. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (pinned.isEmpty() || e.getTo() == null) return;
        Player p = e.getPlayer();
        Long until = pinned.get(p.getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() >= until) {
            pinned.remove(p.getUniqueId());
            return;
        }
        if (e.getTo().getY() > e.getFrom().getY()) {
            Location to = e.getTo().clone();
            to.setY(e.getFrom().getY());
            e.setTo(to);
        }
    }

    // ===================== Kỹ năng chủ động: Shift + chuột phải tay không =====================

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        Soul s = plugin.getSoul(p);
        if (s == null || s == Soul.PURPLE) return;
        e.setCancelled(true);
        switch (s) {
            case RED -> castSaveLoad(p);
            case ORANGE -> castSlash(p);
            case YELLOW -> castBeam(p);
            case GREEN -> castShield(p);
            case BLUE -> castGravity(p);
            case CYAN -> castString(p);
            case HATE -> castHate(p);
            default -> { }
        }
    }

    // ---------- Đỏ: SAVE / LOAD ----------
    private void castSaveLoad(Player p) {
        if (plugin.onCooldown(p, "red")) return;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Save sv = saves.get(id);
        World w = p.getWorld();

        if (sv != null && now <= sv.expire() && sv.loc().getWorld() != null) {
            // LOAD
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            w.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.05);
            p.teleport(sv.loc());
            p.setFallDistance(0f);
            p.setFireTicks(0);
            p.setHealth(Math.max(1.0, Math.min(max, sv.health())));
            saves.remove(id);
            plugin.setCd(p, "red", cfg().getInt("red.cooldown-seconds", 300));
            World w2 = p.getWorld();
            w2.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.05);
            w2.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
            p.sendActionBar(Component.text("LOAD! Quay về điểm đã lưu.", NamedTextColor.RED));
        } else {
            // SAVE
            long window = cfg().getInt("red.save-window-seconds", 60) * 1000L;
            saves.put(id, new Save(p.getLocation().clone(), p.getHealth(), now + window));
            w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 20, 0.4, 0.7, 0.4, 0,
                    new Particle.DustOptions(Color.RED, 1.4f));
            w.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.6f);
            p.sendActionBar(Component.text("SAVE! Bấm lại trong " + (window / 1000L) + "s để LOAD.", NamedTextColor.RED));
        }
    }

    // ---------- Cam: nhát chém tự dò, nổ khi chạm ----------
    private LivingEntity nearestLockOn(Player p, double range) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity en : p.getNearbyEntities(range, range, range)) {
            if (!Util.canLockOn(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            if (!p.hasLineOfSight(le)) continue;
            double d = le.getLocation().distanceSquared(p.getLocation());
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    private void castSlash(Player p) {
        if (plugin.onCooldown(p, "orange")) return;
        FileConfiguration c = cfg();
        final double dmg = c.getDouble("orange.damage", 8.0);
        final double radius = c.getDouble("orange.explode-radius", 3.0);
        final double speed = c.getDouble("orange.speed", 1.2);
        final double range = c.getDouble("orange.range", 30.0);
        final LivingEntity target = nearestLockOn(p, c.getDouble("orange.lock-range", 20.0));
        final Location start = p.getEyeLocation();
        final Vector startDir = start.getDirection().normalize();
        plugin.setCd(p, "orange", c.getInt("orange.cooldown-seconds", 10));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);

        new BukkitRunnable() {
            final Location pos = start.clone();
            Vector dir = startDir.clone();
            double traveled = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                if (target != null && !target.isDead() && target.getWorld().equals(pos.getWorld())) {
                    Vector to = target.getBoundingBox().getCenter().subtract(pos.toVector());
                    if (to.lengthSquared() > 0.0001) {
                        dir = dir.multiply(0.6).add(to.normalize().multiply(0.4)).normalize();
                    }
                }
                pos.add(dir.clone().multiply(speed));
                traveled += speed;
                World w = pos.getWorld();
                w.spawnParticle(Particle.DUST, pos, 5, 0.15, 0.15, 0.15, 0,
                        new Particle.DustOptions(Color.ORANGE, 1.5f));

                boolean hit = pos.getBlock().getType().isSolid() || traveled >= range;
                if (!hit) {
                    for (LivingEntity le : pos.getNearbyLivingEntities(1.0)) {
                        if (Util.canTarget(p, le)) {
                            hit = true;
                            break;
                        }
                    }
                }
                if (hit) {
                    explode(p, pos, dmg, radius);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void explode(Player owner, Location loc, double dmg, double radius) {
        World w = loc.getWorld();
        w.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.DUST, loc, 30, radius / 2, radius / 2, radius / 2, 0,
                new Particle.DustOptions(Color.ORANGE, 1.6f));
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        for (LivingEntity le : loc.getNearbyLivingEntities(radius)) {
            if (Util.canTarget(owner, le)) le.damage(dmg, owner);
        }
    }

    // ---------- Vàng: chùm năng lượng ----------
    private void castBeam(Player p) {
        if (plugin.onCooldown(p, "yellow")) return;
        double range = cfg().getDouble("yellow.range", 30);
        double dmg = cfg().getDouble("yellow.damage", 8.0);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();

        RayTraceResult rb = w.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double max = rb == null ? range : rb.getHitPosition().distance(eye.toVector());
        RayTraceResult re = w.rayTraceEntities(eye, dir, max, 0.4, en -> Util.canTarget(p, en));

        double len = max;
        LivingEntity hit = null;
        if (re != null && re.getHitEntity() instanceof LivingEntity le) {
            hit = le;
            len = re.getHitPosition().distance(eye.toVector());
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.YELLOW, 1.3f);
        for (double d = 1.0; d <= len; d += 0.5) {
            w.spawnParticle(Particle.DUST, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
        }
        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.6f);
        if (hit != null) hit.damage(dmg, p);
        plugin.setCd(p, "yellow", cfg().getInt("yellow.cooldown-seconds", 8));
    }

    // ---------- Xanh lá: Khiên Tử Tế ----------
    private void castShield(Player p) {
        if (plugin.onCooldown(p, "green")) return;
        FileConfiguration c = cfg();
        int sec = c.getInt("green.shield-seconds", 3);
        shieldUntil.put(p.getUniqueId(), System.currentTimeMillis() + sec * 1000L);
        plugin.setCd(p, "green", c.getInt("green.cooldown-seconds", 25));

        double r = c.getDouble("green.heal-radius", 6);
        int healTicks = c.getInt("green.heal-seconds", 4) * 20;
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (en instanceof Player ally && !ally.equals(p) && Util.isAlly(p, ally)) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, healTicks, 1, true, true, true));
            }
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
        p.sendActionBar(Component.text("Khiên Tử Tế!", NamedTextColor.GREEN));

        final int totalTicks = sec * 20;
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= totalTicks) {
                    cancel();
                    return;
                }
                Location base = p.getLocation();
                Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.1f);
                for (int i = 0; i < 12; i++) {
                    double a = i * (Math.PI * 2 / 12);
                    for (double y : new double[]{0.2, 1.0, 1.8}) {
                        p.getWorld().spawnParticle(Particle.DUST,
                                base.clone().add(Math.cos(a) * 1.4, y, Math.sin(a) * 1.4), 1, 0, 0, 0, 0, dust);
                    }
                }
                t += 3;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    // ---------- Xanh dương: trọng lực ----------
    private void castGravity(Player p) {
        if (plugin.onCooldown(p, "blue")) return;
        FileConfiguration c = cfg();
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = w.rayTraceEntities(eye, dir, c.getDouble("blue.target-range", 16), 0.6,
                en -> Util.canTarget(p, en));

        if (r != null && r.getHitEntity() instanceof LivingEntity t) {
            long ms = c.getInt("blue.pin-seconds", 3) * 1000L;
            pinned.put(t.getUniqueId(), System.currentTimeMillis() + ms);
            Vector v = t.getVelocity();
            v.setY(c.getDouble("blue.slam-velocity", -0.9));
            t.setVelocity(v);
            w.spawnParticle(Particle.DUST, t.getLocation().add(0, 0.2, 0), 20, 0.5, 0.1, 0.5, 0,
                    new Particle.DustOptions(Color.BLUE, 1.4f));
            w.playSound(t.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.7f);
        } else {
            Vector h = new Vector(dir.getX(), 0, dir.getZ());
            if (h.lengthSquared() > 0.0001) h.normalize().multiply(c.getDouble("blue.leap-power", 1.4));
            h.setY(c.getDouble("blue.leap-height", 0.8));
            p.setVelocity(h);
            w.spawnParticle(Particle.DUST, p.getLocation(), 20, 0.4, 0.1, 0.4, 0,
                    new Particle.DustOptions(Color.BLUE, 1.4f));
            w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
        }
        plugin.setCd(p, "blue", c.getInt("blue.cooldown-seconds", 12));
    }

    // ---------- Xanh ngọc: sợi tơ ----------
    private void castString(Player p) {
        FileConfiguration c = cfg();
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        RayTraceResult r = w.rayTraceEntities(eye, eye.getDirection(), c.getDouble("cyan.range", 20), 0.6,
                en -> Util.canTarget(p, en));
        if (r == null || !(r.getHitEntity() instanceof LivingEntity target)) {
            p.sendActionBar(Component.text("Không có mục tiêu trước mặt.", NamedTextColor.GRAY));
            return;
        }
        if (plugin.onCooldown(p, "cyan")) return;
        plugin.setCd(p, "cyan", c.getInt("cyan.cooldown-seconds", 14));

        int slowTicks = c.getInt("cyan.slow-seconds", 4) * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1, false, true, true));
        Vector pull = p.getLocation().toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() > 0.0001) {
            pull.normalize().multiply(c.getDouble("cyan.pull-strength", 1.0)).setY(0.3);
            target.setVelocity(pull);
        }
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1.3f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || target.isDead() || t >= 20) {
                    cancel();
                    return;
                }
                Location a = p.getEyeLocation().add(0, -0.3, 0);
                Location b = target.getBoundingBox().getCenter().toLocation(target.getWorld());
                Vector line = b.toVector().subtract(a.toVector());
                double len = line.length();
                if (len > 0.1) {
                    Vector step = line.clone().normalize().multiply(0.5);
                    Location cur = a.clone();
                    Particle.DustOptions dust = new Particle.DustOptions(Color.AQUA, 1.0f);
                    for (double d = 0; d < len; d += 0.5) {
                        cur.getWorld().spawnParticle(Particle.DUST, cur, 1, 0, 0, 0, 0, dust);
                        cur.add(step);
                    }
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ---------- Đen (ẩn): Căm Hận ----------
    private void castHate(Player p) {
        if (plugin.onCooldown(p, "hate")) return;
        FileConfiguration c = cfg();
        double dmg = c.getDouble("hate.damage", 10.0);
        double radius = c.getDouble("hate.radius", 7.0);
        int witherTicks = c.getInt("hate.wither-seconds", 5) * 20;
        int witherLvl = Math.max(0, c.getInt("hate.wither-level", 1));
        double lifesteal = c.getDouble("hate.lifesteal", 0.75);
        int buffTicks = c.getInt("hate.buff-seconds", 6) * 20;
        World w = p.getWorld();
        Location center = p.getLocation().add(0, 1, 0);

        double total = 0;
        hateBlast = true;
        try {
            for (LivingEntity le : p.getNearbyLivingEntities(radius)) {
                if (!Util.canTarget(p, le)) continue;
                le.damage(dmg, p);
                le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherTicks, witherLvl, false, true, true));
                total += dmg;
            }
        } finally {
            hateBlast = false;
        }
        if (total > 0) {
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + total * lifesteal));
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, buffTicks, 1, true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, buffTicks, 1, true, false, true));
        plugin.setCd(p, "hate", c.getInt("hate.cooldown-seconds", 15));

        Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.8f);
        for (int i = 0; i < 48; i++) {
            double a = i * (Math.PI * 2 / 48);
            for (double r = 1.0; r <= radius; r += 1.0) {
                w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r),
                        1, 0.05, 0.3, 0.05, 0, black);
            }
        }
        w.spawnParticle(Particle.SMOKE, center, 60, radius / 3, 0.6, radius / 3, 0.02);
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.6f);
        p.sendActionBar(Component.text("CĂM HẬN!", NamedTextColor.DARK_GRAY));
    }
}
