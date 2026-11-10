package com.racesoul;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public enum Soul {
    RED("red", "Quyết Tâm", NamedTextColor.RED, Material.RED_DYE),
    ORANGE("orange", "Dũng Cảm", NamedTextColor.GOLD, Material.ORANGE_DYE),
    YELLOW("yellow", "Công Lý", NamedTextColor.YELLOW, Material.YELLOW_DYE),
    GREEN("green", "Tử Tế", NamedTextColor.GREEN, Material.LIME_DYE),
    BLUE("blue", "Chính Trực", NamedTextColor.BLUE, Material.BLUE_DYE),
    CYAN("cyan", "Kiên Nhẫn", NamedTextColor.AQUA, Material.CYAN_DYE),
    PURPLE("purple", "Kiên Trì", NamedTextColor.DARK_PURPLE, Material.PURPLE_DYE),
    /** Linh hồn ẩn: không bao giờ random trúng, phải được mở khoá mới dùng được. */
    HATE("hate", "Căm Hận", NamedTextColor.DARK_GRAY, Material.BLACK_DYE, true);

    public final String id;
    public final String title;
    public final NamedTextColor color;
    public final Material icon;
    /** true = linh hồn ẩn (không nằm trong random, cần mở khoá). */
    public final boolean hidden;

    Soul(String id, String title, NamedTextColor color, Material icon) {
        this(id, title, color, icon, false);
    }

    Soul(String id, String title, NamedTextColor color, Material icon, boolean hidden) {
        this.id = id;
        this.title = title;
        this.color = color;
        this.icon = icon;
        this.hidden = hidden;
    }

    /** Random 1 linh hồn thường (không bao giờ ra linh hồn ẩn). */
    public static Soul random() {
        List<Soul> pool = new java.util.ArrayList<>();
        for (Soul s : values()) if (!s.hidden) pool.add(s);
        return pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public static Soul byId(String id) {
        for (Soul s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    /** Mô tả kỹ năng, lấy số liệu từ config. */
    public List<String> describe(FileConfiguration c) {
        switch (this) {
            case RED:
                return List.of(
                        "SAVE / LOAD: Shift + chuột phải (tay không)",
                        "để lưu vị trí và máu. Bấm lại trong " + c.getInt("red.save-window-seconds", 60) + "s",
                        "để LOAD quay về đúng chỗ và máu đã lưu.",
                        "Hồi chiêu " + c.getInt("red.cooldown-seconds", 300) + " giây.");
            case ORANGE:
                return List.of(
                        "Shift + chuột phải (tay không): nhát chém cam",
                        "tự dò mục tiêu rồi nổ (" + (c.getDouble("orange.damage", 8.0) / 2.0) + " tim,",
                        "bán kính " + c.getDouble("orange.explode-radius", 3.0) + "). Hồi chiêu "
                                + c.getInt("orange.cooldown-seconds", 10) + " giây.");
            case YELLOW:
                return List.of(
                        "Shift + chuột phải (tay không):",
                        "chùm năng lượng vàng " + (c.getDouble("yellow.damage", 8.0) / 2.0) + " tim,",
                        "tầm " + c.getInt("yellow.range", 30) + " block. Hồi chiêu "
                                + c.getInt("yellow.cooldown-seconds", 8) + " giây.");
            case GREEN:
                return List.of(
                        "Khiên Tử Tế: Shift + chuột phải (tay không),",
                        "chặn mọi sát thương " + c.getInt("green.shield-seconds", 3) + " giây",
                        "và hồi máu đồng minh gần. Hồi chiêu " + c.getInt("green.cooldown-seconds", 25) + " giây.");
            case BLUE:
                return List.of(
                        "Trọng lực: Shift + chuột phải (tay không).",
                        "Nhìn vào địch: ép xuống đất, không nhảy được "
                                + c.getInt("blue.pin-seconds", 3) + " giây.",
                        "Không có địch: tự phóng người. Miễn sát thương rơi.",
                        "Hồi chiêu " + c.getInt("blue.cooldown-seconds", 12) + " giây.");
            case CYAN:
                return List.of(
                        "Sợi tơ: Shift + chuột phải (tay không),",
                        "gắn vào mục tiêu, kéo lại gần và làm chậm "
                                + c.getInt("cyan.slow-seconds", 4) + " giây.",
                        "Hồi chiêu " + c.getInt("cyan.cooldown-seconds", 14) + " giây.");
            case PURPLE:
                return List.of(
                        "Bất Khuất (bị động): mỗi lần trúng đòn +1 tầng",
                        "(tối đa " + c.getInt("purple.max-stacks", 5) + "), hết sau "
                                + c.getInt("purple.stack-seconds", 10) + "s không bị đánh.",
                        "Càng nhiều tầng càng được Resistance và Strength.");
            case HATE:
                return List.of(
                        "Linh hồn ẩn. Bị động: giảm "
                                + (int) (c.getDouble("hate.damage-reduction", 0.25) * 100) + "% sát thương nhận,",
                        "đánh thường hút " + (int) (c.getDouble("hate.melee-lifesteal", 0.2) * 100) + "% máu.",
                        "Shift + chuột phải (tay không): bùng nổ năng lượng đen,",
                        "gây " + (c.getDouble("hate.damage", 10.0) / 2.0) + " tim trong bán kính "
                                + c.getDouble("hate.radius", 7.0) + ", Wither "
                                + (c.getInt("hate.wither-level", 1) + 1) + " " + c.getInt("hate.wither-seconds", 5) + "s,",
                        "hút " + (int) (c.getDouble("hate.lifesteal", 0.75) * 100) + "% sát thương gây ra",
                        "và nhận Strength II + Speed II " + c.getInt("hate.buff-seconds", 6) + "s.",
                        "Hồi chiêu " + c.getInt("hate.cooldown-seconds", 15) + " giây.",
                        "Dùng /linhhon hate để chuyển qua lại với linh hồn gốc.");
            default:
                return List.of();
        }
    }
}
