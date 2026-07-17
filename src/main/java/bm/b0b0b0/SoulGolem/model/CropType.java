package bm.b0b0b0.SoulGolem.model;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum CropType {
    WHEAT(Material.WHEAT_SEEDS, Material.WHEAT, true),
    CARROT(Material.CARROT, Material.CARROTS, false),
    POTATO(Material.POTATO, Material.POTATOES, false);

    private final Material seed;
    private final Material crop;
    private final boolean seedDropSeparate;

    CropType(Material seed, Material crop, boolean seedDropSeparate) {
        this.seed = seed;
        this.crop = crop;
        this.seedDropSeparate = seedDropSeparate;
    }

    public Material seed() {
        return this.seed;
    }

    public Material crop() {
        return this.crop;
    }

    public boolean seedDropSeparate() {
        return this.seedDropSeparate;
    }

    public static CropType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return WHEAT;
        }
        try {
            return CropType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static CropType bySeed(Material material) {
        if (material == null) {
            return null;
        }
        for (CropType type : values()) {
            if (type.seed == material) {
                return type;
            }
        }
        return null;
    }

    public static CropType byCrop(Material material) {
        if (material == null) {
            return null;
        }
        for (CropType type : values()) {
            if (type.crop == material) {
                return type;
            }
        }
        return null;
    }

    public ItemStack harvestProduct(int amount) {
        if (this == WHEAT) {
            return new ItemStack(Material.WHEAT, Math.max(1, amount));
        }
        return new ItemStack(this.seed, Math.max(1, amount));
    }
}
