package bm.b0b0b0.SoulGolem.config;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class CraftLegacyMigration {

    private static final Pattern CRAFT_KEY = Pattern.compile("^\\s+craft\\s*:", Pattern.MULTILINE);
    private static final Pattern CRAFTING_KEY = Pattern.compile("^crafting\\s*:", Pattern.MULTILINE);
    private static final SerializerConfig SERIALIZER_CONFIG = new SerializerConfig.Builder().build();

    private CraftLegacyMigration() {
    }

    public static void migrate(GolemSettings golems, Path golemsPath, Path configPath) {
        if (!Files.exists(configPath)) {
            return;
        }
        String golemsRaw = "";
        if (Files.exists(golemsPath)) {
            try {
                golemsRaw = Files.readString(golemsPath);
            } catch (IOException exception) {
                return;
            }
            if (CRAFT_KEY.matcher(golemsRaw).find()) {
                return;
            }
        }
        String configRaw;
        try {
            configRaw = Files.readString(configPath);
        } catch (IOException exception) {
            return;
        }
        if (!CRAFTING_KEY.matcher(configRaw).find()) {
            return;
        }

        LegacyConfig legacy = new LegacyConfig();
        legacy.load(configPath);
        if (legacy.crafting == null) {
            return;
        }
        boolean master = legacy.crafting.enabled;
        copy(golems.farmer.craft, legacy.crafting.farmer, master);
        copy(golems.miner.craft, legacy.crafting.miner, master);
        golems.reload(golemsPath);
    }

    private static void copy(GolemSettings.StatueCraft target, LegacyCraft source, boolean masterEnabled) {
        if (source == null) {
            target.enabled = masterEnabled;
            return;
        }
        target.enabled = masterEnabled && source.enabled;
        if (source.shape != null && !source.shape.isEmpty()) {
            target.shape = new ArrayList<>(source.shape);
        }
        if (source.ingredients != null && !source.ingredients.isEmpty()) {
            target.ingredients = new ArrayList<>(source.ingredients);
        }
    }

    private static final class LegacyConfig extends YamlSerializable {
        public LegacyCrafting crafting = new LegacyCrafting();

        private LegacyConfig() {
            super(SERIALIZER_CONFIG);
        }
    }

    private static final class LegacyCrafting {
        public boolean enabled = true;
        public LegacyCraft farmer = new LegacyCraft();
        public LegacyCraft miner = new LegacyCraft();
    }

    private static final class LegacyCraft {
        public boolean enabled = true;
        public List<String> shape = new ArrayList<>();
        public List<String> ingredients = new ArrayList<>();
    }
}
