package bm.b0b0b0.SoulGolem.config.settings;

import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;
import bm.b0b0b0.SoulGolem.model.CropType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

public final class Settings extends YamlSerializable {

    private static final SerializerConfig SERIALIZER_CONFIG = new SerializerConfig.Builder().build();

    public Settings() {
        super(SERIALIZER_CONFIG);
    }

    @Comment({
            @CommentValue("Language file name without extension (lang/<name>.yml)")
    })
    public String language = "ru";

    @Comment({
            @CommentValue("Log seat approach/teleport to console (sit debug)")
    })
    public boolean debugSeat = true;

    @NewLine
    @Comment({
            @CommentValue("Maximum active golems per player (permission overrides possible later)")
    })
    public int maxGolemsPerPlayer = 3;

    @Comment({
            @CommentValue("Work radius in blocks from golem (square half-size)")
    })
    public int workRadius = 3;

    @Comment({
            @CommentValue("Ticks between miner actions for one golem")
    })
    public long workIntervalTicks = 40L;

    @Comment({
            @CommentValue("How many golems to process per coordinator tick")
    })
    public int golemsPerCoordinatorTick = 2;

    @Comment({
            @CommentValue("Coordinator global tick period")
    })
    public long coordinatorPeriodTicks = 10L;

    @Comment({
            @CommentValue("Pathfinder speed multiplier (1.0 = vanilla copper golem walk). Mood does not affect walk.")
    })
    public double walkSpeed = 1.0D;

    @Comment({
            @CommentValue("Ticks spent mining one block (animation)")
    })
    public long mineDurationTicks = 20L;

    @Comment({
            @CommentValue("Fallback standing rest after deposit if no bench (ticks). Prefer miner.standingRestTicks for miners")
    })
    public long restDurationTicks = 100L;

    @Comment({
            @CommentValue("Pickaxe in miner hand (MATERIAL)")
    })
    public String pickaxeMaterial = "COPPER_PICKAXE";

    @Comment({
            @CommentValue("Hoe in farmer hand (MATERIAL)")
    })
    public String hoeMaterial = "COPPER_HOE";

    @Comment({
            @CommentValue("Shovel in farmer hand while clearing border (MATERIAL)")
    })
    public String shovelMaterial = "COPPER_SHOVEL";

    @NewLine
    @Comment({
            @CommentValue("Draw wool (or other) border around work area")
    })
    public boolean borderEnabled = true;

    @Comment({
            @CommentValue("Border block material")
    })
    public String borderMaterial = "PURPLE_WOOL";

    @Comment({
            @CommentValue("How many ore blocks can exist in the work area at once")
    })
    public int maxActiveOres = 4;

    @NewLine
    @Comment({
            @CommentValue("Only transform blocks that see sky (surface)")
    })
    public boolean requireSkyAccess = false;

    @Comment({
            @CommentValue("Allow transforming blocks underground")
    })
    public boolean allowUnderground = false;

    @Comment({
            @CommentValue("Allow transforming blocks in air (Y above solid surface)")
    })
    public boolean allowAir = false;

    @NewLine
    @Comment({
            @CommentValue("Blocks where a statue can be placed to spawn a golem")
    })
    public List<String> placementBlocks = defaultPlacement();

    @Comment({
            @CommentValue("Blocks that can be turned into resource ores")
    })
    public List<String> transformableBlocks = defaultTransformable();

    @Comment({
            @CommentValue("Weighted ore table. Higher weight = more common")
    })
    public List<OreWeight> oreTable = defaultOreTable();

    @NewLine
    @Comment({
            @CommentValue("Activation: experience levels required (0 = free)")
    })
    public int activationXpLevels = 0;

    @Comment({
            @CommentValue("Activation item costs as MATERIAL:amount (empty = none)")
    })
    public List<String> activationItems = new ArrayList<>();

    @Comment({
            @CommentValue("Prevent spawning when work area overlaps another golem")
    })
    public boolean preventAreaOverlap = true;

    @Comment({
            @CommentValue("Extra gap (blocks) between golem areas: homes closer than radiusA + radiusB + this are rejected")
    })
    public int areaOverlapPadding = 3;

    @NewLine
    @Comment({
            @CommentValue("Base energy capacity")
    })
    public int energyCapacity = 1000;

    @Comment({
            @CommentValue("Energy drained per mined block")
    })
    public int energyPerMine = 8;

    @Comment({
            @CommentValue("Energy drained per farmer work action (till/plant/harvest/clear)")
    })
    public int energyPerFarmAction = 4;

    @Comment({
            @CommentValue("Feed item taken from chest to restore energy")
    })
    public String energyFeedItem = "COPPER_INGOT";

    @Comment({
            @CommentValue("Energy restored per feed item")
    })
    public int energyPerIngot = 200;

    @Comment({
            @CommentValue("When energy is at or below this, golem seeks copper from chest")
    })
    public int energyHungryThreshold = 150;

    @NewLine
    @Comment({
            @CommentValue("Mood: min border torches for +1 mood point")
    })
    public int minTorchesForMood = 2;

    @Comment({
            @CommentValue("Work speed multiplier at mood 0 / 1 / 2 (seat + torches)")
    })
    public List<Double> moodWorkSpeed = defaultMoodWork();

    @Comment({
            @CommentValue("Rest duration multiplier at mood 0 / 1 / 2")
    })
    public List<Double> moodRestMultiplier = defaultMoodRest();

    @NewLine
    @Comment({
            @CommentValue("Level multipliers radius/speed/drop (index 0 unused, 1-5 used)")
    })
    public List<LevelStats> levels = defaultLevels();

    @NewLine
    public Permissions permissions = new Permissions();

    @NewLine
    public Database database = new Database();

    @NewLine
    public Visuals visuals = new Visuals();

    @NewLine
    public Farmer farmer = new Farmer();

    @NewLine
    public Miner miner = new Miner();

    @NewLine
    @Comment({
            @CommentValue("Area defense: sword/axe from chest, ground loot, or right-click give; stay inside wool border")
    })
    public Combat combat = new Combat();

    public static final class Combat {
        @Comment({
                @CommentValue("Attack mobs inside the work area when armed with a sword or axe")
        })
        public boolean enabled = true;

        @Comment({
                @CommentValue("Milliseconds between swings")
        })
        public long attackCooldownMs = 650;

        public double damageWood = 3.0D;
        public double damageGold = 3.0D;
        public double damageStone = 4.0D;
        public double damageIron = 5.0D;
        public double damageDiamond = 6.0D;
        public double damageNetherite = 7.0D;

        @Comment({
                @CommentValue("Extra damage when using an axe")
        })
        public double axeBonus = 1.0D;
    }

    public static final class OreWeight {
        public String material = "IRON_ORE";
        public int weight = 10;

        public OreWeight() {
        }

        public OreWeight(String material, int weight) {
            this.material = material;
            this.weight = weight;
        }
    }

    public static final class LevelStats {
        public int level = 1;
        public double radiusMultiplier = 1.0D;
        public double speedMultiplier = 1.0D;
        public double rareDropMultiplier = 1.0D;

        public LevelStats() {
        }

        public LevelStats(int level, double radiusMultiplier, double speedMultiplier, double rareDropMultiplier) {
            this.level = level;
            this.radiusMultiplier = radiusMultiplier;
            this.speedMultiplier = speedMultiplier;
            this.rareDropMultiplier = rareDropMultiplier;
        }
    }

    public static final class Permissions {
        public String admin = "soulgolem.admin";
        public String use = "soulgolem.use";
        public String give = "soulgolem.give";
        public String reload = "soulgolem.reload";
        public String bypassLimit = "soulgolem.bypass.limit";
    }

    public static final class Database {
        @Comment({
                @CommentValue("sqlite or mysql")
        })
        public String type = "sqlite";

        public String sqliteFile = "data.db";

        public String mysqlHost = "localhost";
        public int mysqlPort = 3306;
        public String mysqlDatabase = "soulgolem";
        public String mysqlUsername = "root";
        public String mysqlPassword = "password";

        public int poolSize = 4;
        public long connectionTimeoutMs = 10000L;
    }

    public static final class Visuals {
        public boolean particles = true;
        public boolean sounds = true;
        public String mineParticle = "BLOCK";
        public String mineSound = "BLOCK_STONE_BREAK";
        public float soundVolume = 0.6F;
        public float soundPitch = 1.0F;

        @NewLine
        @Comment({
                @CommentValue("TextDisplay settings for golem nameplate and chest hologram")
        })
        public TextDisplays textDisplays = new TextDisplays();
    }

    public static final class TextDisplays {
        @Comment({
                @CommentValue("Golem nameplates and chest/craft TextDisplays")
        })
        public boolean enabled = true;

        @Comment({
                @CommentValue("If true, text is visible through blocks")
        })
        public boolean seeThrough = false;

        @Comment({
                @CommentValue("Drop shadow under text")
        })
        public boolean shadowed = true;

        @Comment({
                @CommentValue("Vanilla semi-transparent text background")
        })
        public boolean defaultBackground = false;

        @Comment({
                @CommentValue("CENTER, FIXED, VERTICAL, HORIZONTAL")
        })
        public String billboard = "CENTER";

        @Comment({
                @CommentValue("LEFT, CENTER, RIGHT")
        })
        public String alignment = "CENTER";

        @Comment({
                @CommentValue("Client view distance multiplier for the display (1.0 = default)")
        })
        public float viewRange = 0.6F;

        @Comment({
                @CommentValue("Max line width in pixels before wrapping")
        })
        public int lineWidth = 120;

        @Comment({
                @CommentValue("Text opacity 0-255, or -1 for default")
        })
        public int textOpacity = -1;

        @Comment({
                @CommentValue("Force full brightness (ignore local lighting)")
        })
        public boolean fullBright = true;

        @Comment({
                @CommentValue("Nameplate Y above passenger mount (top of golem + antenna)")
        })
        public float golemOffsetY = 0.55F;

        @Comment({
                @CommentValue("Golem nameplate scale")
        })
        public float golemScale = 0.8F;

        @Comment({
                @CommentValue("Chest hologram Y above block")
        })
        public float chestOffsetY = 1.15F;

        @Comment({
                @CommentValue("Chest hologram scale")
        })
        public float chestScale = 0.85F;
    }

    public static final class Farmer {
        @Comment({
                @CommentValue("Craft bread at crafting table when chest has 3+ wheat")
        })
        public boolean craftBread = true;

        @Comment({
                @CommentValue("Wander the farm while waiting for crops")
        })
        public boolean wanderWhileWaiting = true;

        @Comment({
                @CommentValue("Pick up item drops inside the work area (including border and outer fence ring)")
        })
        public boolean collectGroundLoot = true;

        @Comment({
                @CommentValue("Use bone meal from chest on immature wheat (vanilla applyBoneMeal)")
        })
        public boolean useBoneMeal = true;

        @Comment({
                @CommentValue("How many bone meal to take per fertilize trip")
        })
        public int boneMealPerTrip = 3;

        @Comment({
                @CommentValue("How many seeds to take per planting trip")
        })
        public int seedsPerTrip = 8;

        @Comment({
                @CommentValue("Place torches from chest on farm corners only (not around the bench)")
        })
        public boolean placeTorches = true;

        @Comment({
                @CommentValue("Maximum torches on the farm border")
        })
        public int maxTorches = 4;

        @Comment({
                @CommentValue("How many torches to take/place per trip")
        })
        public int torchesPerTrip = 2;

        @Comment({
                @CommentValue("Torch material to place")
        })
        public String torchMaterial = "TORCH";

        @Comment({
                @CommentValue("If chest has stairs, place one bench on the border and sit while waiting")
        })
        public boolean placeSeat = true;

        @Comment({
                @CommentValue("Clear dirt/blocks sitting on top of the border before placing seat/torches")
        })
        public boolean clearBorder = true;

        @Comment({
                @CommentValue("Place wooden fence ring outside the wool border (radius+1) from chest materials")
        })
        public boolean placeFence = true;

        @Comment({
                @CommentValue("Fence material for the outer ring")
        })
        public String fenceMaterial = "OAK_FENCE";

        @Comment({
                @CommentValue("Fence gate material (placed opposite the chest)")
        })
        public String gateMaterial = "OAK_FENCE_GATE";

        @Comment({
                @CommentValue("How many fence posts to take/place per trip")
        })
        public int fencesPerTrip = 8;

        @Comment({
                @CommentValue("Auto-close the outer fence gate if left open (lazy: wait, then walk and close)")
        })
        public boolean gateAutoClose = true;

        @Comment({
                @CommentValue("How long the gate may stay open before the golem goes to close it (ms)")
        })
        public long gateCloseDelayMs = 2000;

        @Comment({
                @CommentValue("Build a free wool rain shelter (Г): 1 above NPC (+3); corner = fence+3 wool or 4 wool if no fence yet")
        })
        public boolean rainShelter = true;

        @Comment({
                @CommentValue("Supported crops: WHEAT, CARROT, POTATO, BEETROOT, PUMPKIN, MELON"),
                @CommentValue("PUMPKIN/MELON plant on a checkerboard (stem / empty fruit pad) so fruit can grow")
        })
        public List<String> crops = defaultCrops();
    }

    public static final class Miner {
        @Comment({
                @CommentValue("Clear grass/junk on border and floor before mining")
        })
        public boolean clearArea = true;

        @Comment({
                @CommentValue("Pick up item drops inside the work area (including border and outer fence ring)")
        })
        public boolean collectGroundLoot = true;

        @Comment({
                @CommentValue("Take iron/diamond/netherite pickaxe from chest: 2/3/4 blocks per rest trip")
        })
        public boolean pickaxeUpgrades = true;

        @Comment({
                @CommentValue("Place torches from chest on mining area corners")
        })
        public boolean placeTorches = true;

        @Comment({
                @CommentValue("Maximum torches on the mining border")
        })
        public int maxTorches = 4;

        @Comment({
                @CommentValue("How many torches to take/place per trip")
        })
        public int torchesPerTrip = 2;

        @Comment({
                @CommentValue("Torch material to place")
        })
        public String torchMaterial = "TORCH";

        @Comment({
                @CommentValue("If chest has stairs, place one bench on the border (moves torch aside like farmer)")
        })
        public boolean placeSeat = true;

        @Comment({
                @CommentValue("Place wooden fence ring outside the wool border (radius+1) from chest materials")
        })
        public boolean placeFence = true;

        @Comment({
                @CommentValue("Fence material for the outer ring")
        })
        public String fenceMaterial = "OAK_FENCE";

        @Comment({
                @CommentValue("Fence gate material (placed opposite the chest)")
        })
        public String gateMaterial = "OAK_FENCE_GATE";

        @Comment({
                @CommentValue("How many fence posts to take/place per trip")
        })
        public int fencesPerTrip = 8;

        @Comment({
                @CommentValue("Auto-close the outer fence gate if left open (lazy: wait, then walk and close)")
        })
        public boolean gateAutoClose = true;

        @Comment({
                @CommentValue("How long the gate may stay open before the golem goes to close it (ms)")
        })
        public long gateCloseDelayMs = 2000;

        @Comment({
                @CommentValue("Build a free wool rain shelter (Г): 1 above NPC (+3); corner = fence+3 wool or 4 wool if no fence yet")
        })
        public boolean rainShelter = true;

        @Comment({
                @CommentValue("Standing rest without bench after deposit (ticks, 1.5 min = 1800)")
        })
        public long standingRestTicks = 1800L;

        @Comment({
                @CommentValue("While standing rest, how often to peek the chest for stairs to build a bench (ms)")
        })
        public long standingRestSeatCheckMs = 3000L;

        @Comment({
                @CommentValue("Sit on the bench to rest after depositing loot (ticks, 20s = 400)")
        })
        public long seatRestTicks = 400L;
    }

    private static List<String> defaultCrops() {
        List<String> list = new ArrayList<>();
        list.add("WHEAT");
        list.add("CARROT");
        list.add("POTATO");
        list.add("BEETROOT");
        list.add("PUMPKIN");
        list.add("MELON");
        return list;
    }

    private static List<Double> defaultMoodWork() {
        List<Double> list = new ArrayList<>();
        list.add(0.7D);
        list.add(0.85D);
        list.add(1.0D);
        return list;
    }

    private static List<Double> defaultMoodRest() {
        List<Double> list = new ArrayList<>();
        list.add(1.5D);
        list.add(1.25D);
        list.add(1.0D);
        return list;
    }

    public double moodWorkSpeedAt(int mood) {
        return moodValue(this.moodWorkSpeed, mood, 1.0D);
    }

    public double moodRestMultiplierAt(int mood) {
        return moodValue(this.moodRestMultiplier, mood, 1.0D);
    }

    public Material energyFeedMaterial() {
        Material material = Material.matchMaterial(this.energyFeedItem);
        if (material != null) {
            return material;
        }
        Material copper = Material.matchMaterial("COPPER_INGOT");
        return copper != null ? copper : Material.COPPER_INGOT;
    }

    public List<CropType> enabledCrops() {
        List<CropType> list = new ArrayList<>();
        if (this.farmer.crops != null) {
            for (String raw : this.farmer.crops) {
                CropType type = CropType.fromString(raw);
                if (type != null && !list.contains(type)) {
                    list.add(type);
                }
            }
        }
        if (list.isEmpty()) {
            list.add(CropType.WHEAT);
        }
        return list;
    }

    private static double moodValue(List<Double> list, int mood, double fallback) {
        if (list == null || list.isEmpty()) {
            return fallback;
        }
        int index = Math.max(0, Math.min(mood, list.size() - 1));
        Double value = list.get(index);
        return value == null || value <= 0.0D ? fallback : value;
    }

    private static List<String> defaultPlacement() {
        List<String> list = new ArrayList<>();
        list.add("GRASS_BLOCK");
        return list;
    }

    private static List<String> defaultTransformable() {
        List<String> list = new ArrayList<>();
        list.add("GRASS_BLOCK");
        list.add("DIRT");
        list.add("STONE");
        list.add("COBBLESTONE");
        list.add("DEEPSLATE");
        return list;
    }

    private static List<OreWeight> defaultOreTable() {
        List<OreWeight> list = new ArrayList<>();
        list.add(new OreWeight("COAL_ORE", 30));
        list.add(new OreWeight("COPPER_ORE", 25));
        list.add(new OreWeight("IRON_ORE", 20));
        list.add(new OreWeight("GOLD_ORE", 10));
        list.add(new OreWeight("REDSTONE_ORE", 8));
        list.add(new OreWeight("LAPIS_ORE", 5));
        list.add(new OreWeight("DIAMOND_ORE", 2));
        list.add(new OreWeight("EMERALD_ORE", 1));
        return list;
    }

    private static List<LevelStats> defaultLevels() {
        List<LevelStats> list = new ArrayList<>();
        list.add(new LevelStats(1, 1.0D, 1.0D, 1.0D));
        list.add(new LevelStats(2, 1.25D, 1.1D, 1.1D));
        list.add(new LevelStats(3, 1.5D, 1.2D, 1.25D));
        list.add(new LevelStats(4, 1.75D, 1.35D, 1.4D));
        list.add(new LevelStats(5, 2.0D, 1.5D, 1.6D));
        return list;
    }
}
