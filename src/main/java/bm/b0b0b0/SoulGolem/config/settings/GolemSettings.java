package bm.b0b0b0.SoulGolem.config.settings;

import bm.b0b0b0.SoulGolem.model.CropType;
import java.util.ArrayList;
import java.util.List;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;
import org.bukkit.Material;

public final class GolemSettings extends YamlSerializable {

    private static final SerializerConfig SERIALIZER_CONFIG = new SerializerConfig.Builder().build();

    public GolemSettings() {
        super(SERIALIZER_CONFIG);
    }

    @Comment({
            @CommentValue("Golem behavior (farmer/miner). Server/DB/permissions/statue recipes: config.yml"),
            @CommentValue("Work radius in blocks from golem (square half-size)")
    })
    public int workRadius = 3;

    @Comment({
            @CommentValue("Ticks between miner actions for one golem")
    })
    public long workIntervalTicks = 40L;

    @Comment({
            @CommentValue("How many golems to process per coordinator tick (diggers always tick the full crew)")
    })
    public int golemsPerCoordinatorTick = 8;

    @Comment({
            @CommentValue("Coordinator global tick period")
    })
    public long coordinatorPeriodTicks = 5L;

    @Comment({
            @CommentValue("Pathfinder speed multiplier (1.0 = vanilla copper golem walk). Mood does not affect walk.")
    })
    public double walkSpeed = 1.0D;

    @Comment({
            @CommentValue("Owner hit with a stick: temporary work speed boost (all soul golems)")
    })
    public boolean stickBoostEnabled = true;

    @Comment({
            @CommentValue("Stick boost duration in milliseconds")
    })
    public long stickBoostDurationMs = 20_000L;

    @Comment({
            @CommentValue("Work/dig speed multiplier while stick boost is active (2.0 = twice as fast)")
    })
    public double stickBoostMultiplier = 2.0D;

    @Comment({
            @CommentValue("If this item is in the soul chest, golems deposit/withdraw remotely (no walk to chest)")
    })
    public boolean chestLinkEnabled = true;

    @Comment({
            @CommentValue("Item that enables remote chest link (kept in the chest, not consumed)")
    })
    public String chestLinkItem = "HOPPER";

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
    public Visuals visuals = new Visuals();

    @NewLine
    @Comment({
            @CommentValue("Shared work-area support for all golem types (loot, torches, seat, fence, shelter)")
    })
    public Yard yard = new Yard();

    @NewLine
    public Farmer farmer = new Farmer();

    @NewLine
    public Miner miner = new Miner();

    @NewLine
    public Digger digger = new Digger();

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

    public static final class Yard {
        @Comment({
                @CommentValue("Pick up item drops inside the work area (including border and outer fence ring)")
        })
        public boolean collectGroundLoot = true;

        @Comment({
                @CommentValue("Place torches from chest on work area corners only (not around the bench)")
        })
        public boolean placeTorches = true;

        @Comment({
                @CommentValue("Maximum torches on the work area border")
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
                @CommentValue("Clear dirt/blocks on the border (and floor for miners) before seat/torches/mining")
        })
        public boolean clearBorder = true;

        @Comment({
                @CommentValue("Place wooden fence ring outside the wool border (radius+1) from chest materials")
        })
        public boolean placeFence = true;

        @Comment({
                @CommentValue("Preferred fence if several types are in the chest; any fence from Tag.FENCES works")
        })
        public String fenceMaterial = "OAK_FENCE";

        @Comment({
                @CommentValue("Preferred fence gate if several types are in the chest; any Tag.FENCE_GATES works")
        })
        public String gateMaterial = "OAK_FENCE_GATE";

        @Comment({
                @CommentValue("Place a free path block under the outer fence gate")
        })
        public boolean placeGatePath = true;

        @Comment({
                @CommentValue("Block under the gate (e.g. DIRT_PATH)")
        })
        public String gatePathMaterial = "DIRT_PATH";

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
        public long gateCloseDelayMs = 2000L;

        @Comment({
                @CommentValue("Build a free wool rain shelter (Г): 1 above NPC (+3); corner = fence+3 wool or 4 wool if no fence yet")
        })
        public boolean rainShelter = true;
    }

    public static final class Farmer {
        @NewLine
        @Comment({
                @CommentValue("Crafting table recipe for Soul Farmer statue (copper ring + center item)")
        })
        public StatueCraft craft = StatueCraft.farmerDefault();

        @Comment({
                @CommentValue("Bake bread when a crafting table is placed (put CRAFTING_TABLE in chest; golem places it near the chest)")
        })
        public boolean craftBread = true;

        @Comment({
                @CommentValue("If true, nobody (including owner) can open the golem crafting table; only admin Creative+Shift bypasses")
        })
        public boolean denyCraftOpen = true;

        @Comment({
                @CommentValue("Wander the farm while waiting for crops")
        })
        public boolean wanderWhileWaiting = true;

        @Comment({
                @CommentValue("Use bone meal from chest on immature crops (stems only until fully grown)")
        })
        public boolean useBoneMeal = true;

        @Comment({
                @CommentValue("How many bone meal to take per fertilize trip")
        })
        public int boneMealPerTrip = 3;

        @Comment({
                @CommentValue("Place COMPOSTER from chest next to the crafting table; compost excess seeds/crops into bone meal")
        })
        public boolean useComposter = true;

        @Comment({
                @CommentValue("Minimum seeds of each type to keep in chest (not composted)")
        })
        public int compostSeedReserve = 16;

        @Comment({
                @CommentValue("If bone meal in chest+hands is below this and crops need fertilizing, also compost harvest products")
        })
        public int compostWhenBoneMealBelow = 3;

        @Comment({
                @CommentValue("Minimum harvest items of each type to keep when composting crops")
        })
        public int compostCropKeep = 64;

        @Comment({
                @CommentValue("How many compostable items to take per compost trip")
        })
        public int compostItemsPerTrip = 8;

        @Comment({
                @CommentValue("Ticks to wait after crops become ripe (or after bone meal) before harvesting — so pumpkin/melon are visible. 40 ≈ 2s, 0 = instant")
        })
        public long harvestNoticeTicks = 40L;

        @Comment({
                @CommentValue("How many seeds to take per planting trip")
        })
        public int seedsPerTrip = 8;

        @Comment({
                @CommentValue("Supported crops: WHEAT, CARROT, POTATO, BEETROOT, PUMPKIN, MELON"),
                @CommentValue("PUMPKIN/MELON plant on a checkerboard (stem / empty fruit pad) so fruit can grow")
        })
        public List<String> crops = defaultCrops();
    }

    public static final class Miner {
        @NewLine
        @Comment({
                @CommentValue("Crafting table recipe for Soul Miner statue (copper ring + center item)")
        })
        public StatueCraft craft = StatueCraft.minerDefault();

        @Comment({
                @CommentValue("Take iron/diamond/netherite pickaxe from chest: 2/3/4 blocks per rest trip")
        })
        public boolean pickaxeUpgrades = true;

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

    public static final class Digger {
        @NewLine
        @Comment({
                @CommentValue("Crafting table recipe for Soul Digger statue (copper ring + center item)")
        })
        public StatueCraft craft = StatueCraft.diggerDefault();

        @Comment({
                @CommentValue("Pit width in blocks (square). Border radius = pitSize/2")
        })
        public int pitSize = 8;

        @Comment({
                @CommentValue("How many layers to dig down from the surface floor")
        })
        public int maxDepth = 30;

        @Comment({
                @CommentValue("How many blocks to carry to the chest per trip")
        })
        public int blocksPerTrip = 10;

        @Comment({
                @CommentValue("Free spiral stair material along the pit wall")
        })
        public String stairMaterial = "STONE_STAIRS";

        @Comment({
                @CommentValue("Solid under each stair + corner landing (not dug by diggers)")
        })
        public String stairSupportMaterial = "STONE";

        @Comment({
                @CommentValue("Shovel for soft blocks (dirt/sand/gravel)")
        })
        public String shovelMaterial = "COPPER_SHOVEL";

        @Comment({
                @CommentValue("Pickaxe for hard blocks (stone/ores)")
        })
        public String pickaxeMaterial = "COPPER_PICKAXE";

        @Comment({
                @CommentValue("Do not dig a floor block if open air below is deeper than this (cave safety)")
        })
        public int caveSafeDepth = 5;

        @Comment({
                @CommentValue("Replace lava/water near diggers / dig floor with this solid")
        })
        public String hazardSealMaterial = "COBBLESTONE";

        @Comment({
                @CommentValue("Fill border gaps / lava / water with this solid")
        })
        public String borderSealMaterial = "STONE";

        @Comment({
                @CommentValue("If air/fluid appears within this many blocks under the dig floor on the border, seal that column down")
        })
        public int borderGapProbe = 2;

        @Comment({
                @CommentValue("Max diggers on one pit (leader + helpers hired from copper golem statues in the chest)")
        })
        public int maxCrew = 10;

        @Comment({
                @CommentValue("Ticks between digger seek/actions (lower = more aggressive crew)")
        })
        public long workIntervalTicks = 5L;

        @Comment({
                @CommentValue("Dig duration for soft blocks (dirt/sand/gravel) in ticks")
        })
        public long softDigDurationTicks = 6L;

        @Comment({
                @CommentValue("Dig duration for hard blocks (stone/ores) in ticks")
        })
        public long hardDigDurationTicks = 12L;

        @Comment({
                @CommentValue("Unused: diggers climb out via stone spiral stairs. Kept for config compatibility.")
        })
        public String escapeLadderMaterial = "LADDER";
    }

    public static final class StatueCraft {
        @Comment({
                @CommentValue("Enable this statue recipe")
        })
        public boolean enabled = true;

        @Comment({
                @CommentValue("Shaped recipe rows (max 3), like a chest ring")
        })
        public List<String> shape = new ArrayList<>();

        @Comment({
                @CommentValue("Ingredient keys as CHAR=MATERIAL (e.g. C=COPPER_INGOT)")
        })
        public List<String> ingredients = new ArrayList<>();

        public static StatueCraft farmerDefault() {
            StatueCraft craft = new StatueCraft();
            craft.enabled = true;
            craft.shape = defaultRingShape();
            craft.ingredients = defaultIngredients("WHEAT_SEEDS");
            return craft;
        }

        public static StatueCraft minerDefault() {
            StatueCraft craft = new StatueCraft();
            craft.enabled = true;
            craft.shape = defaultRingShape();
            craft.ingredients = defaultIngredients("COAL");
            return craft;
        }

        public static StatueCraft diggerDefault() {
            StatueCraft craft = new StatueCraft();
            craft.enabled = true;
            craft.shape = defaultRingShape();
            craft.ingredients = defaultIngredients("IRON_SHOVEL");
            return craft;
        }

        private static List<String> defaultRingShape() {
            List<String> shape = new ArrayList<>(3);
            shape.add("CCC");
            shape.add("CSC");
            shape.add("CCC");
            return shape;
        }

        private static List<String> defaultIngredients(String center) {
            List<String> list = new ArrayList<>(2);
            list.add("C=COPPER_INGOT");
            list.add("S=" + center);
            return list;
        }
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
