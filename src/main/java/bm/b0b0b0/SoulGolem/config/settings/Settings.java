package bm.b0b0b0.SoulGolem.config.settings;

import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;
import java.util.ArrayList;
import java.util.List;

public final class Settings extends YamlSerializable {

    private static final SerializerConfig SERIALIZER_CONFIG = new SerializerConfig.Builder().build();

    public Settings() {
        super(SERIALIZER_CONFIG);
    }

    @Comment({
            @CommentValue("Language file name without extension (lang/<name>.yml)")
    })
    public String language = "ru";

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
            @CommentValue("Movement speed while walking to ore")
    })
    public double walkSpeed = 1.0D;

    @Comment({
            @CommentValue("Ticks spent mining one block (animation)")
    })
    public long mineDurationTicks = 20L;

    @Comment({
            @CommentValue("Fallback standing rest after deposit if no bench (ticks, 20 = 1s). Bench uses miner.seatRestTicks")
    })
    public long restDurationTicks = 100L;

    @Comment({
            @CommentValue("Pickaxe in miner hand (MATERIAL)")
    })
    public String pickaxeMaterial = "IRON_PICKAXE";

    @Comment({
            @CommentValue("Hoe in farmer hand (MATERIAL)")
    })
    public String hoeMaterial = "IRON_HOE";

    @Comment({
            @CommentValue("Shovel in farmer hand while clearing border (MATERIAL)")
    })
    public String shovelMaterial = "IRON_SHOVEL";

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
    public int maxActiveOres = 2;

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

    @NewLine
    @Comment({
            @CommentValue("Base energy capacity (Phase 2 uses this)")
    })
    public int energyCapacity = 1000;

    @Comment({
            @CommentValue("Energy drained per mined block (0 disables drain in MVP)")
    })
    public int energyPerMine = 0;

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
                @CommentValue("Extra height above golem hitbox for nameplate (copper golem antenna)")
        })
        public float golemOffsetY = 0.85F;

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
                @CommentValue("Use bone meal from chest on immature wheat (vanilla applyBoneMeal)")
        })
        public boolean useBoneMeal = true;

        @Comment({
                @CommentValue("How many bone meal to take per fertilize trip")
        })
        public int boneMealPerTrip = 3;

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
    }

    public static final class Miner {
        @Comment({
                @CommentValue("Clear grass/junk on border and floor before mining")
        })
        public boolean clearArea = true;

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
                @CommentValue("Sit on the bench to rest after depositing loot (ticks, 20s = 400)")
        })
        public long seatRestTicks = 400L;
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
