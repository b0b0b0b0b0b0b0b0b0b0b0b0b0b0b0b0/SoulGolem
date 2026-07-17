package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.PluginConfig;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;

public final class OreTableService {

    private final PluginConfig config;
    private Set<Material> transformable = EnumSet.noneOf(Material.class);
    private List<WeightedOre> ores = List.of();
    private int totalWeight;

    public OreTableService(PluginConfig config) {
        this.config = config;
        reload();
    }

    public void reload() {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : this.config.settings().transformableBlocks) {
            Material material = Material.matchMaterial(name);
            if (material != null && material.isBlock()) {
                materials.add(material);
            }
        }
        this.transformable = materials;

        List<WeightedOre> weighted = new ArrayList<>();
        int sum = 0;
        for (Settings.OreWeight entry : this.config.settings().oreTable) {
            Material material = Material.matchMaterial(entry.material);
            if (material == null || !material.isBlock() || entry.weight <= 0) {
                continue;
            }
            weighted.add(new WeightedOre(material, entry.weight));
            sum += entry.weight;
        }
        this.ores = List.copyOf(weighted);
        this.totalWeight = sum;
    }

    public boolean isTransformable(Material material) {
        return this.transformable.contains(material);
    }

    public boolean isOre(Material material) {
        for (WeightedOre ore : this.ores) {
            if (ore.material == material) {
                return true;
            }
        }
        return false;
    }

    public Material rollOre() {
        if (this.ores.isEmpty() || this.totalWeight <= 0) {
            return Material.IRON_ORE;
        }
        int roll = ThreadLocalRandom.current().nextInt(this.totalWeight);
        int cursor = 0;
        for (WeightedOre ore : this.ores) {
            cursor += ore.weight;
            if (roll < cursor) {
                return ore.material;
            }
        }
        return this.ores.get(this.ores.size() - 1).material;
    }

    private record WeightedOre(Material material, int weight) {
    }
}
