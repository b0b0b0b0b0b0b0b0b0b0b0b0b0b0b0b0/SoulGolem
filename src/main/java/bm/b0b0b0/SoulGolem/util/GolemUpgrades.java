package bm.b0b0b0.SoulGolem.util;

import org.bukkit.Material;

public final class GolemUpgrades {

    private static final String PICKAXE_KEY = "\"pickaxe\":\"";

    private GolemUpgrades() {
    }

    public static Material readPickaxe(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        int start = json.indexOf(PICKAXE_KEY);
        if (start < 0) {
            return null;
        }
        start += PICKAXE_KEY.length();
        int end = json.indexOf('"', start);
        if (end <= start) {
            return null;
        }
        String raw = json.substring(start, end);
        if (raw.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(raw);
        if (material == null || !material.name().endsWith("_PICKAXE")) {
            return null;
        }
        return material;
    }

    public static String writePickaxe(String existing, Material pickaxe) {
        String value = pickaxe == null ? "" : pickaxe.name();
        if (existing == null || existing.isBlank() || !existing.contains(PICKAXE_KEY)) {
            return "{\"pickaxe\":\"" + value + "\"}";
        }
        int start = existing.indexOf(PICKAXE_KEY) + PICKAXE_KEY.length();
        int end = existing.indexOf('"', start);
        if (end < start) {
            return "{\"pickaxe\":\"" + value + "\"}";
        }
        return existing.substring(0, start) + value + existing.substring(end);
    }
}
