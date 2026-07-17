package bm.b0b0b0.SoulGolem.config;

import bm.b0b0b0.SoulGolem.config.settings.GuiConfirmSettings;
import bm.b0b0b0.SoulGolem.config.settings.GuiGeneralSettings;
import bm.b0b0b0.SoulGolem.config.settings.GuiListSettings;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import java.nio.file.Path;
import org.bukkit.plugin.Plugin;

public final class PluginConfig {

    private final Settings settings;
    private final GuiGeneralSettings guiGeneral;
    private final GuiListSettings guiList;
    private final GuiConfirmSettings guiConfirm;
    private final Path dataFolder;

    public PluginConfig(
            Settings settings,
            GuiGeneralSettings guiGeneral,
            GuiListSettings guiList,
            GuiConfirmSettings guiConfirm,
            Path dataFolder
    ) {
        this.settings = settings;
        this.guiGeneral = guiGeneral;
        this.guiList = guiList;
        this.guiConfirm = guiConfirm;
        this.dataFolder = dataFolder;
    }

    public Settings settings() {
        return this.settings;
    }

    public GuiGeneralSettings guiGeneral() {
        return this.guiGeneral;
    }

    public GuiListSettings guiList() {
        return this.guiList;
    }

    public GuiConfirmSettings guiConfirm() {
        return this.guiConfirm;
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

    public Path guiListPath() {
        return this.dataFolder.resolve("gui").resolve("list.yml");
    }

    public Path guiConfirmPath() {
        return this.dataFolder.resolve("gui").resolve("confirm.yml");
    }

    public static PluginConfig load(Plugin plugin) {
        Path dataFolder = plugin.getDataFolder().toPath();
        Settings settings = new Settings();
        settings.reload(dataFolder.resolve("config.yml"));

        Path guiDir = dataFolder.resolve("gui");
        GuiGeneralSettings guiGeneral = new GuiGeneralSettings();
        guiGeneral.reload(guiDir.resolve("general.yml"));

        GuiListSettings guiList = new GuiListSettings();
        guiList.reload(guiDir.resolve("list.yml"));

        GuiConfirmSettings guiConfirm = new GuiConfirmSettings();
        guiConfirm.reload(guiDir.resolve("confirm.yml"));

        return new PluginConfig(settings, guiGeneral, guiList, guiConfirm, dataFolder);
    }

    public void reload() {
        this.settings.reload(configPath());
        this.guiGeneral.reload(guiGeneralPath());
        this.guiList.reload(guiListPath());
        this.guiConfirm.reload(guiConfirmPath());
    }
}
