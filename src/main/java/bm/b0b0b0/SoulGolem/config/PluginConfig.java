package bm.b0b0b0.SoulGolem.config;

import bm.b0b0b0.SoulGolem.config.settings.GuiGeneralSettings;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import java.nio.file.Path;
import org.bukkit.plugin.Plugin;

public final class PluginConfig {

    private final Settings settings;
    private final GuiGeneralSettings guiGeneral;
    private final Path dataFolder;

    public PluginConfig(Settings settings, GuiGeneralSettings guiGeneral, Path dataFolder) {
        this.settings = settings;
        this.guiGeneral = guiGeneral;
        this.dataFolder = dataFolder;
    }

    public Settings settings() {
        return this.settings;
    }

    public GuiGeneralSettings guiGeneral() {
        return this.guiGeneral;
    }

    public Path dataFolder() {
        return this.dataFolder;
    }

    public Path configPath() {
        return this.dataFolder.resolve("config.yml");
    }

    public Path guiGeneralPath() {
        return this.dataFolder.resolve("gui").resolve("general.yml");
    }

    public static PluginConfig load(Plugin plugin) {
        Path dataFolder = plugin.getDataFolder().toPath();
        Settings settings = new Settings();
        settings.reload(dataFolder.resolve("config.yml"));

        Path guiDir = dataFolder.resolve("gui");
        GuiGeneralSettings guiGeneral = new GuiGeneralSettings();
        guiGeneral.reload(guiDir.resolve("general.yml"));

        return new PluginConfig(settings, guiGeneral, dataFolder);
    }

    public void reload() {
        this.settings.reload(configPath());
        this.guiGeneral.reload(guiGeneralPath());
    }
}
