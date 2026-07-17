package bm.b0b0b0.SoulGolem.config;

import bm.b0b0b0.SoulGolem.message.MessageService;
import org.bukkit.plugin.Plugin;

public final class ConfigurationLoader {

    private final Plugin plugin;
    private PluginConfig config;
    private MessageService messages;

    public ConfigurationLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!this.plugin.getDataFolder().exists() && !this.plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data folder");
        }
        this.config = PluginConfig.load(this.plugin);
        this.messages = MessageService.load(this.plugin, this.config.settings().language);
    }

    public void reload() {
        this.config.reload();
        this.messages = MessageService.load(this.plugin, this.config.settings().language);
    }

    public PluginConfig config() {
        return this.config;
    }

    public MessageService messages() {
        return this.messages;
    }
}
