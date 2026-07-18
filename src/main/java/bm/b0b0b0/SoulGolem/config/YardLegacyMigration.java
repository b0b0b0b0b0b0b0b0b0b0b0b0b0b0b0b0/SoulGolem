package bm.b0b0b0.SoulGolem.config;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class YardLegacyMigration {

    private static final Pattern YARD_KEY = Pattern.compile("^yard\\s*:", Pattern.MULTILINE);
    private static final SerializerConfig SERIALIZER_CONFIG = new SerializerConfig.Builder().build();

    private YardLegacyMigration() {
    }

    public static void migrate(GolemSettings golems, Path golemsPath) {
        if (!Files.exists(golemsPath)) {
            return;
        }
        String raw;
        try {
            raw = Files.readString(golemsPath);
        } catch (IOException exception) {
            return;
        }
        if (YARD_KEY.matcher(raw).find()) {
            return;
        }

        LegacySnapshot legacy = new LegacySnapshot();
        legacy.load(golemsPath);
        copyYard(golems.yard, legacy, raw);
        golems.reload(golemsPath);
    }

    private static void copyYard(GolemSettings.Yard yard, LegacySnapshot legacy, String raw) {
        Role farmer = legacy.farmer;
        Role miner = legacy.miner;

        yard.collectGroundLoot = pickBool(raw, "farmer", "collectGroundLoot", farmer.collectGroundLoot,
                "miner", "collectGroundLoot", miner.collectGroundLoot);
        yard.placeTorches = pickBool(raw, "farmer", "placeTorches", farmer.placeTorches,
                "miner", "placeTorches", miner.placeTorches);
        yard.maxTorches = pickInt(raw, "farmer", "maxTorches", farmer.maxTorches,
                "miner", "maxTorches", miner.maxTorches);
        yard.torchesPerTrip = pickInt(raw, "farmer", "torchesPerTrip", farmer.torchesPerTrip,
                "miner", "torchesPerTrip", miner.torchesPerTrip);
        yard.torchMaterial = pickString(raw, "farmer", "torchMaterial", farmer.torchMaterial,
                "miner", "torchMaterial", miner.torchMaterial);
        yard.placeSeat = pickBool(raw, "farmer", "placeSeat", farmer.placeSeat,
                "miner", "placeSeat", miner.placeSeat);
        yard.clearBorder = pickBool(raw, "farmer", "clearBorder", farmer.clearBorder,
                "miner", "clearArea", miner.clearArea);
        yard.placeFence = pickBool(raw, "farmer", "placeFence", farmer.placeFence,
                "miner", "placeFence", miner.placeFence);
        yard.fenceMaterial = pickString(raw, "farmer", "fenceMaterial", farmer.fenceMaterial,
                "miner", "fenceMaterial", miner.fenceMaterial);
        yard.gateMaterial = pickString(raw, "farmer", "gateMaterial", farmer.gateMaterial,
                "miner", "gateMaterial", miner.gateMaterial);
        yard.placeGatePath = pickBool(raw, "farmer", "placeGatePath", farmer.placeGatePath,
                "miner", "placeGatePath", miner.placeGatePath);
        yard.gatePathMaterial = pickString(raw, "farmer", "gatePathMaterial", farmer.gatePathMaterial,
                "miner", "gatePathMaterial", miner.gatePathMaterial);
        yard.fencesPerTrip = pickInt(raw, "farmer", "fencesPerTrip", farmer.fencesPerTrip,
                "miner", "fencesPerTrip", miner.fencesPerTrip);
        yard.gateAutoClose = pickBool(raw, "farmer", "gateAutoClose", farmer.gateAutoClose,
                "miner", "gateAutoClose", miner.gateAutoClose);
        yard.gateCloseDelayMs = pickLong(raw, "farmer", "gateCloseDelayMs", farmer.gateCloseDelayMs,
                "miner", "gateCloseDelayMs", miner.gateCloseDelayMs);
        yard.rainShelter = pickBool(raw, "farmer", "rainShelter", farmer.rainShelter,
                "miner", "rainShelter", miner.rainShelter);
    }

    private static boolean pickBool(
            String raw,
            String farmerSection,
            String farmerKey,
            boolean farmerValue,
            String minerSection,
            String minerKey,
            boolean minerValue
    ) {
        if (sectionHasKey(raw, farmerSection, farmerKey)) {
            return farmerValue;
        }
        if (sectionHasKey(raw, minerSection, minerKey)) {
            return minerValue;
        }
        return farmerValue;
    }

    private static int pickInt(
            String raw,
            String farmerSection,
            String farmerKey,
            int farmerValue,
            String minerSection,
            String minerKey,
            int minerValue
    ) {
        if (sectionHasKey(raw, farmerSection, farmerKey)) {
            return farmerValue;
        }
        if (sectionHasKey(raw, minerSection, minerKey)) {
            return minerValue;
        }
        return farmerValue;
    }

    private static long pickLong(
            String raw,
            String farmerSection,
            String farmerKey,
            long farmerValue,
            String minerSection,
            String minerKey,
            long minerValue
    ) {
        if (sectionHasKey(raw, farmerSection, farmerKey)) {
            return farmerValue;
        }
        if (sectionHasKey(raw, minerSection, minerKey)) {
            return minerValue;
        }
        return farmerValue;
    }

    private static String pickString(
            String raw,
            String farmerSection,
            String farmerKey,
            String farmerValue,
            String minerSection,
            String minerKey,
            String minerValue
    ) {
        if (sectionHasKey(raw, farmerSection, farmerKey)) {
            return farmerValue;
        }
        if (sectionHasKey(raw, minerSection, minerKey)) {
            return minerValue;
        }
        return farmerValue;
    }

    private static boolean sectionHasKey(String raw, String section, String key) {
        String block = sectionBlock(raw, section);
        if (block == null) {
            return false;
        }
        return Pattern.compile("(?m)^\\s+" + Pattern.quote(key) + "\\s*:").matcher(block).find();
    }

    private static String sectionBlock(String raw, String section) {
        Pattern sectionStart = Pattern.compile("(?m)^" + Pattern.quote(section) + "\\s*:\\s*$");
        var startMatcher = sectionStart.matcher(raw);
        if (!startMatcher.find()) {
            return null;
        }
        int start = startMatcher.start();
        Pattern nextSection = Pattern.compile("(?m)^\\S");
        var nextMatcher = nextSection.matcher(raw);
        int end = raw.length();
        if (nextMatcher.find(start + 1)) {
            while (nextMatcher.find()) {
                int index = nextMatcher.start();
                if (index <= start) {
                    continue;
                }
                String line = raw.substring(index, raw.indexOf('\n', index) >= 0 ? raw.indexOf('\n', index) : raw.length());
                if (!line.startsWith(" ") && !line.startsWith("\t")) {
                    end = index;
                    break;
                }
            }
        }
        return raw.substring(start, end);
    }

    private static final class LegacySnapshot extends YamlSerializable {

        public Role farmer = new Role();
        public Role miner = new Role();

        LegacySnapshot() {
            super(SERIALIZER_CONFIG);
        }
    }

    public static final class Role {
        public boolean collectGroundLoot = true;
        public boolean placeTorches = true;
        public int maxTorches = 4;
        public int torchesPerTrip = 2;
        public String torchMaterial = "TORCH";
        public boolean placeSeat = true;
        public boolean clearBorder = true;
        public boolean clearArea = true;
        public boolean placeFence = true;
        public String fenceMaterial = "OAK_FENCE";
        public String gateMaterial = "OAK_FENCE_GATE";
        public boolean placeGatePath = true;
        public String gatePathMaterial = "DIRT_PATH";
        public int fencesPerTrip = 8;
        public boolean gateAutoClose = true;
        public long gateCloseDelayMs = 2000L;
        public boolean rainShelter = true;
    }
}
