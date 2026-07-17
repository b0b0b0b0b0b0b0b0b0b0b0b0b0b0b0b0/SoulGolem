package bm.b0b0b0.SoulGolem.model;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum CropType {
    WHEAT(Material.WHEAT_SEEDS, Material.WHEAT, true, Kind.SIMPLE, null),
    CARROT(Material.CARROT, Material.CARROTS, false, Kind.SIMPLE, null),
    POTATO(Material.POTATO, Material.POTATOES, false, Kind.SIMPLE, null),
    BEETROOT(Material.BEETROOT_SEEDS, Material.BEETROOTS, true, Kind.SIMPLE, null),
    PUMPKIN(Material.PUMPKIN_SEEDS, Material.PUMPKIN_STEM, true, Kind.STEM, Material.PUMPKIN),
    MELON(Material.MELON_SEEDS, Material.MELON_STEM, true, Kind.STEM, Material.MELON);

    public enum Kind {
        SIMPLE,
        STEM
    }

    private final Material seed;
    private final Material crop;
    private final boolean seedDropSeparate;
    private final Kind kind;
    private final Material fruit;

    CropType(Material seed, Material crop, boolean seedDropSeparate, Kind kind, Material fruit) {
        this.seed = seed;
        this.crop = crop;
        this.seedDropSeparate = seedDropSeparate;
        this.kind = kind;
        this.fruit = fruit;
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

    public Kind kind() {
        return this.kind;
    }

    public boolean isStemCrop() {
        return this.kind == Kind.STEM;
    }

    public Material fruit() {
        return this.fruit;
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
        if (material == Material.PUMPKIN
                || material == Material.PUMPKIN_STEM
                || material == Material.ATTACHED_PUMPKIN_STEM) {
            return PUMPKIN;
        }
        if (material == Material.MELON
                || material == Material.MELON_STEM
                || material == Material.ATTACHED_MELON_STEM) {
            return MELON;
        }
        for (CropType type : values()) {
            if (type.crop == material) {
                return type;
            }
        }
        return null;
    }

    public boolean isFruit(Material material) {
        return this.fruit != null && this.fruit == material;
    }

    public boolean isStemBlock(Material material) {
        if (!isStemCrop() || material == null) {
            return false;
        }
        if (material == this.crop) {
            return true;
        }
        if (this == PUMPKIN) {
            return material == Material.ATTACHED_PUMPKIN_STEM;
        }
        if (this == MELON) {
            return material == Material.ATTACHED_MELON_STEM;
        }
        return false;
    }

    public ItemStack harvestProduct(int amount) {
        if (this == WHEAT) {
            return new ItemStack(Material.WHEAT, Math.max(1, amount));
        }
        if (this == BEETROOT) {
            return new ItemStack(Material.BEETROOT, Math.max(1, amount));
        }
        if (this == PUMPKIN) {
            return new ItemStack(Material.PUMPKIN, Math.max(1, amount));
        }
        if (this == MELON) {
            return new ItemStack(Material.MELON_SLICE, Math.max(1, amount));
        }
        return new ItemStack(this.seed, Math.max(1, amount));
    }
}
