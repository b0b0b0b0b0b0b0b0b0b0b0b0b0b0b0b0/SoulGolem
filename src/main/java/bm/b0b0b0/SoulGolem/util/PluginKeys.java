package bm.b0b0b0.SoulGolem.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class PluginKeys {

    private final NamespacedKey statue;
    private final NamespacedKey statueType;
    private final NamespacedKey golemId;
    private final NamespacedKey owner;
    private final NamespacedKey chestGolemId;
    private final NamespacedKey craftGolemId;

    public PluginKeys(Plugin plugin) {
        this.statue = new NamespacedKey(plugin, "statue");
        this.statueType = new NamespacedKey(plugin, "statue_type");
        this.golemId = new NamespacedKey(plugin, "golem_id");
        this.owner = new NamespacedKey(plugin, "owner");
        this.chestGolemId = new NamespacedKey(plugin, "chest_golem_id");
        this.craftGolemId = new NamespacedKey(plugin, "craft_golem_id");
    }

    public NamespacedKey statue() {
        return this.statue;
    }

    public NamespacedKey statueType() {
        return this.statueType;
    }

    public NamespacedKey golemId() {
        return this.golemId;
    }

    public NamespacedKey owner() {
        return this.owner;
    }

    public NamespacedKey chestGolemId() {
        return this.chestGolemId;
    }

    public NamespacedKey craftGolemId() {
        return this.craftGolemId;
    }
}
