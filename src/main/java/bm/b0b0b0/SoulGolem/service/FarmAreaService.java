package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.CropType;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Gate;
import org.bukkit.entity.CopperGolem;

public final class FarmAreaService {

    private final ConfigurationLoader configurationLoader;
    private final WorkAreaService workAreaService;
    private final SoulChestService chestService;
    private final Map<BlockPos, UUID> farmBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<BlockPos>> byGolem = new ConcurrentHashMap<>();
    private final Map<BlockPos, Material> originals = new ConcurrentHashMap<>();

    public FarmAreaService(
            ConfigurationLoader configurationLoader,
            WorkAreaService workAreaService,
            SoulChestService chestService
    ) {
        this.configurationLoader = configurationLoader;
        this.workAreaService = workAreaService;
        this.chestService = chestService;
    }

    public void ensureWater(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        Block water = world.getBlockAt(homeX, homeY, homeZ);
        if (this.chestService.isChestColumn(data, water) || water.getType() == Material.CRAFTING_TABLE) {
            return;
        }
        Block above = water.getRelative(BlockFace.UP);
        if (isVegetation(above.getType())) {
            clearVegetation(above);
        }
        if (water.getType() != Material.WATER) {
            rememberAndSet(water, Material.WATER, data.id());
        }
    }

    public List<Block> tillableSoil(SoulGolemData data, int radius) {
        List<Block> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return list;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Block soil = world.getBlockAt(homeX + x, homeY, homeZ + z);
                if (this.chestService.isChestColumn(data, soil)) {
                    continue;
                }
                if (soil.getType() == Material.CRAFTING_TABLE || SoulChestService.isChestLike(soil.getType())) {
                    continue;
                }
                if (isBorderWool(soil.getType())) {
                    continue;
                }
                if (!canTill(soil.getType()) || soil.getType() == Material.FARMLAND) {
                    continue;
                }
                Block above = soil.getRelative(BlockFace.UP);
                if (!above.getType().isAir()
                        && !isVegetation(above.getType())
                        && !isAnyCrop(above.getType())) {
                    continue;
                }
                list.add(soil);
            }
        }
        return list;
    }

    public void tillSoil(Block soil, UUID golemId) {
        if (!canTill(soil.getType()) || soil.getType() == Material.FARMLAND) {
            return;
        }
        clearVegetation(soil.getRelative(BlockFace.UP));
        rememberAndSet(soil, Material.FARMLAND, golemId);
    }

    private void rememberAndSet(Block block, Material type, UUID golemId) {
        BlockPos pos = BlockPos.of(block);
        this.originals.putIfAbsent(pos, block.getType());
        block.setType(type, false);
        protect(block, golemId);
    }

    private boolean isBorderWool(Material type) {
        return isBorderMaterial(type);
    }

    public boolean isBorderMaterial(Material type) {
        String configured = this.configurationLoader.config().settings().borderMaterial;
        Material border = Material.matchMaterial(configured == null ? "PURPLE_WOOL" : configured);
        return border != null && type == border;
    }

    private static boolean canTill(Material type) {
        return type == Material.GRASS_BLOCK
                || type == Material.DIRT
                || type == Material.COARSE_DIRT
                || type == Material.ROOTED_DIRT
                || type == Material.FARMLAND
                || type == Material.DIRT_PATH;
    }

    public static boolean isVegetation(Material type) {
        if (type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.FERN
                || type == Material.LARGE_FERN
                || type == Material.DEAD_BUSH
                || type == Material.BUSH
                || type == Material.FIREFLY_BUSH
                || type == Material.SHORT_DRY_GRASS
                || type == Material.TALL_DRY_GRASS
                || type == Material.WILDFLOWERS
                || type == Material.CACTUS_FLOWER
                || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.PINK_PETALS
                || type == Material.LEAF_LITTER
                || type == Material.AZALEA
                || type == Material.FLOWERING_AZALEA
                || type == Material.ROSE_BUSH) {
            return true;
        }
        if (Tag.FLOWERS.isTagged(type) || Tag.SMALL_FLOWERS.isTagged(type)) {
            return true;
        }
        String name = type.name();
        return name.endsWith("_SAPLING")
                || name.endsWith("_BUSH")
                || name.equals("GRASS")
                || name.equals("DOUBLE_PLANT");
    }

    public static void clearVegetation(Block block) {
        if (!isVegetation(block.getType())) {
            return;
        }
        Block above = block.getRelative(BlockFace.UP);
        Block below = block.getRelative(BlockFace.DOWN);
        block.setType(Material.AIR, false);
        if (isVegetation(above.getType())) {
            above.setType(Material.AIR, false);
        }
        if (isVegetation(below.getType())) {
            below.setType(Material.AIR, false);
        }
    }

    public void maintainFarmland(SoulGolemData data, int radius) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Block soil = world.getBlockAt(homeX + x, homeY, homeZ + z);
                if (this.chestService.isChestColumn(data, soil)) {
                    continue;
                }
                if (soil.getType() == Material.CRAFTING_TABLE || SoulChestService.isChestLike(soil.getType())) {
                    continue;
                }
                if (isBorderWool(soil.getType())) {
                    continue;
                }
                if (soil.getType() == Material.FARMLAND || soil.getType() == Material.WATER) {
                    continue;
                }
                if (!canTill(soil.getType())) {
                    continue;
                }
                Block above = soil.getRelative(BlockFace.UP);
                if (!above.getType().isAir() && !isAnyCrop(above.getType()) && !isVegetation(above.getType())) {
                    continue;
                }
                if (isVegetation(above.getType())) {
                    clearVegetation(above);
                }
                rememberAndSet(soil, Material.FARMLAND, data.id());
            }
        }
    }

    public List<Block> weedsToClear(SoulGolemData data, int radius) {
        List<Block> list = new ArrayList<>();
        if (this.configurationLoader.config().settings().farmer.clearBorder) {
            list.addAll(areaObstructions(data, radius));
        } else {
            list.addAll(fieldObstructions(data, radius));
        }
        return list;
    }

    public List<Block> minerJunkToClear(SoulGolemData data, int radius, OreTableService oreTable) {
        List<Block> list = new ArrayList<>();
        if (!this.configurationLoader.config().settings().miner.clearArea) {
            return list;
        }
        for (Block block : areaObstructions(data, radius)) {
            if (oreTable != null && oreTable.isOre(block.getType())) {
                continue;
            }
            list.add(block);
        }
        return list;
    }

    public List<Block> areaObstructions(SoulGolemData data, int radius) {
        return areaObstructions(data, radius, false);
    }

    public List<Block> areaObstructionsForSetup(SoulGolemData data, int radius) {
        return areaObstructions(data, radius, true);
    }

    private List<Block> areaObstructions(SoulGolemData data, int radius, boolean setup) {
        List<Block> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return list;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        for (int x = homeX - r; x <= homeX + r; x++) {
            for (int z = homeZ - r; z <= homeZ + r; z++) {
                boolean perimeter = x == homeX - r || x == homeX + r || z == homeZ - r || z == homeZ + r;
                int clearHeight = setup && perimeter ? 2 : 6;
                addColumnObstructions(list, world, data, x, homeY, z, setup, clearHeight);
            }
        }
        list.sort((a, b) -> {
            if (a.getZ() != b.getZ()) {
                return Integer.compare(a.getZ(), b.getZ());
            }
            int xCmp = (a.getZ() & 1) == 0
                    ? Integer.compare(a.getX(), b.getX())
                    : Integer.compare(b.getX(), a.getX());
            if (xCmp != 0) {
                return xCmp;
            }
            return Integer.compare(b.getY(), a.getY());
        });
        return list;
    }

    public List<Block> fieldObstructions(SoulGolemData data, int radius) {
        List<Block> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return list;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        for (int x = homeX - r + 1; x <= homeX + r - 1; x++) {
            for (int z = homeZ - r + 1; z <= homeZ + r - 1; z++) {
                if (x == homeX && z == homeZ) {
                    continue;
                }
                addColumnObstructions(list, world, data, x, homeY, z, false, 6);
            }
        }
        return list;
    }

    public List<Block> fieldWeeds(SoulGolemData data, int radius) {
        return fieldObstructions(data, radius);
    }

    private void addColumnObstructions(
            List<Block> list,
            World world,
            SoulGolemData data,
            int x,
            int homeY,
            int z,
            boolean setup,
            int clearHeight
    ) {
        if (isWaterColumn(data, x, z)) {
            return;
        }
        if (!setup) {
            if (this.chestService.isChestColumn(data, world.getBlockAt(x, homeY, z))) {
                return;
            }
            if (data.hasCraftStation()
                    && x == (int) Math.floor(data.craftX())
                    && z == (int) Math.floor(data.craftZ())) {
                return;
            }
        }
        int maxY = homeY + Math.max(1, clearHeight);
        for (int y = homeY + 1; y <= maxY; y++) {
            Block above = world.getBlockAt(x, y, z);
            if (above.getType().isAir()) {
                continue;
            }
            if (!setup && (SoulChestService.isChestLike(above.getType()) || above.getType() == Material.CRAFTING_TABLE)) {
                continue;
            }
            if (isClearableObstruction(above, data)) {
                if (!containsBlock(list, above)) {
                    list.add(above);
                }
            }
        }
    }

    public boolean isClearableObstruction(Block block, SoulGolemData data) {
        Material type = block.getType();
        if (type.isAir() || type == Material.WATER || type == Material.LAVA) {
            return false;
        }
        if (SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE || type == Material.FURNACE
                || type == Material.BLAST_FURNACE || type == Material.SMOKER) {
            return false;
        }
        if (Tag.STAIRS.isTagged(type)) {
            return false;
        }
        if (Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type)) {
            return false;
        }
        if (isTorchMaterial(type)) {
            return false;
        }
        if (isAnyCrop(type)) {
            return false;
        }
        if (isBorderWool(type)) {
            return false;
        }
        if (isVegetation(type) || type == Material.SNOW) {
            return true;
        }
        return type.isSolid();
    }

    public List<Block> perimeterObstructions(SoulGolemData data, int radius) {
        List<Block> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return list;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        for (int x = homeX - r; x <= homeX + r; x++) {
            addColumnObstructions(list, world, data, x, homeY, homeZ - r, false, 6);
            addColumnObstructions(list, world, data, x, homeY, homeZ + r, false, 6);
        }
        for (int z = homeZ - r + 1; z <= homeZ + r - 1; z++) {
            addColumnObstructions(list, world, data, homeX - r, homeY, z, false, 6);
            addColumnObstructions(list, world, data, homeX + r, homeY, z, false, 6);
        }
        return list;
    }

    public List<Block> emptyFarmland(SoulGolemData data, int radius) {
        List<Block> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return list;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Block soil = world.getBlockAt(homeX + x, homeY, homeZ + z);
                if (soil.getType() != Material.FARMLAND) {
                    continue;
                }
                if (this.chestService.isChestColumn(data, soil)) {
                    continue;
                }
                Block crop = soil.getRelative(BlockFace.UP);
                if (crop.getType().isAir()) {
                    list.add(soil);
                }
            }
        }
        return list;
    }

    public List<Block> matureWheat(SoulGolemData data, int radius) {
        return matureCrops(data, radius, List.of(CropType.WHEAT));
    }

    public List<Block> matureCrops(SoulGolemData data, int radius, Collection<CropType> crops) {
        List<Block> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null || crops == null || crops.isEmpty()) {
            return list;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                Block crop = world.getBlockAt(homeX + x, homeY + 1, homeZ + z);
                if (!isEnabledCrop(crop.getType(), crops)) {
                    continue;
                }
                BlockData dataBlock = crop.getBlockData();
                if (!(dataBlock instanceof Ageable ageable)) {
                    continue;
                }
                if (ageable.getAge() >= ageable.getMaximumAge()) {
                    list.add(crop);
                }
            }
        }
        return list;
    }

    public Block pickFarthestFromChest(List<Block> blocks, SoulGolemData data) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        int cx = (int) Math.floor(data.chestX());
        int cz = (int) Math.floor(data.chestZ());
        Block best = null;
        double bestDist = -1.0D;
        for (Block block : blocks) {
            double dx = block.getX() - cx;
            double dz = block.getZ() - cz;
            double dist = dx * dx + dz * dz;
            if (dist > bestDist) {
                bestDist = dist;
                best = block;
            }
        }
        return best;
    }

    public Block pickImmatureForBoneMeal(List<Block> immature, SoulGolemData data) {
        if (immature == null || immature.isEmpty()) {
            return null;
        }
        int cx = (int) Math.floor(data.chestX());
        int cz = (int) Math.floor(data.chestZ());
        Block best = null;
        int bestAge = -1;
        double bestDist = -1.0D;
        for (Block crop : immature) {
            int age = 0;
            if (crop.getBlockData() instanceof Ageable ageable) {
                age = ageable.getAge();
            }
            double dx = crop.getX() - cx;
            double dz = crop.getZ() - cz;
            double dist = dx * dx + dz * dz;
            if (age > bestAge || (age == bestAge && dist > bestDist)) {
                bestAge = age;
                bestDist = dist;
                best = crop;
            }
        }
        return best;
    }

    public List<Block> immatureWheat(SoulGolemData data, int radius) {
        return immatureCrops(data, radius, List.of(CropType.WHEAT));
    }

    public List<Block> immatureCrops(SoulGolemData data, int radius, Collection<CropType> crops) {
        List<Block> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null || crops == null || crops.isEmpty()) {
            return list;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                Block crop = world.getBlockAt(homeX + x, homeY + 1, homeZ + z);
                if (!isEnabledCrop(crop.getType(), crops)) {
                    continue;
                }
                BlockData dataBlock = crop.getBlockData();
                if (!(dataBlock instanceof Ageable ageable)) {
                    continue;
                }
                if (ageable.getAge() < ageable.getMaximumAge()) {
                    list.add(crop);
                }
            }
        }
        return list;
    }

    public int moodScore(SoulGolemData data, int radius) {
        Settings settings = this.configurationLoader.config().settings();
        int score = 0;
        if (hasValidSeat(data)) {
            score++;
        }
        if (countBorderTorches(data, radius) >= Math.max(1, settings.minTorchesForMood)) {
            score++;
        }
        return Math.max(0, Math.min(2, score));
    }

    public List<Block> perimeterTorchSpots(SoulGolemData data, int radius) {
        int max = Math.max(1, this.configurationLoader.config().settings().farmer.maxTorches);
        return perimeterTorchSpots(data, radius, max);
    }

    public List<Block> perimeterTorchSpots(SoulGolemData data, int radius, int maxTorches) {
        List<Block> candidates = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return candidates;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        int[][] corners = {
                {homeX - r, homeZ - r},
                {homeX + r, homeZ - r},
                {homeX - r, homeZ + r},
                {homeX + r, homeZ + r}
        };
        Block seat = hasValidSeat(data) ? seatBlock(data) : null;
        for (int[] corner : corners) {
            addTorchSpot(candidates, world, data, corner[0], homeY, corner[1], seat);
        }
        int max = Math.max(1, maxTorches);
        int existing = countBorderTorches(data, radius);
        int need = Math.max(0, max - existing);
        if (need <= 0) {
            return List.of();
        }
        List<Block> result = new ArrayList<>();
        for (Block spot : candidates) {
            if (result.size() >= need) {
                break;
            }
            if (!containsBlock(result, spot)) {
                result.add(spot);
            }
        }
        return result;
    }

    public int countBorderTorches(SoulGolemData data, int radius) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return 0;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        int count = 0;
        for (int x = homeX - r; x <= homeX + r; x++) {
            count += torchCountAt(world, data, x, homeY, homeZ - r);
            count += torchCountAt(world, data, x, homeY, homeZ + r);
        }
        for (int z = homeZ - r + 1; z <= homeZ + r - 1; z++) {
            count += torchCountAt(world, data, homeX - r, homeY, z);
            count += torchCountAt(world, data, homeX + r, homeY, z);
        }
        return count;
    }

    private int torchCountAt(World world, SoulGolemData data, int x, int homeY, int z) {
        if (isWaterColumn(data, x, z)) {
            return 0;
        }
        for (int y = homeY - 1; y <= homeY + 3; y++) {
            Block block = world.getBlockAt(x, y, z);
            if (isTorchMaterial(block.getType())) {
                return 1;
            }
        }
        return 0;
    }

    private boolean isTorchMaterial(Material type) {
        if (type == Material.TORCH || type == Material.WALL_TORCH
                || type == Material.SOUL_TORCH || type == Material.SOUL_WALL_TORCH) {
            return true;
        }
        Settings settings = this.configurationLoader.config().settings();
        Material farmerTorch = Material.matchMaterial(
                settings.farmer.torchMaterial == null ? "TORCH" : settings.farmer.torchMaterial
        );
        if (farmerTorch != null && type == farmerTorch) {
            return true;
        }
        Material minerTorch = Material.matchMaterial(
                settings.miner.torchMaterial == null ? "TORCH" : settings.miner.torchMaterial
        );
        return minerTorch != null && type == minerTorch;
    }

    private static boolean containsBlock(List<Block> list, Block block) {
        for (Block existing : list) {
            if (existing.getX() == block.getX() && existing.getY() == block.getY() && existing.getZ() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    private void addTorchSpot(List<Block> list, World world, SoulGolemData data, int x, int homeY, int z, Block seat) {
        Block spot = resolveTorchSpot(world, data, x, homeY, z, seat);
        if (spot != null && !containsBlock(list, spot)) {
            list.add(spot);
        }
    }

    private Block resolveTorchSpot(World world, SoulGolemData data, int x, int homeY, int z, Block seat) {
        if (isWaterColumn(data, x, z)) {
            return null;
        }
        if (seat != null && seat.getX() == x && seat.getZ() == z) {
            return null;
        }
        if (this.chestService.isChestColumn(data, world.getBlockAt(x, homeY, z))) {
            return null;
        }
        for (int y = homeY + 2; y >= homeY - 4; y--) {
            Block ground = world.getBlockAt(x, y, z);
            Material groundType = ground.getType();
            if (!groundType.isSolid()
                    || SoulChestService.isChestLike(groundType)
                    || groundType == Material.CRAFTING_TABLE
                    || Tag.STAIRS.isTagged(groundType)) {
                continue;
            }
            Block above = ground.getRelative(BlockFace.UP);
            if (seat != null && above.getX() == seat.getX() && above.getY() == seat.getY() && above.getZ() == seat.getZ()) {
                return null;
            }
            if (above.getType().isAir()) {
                return above;
            }
            return null;
        }
        return null;
    }

    public void placeTorch(Block spot, Material torch, UUID golemId) {
        if (!spot.getType().isAir()) {
            return;
        }
        if (Tag.STAIRS.isTagged(spot.getRelative(BlockFace.DOWN).getType())) {
            return;
        }
        spot.setType(torch, false);
        protect(spot, golemId);
    }

    public Material resolveFenceMaterial() {
        return resolveFenceMaterial(null);
    }

    public Material resolveFenceMaterial(SoulGolemData data) {
        String raw = data != null && data.type() == GolemType.MINER
                ? settings().miner.fenceMaterial
                : settings().farmer.fenceMaterial;
        Material fence = Material.matchMaterial(raw);
        return fence != null && fence.isBlock() ? fence : Material.OAK_FENCE;
    }

    public Material resolveGateMaterial() {
        return resolveGateMaterial(null);
    }

    public Material resolveGateMaterial(SoulGolemData data) {
        String raw = data != null && data.type() == GolemType.MINER
                ? settings().miner.gateMaterial
                : settings().farmer.gateMaterial;
        Material gate = Material.matchMaterial(raw);
        return gate != null && gate.isBlock() ? gate : Material.OAK_FENCE_GATE;
    }

    public boolean needsOuterFenceWork(SoulGolemData data, int radius) {
        return !outerFenceSlots(data, radius).isEmpty() || needsOuterFenceGate(data, radius);
    }

    public boolean canProgressOuterFence(SoulGolemData data, int radius, int carriedFence, int carriedGate) {
        if (!outerFenceSlots(data, radius).isEmpty()) {
            return carriedFence > 0 || this.chestService.countItem(data, resolveFenceMaterial(data)) > 0;
        }
        if (needsOuterFenceGate(data, radius)) {
            return carriedGate > 0 || this.chestService.countItem(data, resolveGateMaterial(data)) > 0;
        }
        return false;
    }

    public boolean needsOuterFenceGate(SoulGolemData data, int radius) {
        Block gate = outerFenceGateSpot(data, radius);
        if (gate == null) {
            return false;
        }
        Material expected = resolveGateMaterial(data);
        return gate.getType() != expected && !Tag.FENCE_GATES.isTagged(gate.getType());
    }

    public List<Block> outerFenceSlots(SoulGolemData data, int radius) {
        List<Block> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return list;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int fenceY = homeY + 1;
        int r = Math.max(1, radius);
        int fr = r + 1;
        int minX = homeX - fr;
        int maxX = homeX + fr;
        int minZ = homeZ - fr;
        int maxZ = homeZ + fr;
        Block gateSpot = outerFenceGateSpot(data, radius);
        Material fence = resolveFenceMaterial(data);
        Material gate = resolveGateMaterial(data);
        for (int x = minX; x <= maxX; x++) {
            addOuterFenceSlotIfNeeded(list, world, x, fenceY, minZ, fence, gate, gateSpot);
        }
        for (int z = minZ + 1; z <= maxZ; z++) {
            addOuterFenceSlotIfNeeded(list, world, maxX, fenceY, z, fence, gate, gateSpot);
        }
        for (int x = maxX - 1; x >= minX; x--) {
            addOuterFenceSlotIfNeeded(list, world, x, fenceY, maxZ, fence, gate, gateSpot);
        }
        for (int z = maxZ - 1; z > minZ; z--) {
            addOuterFenceSlotIfNeeded(list, world, minX, fenceY, z, fence, gate, gateSpot);
        }
        return list;
    }

    private static void addOuterFenceSlotIfNeeded(
            List<Block> list,
            World world,
            int x,
            int fenceY,
            int z,
            Material fence,
            Material gate,
            Block gateSpot
    ) {
        Block block = world.getBlockAt(x, fenceY, z);
        if (gateSpot != null && block.getX() == gateSpot.getX() && block.getZ() == gateSpot.getZ()) {
            return;
        }
        Material type = block.getType();
        if (type == fence || Tag.FENCES.isTagged(type) || type == gate || Tag.FENCE_GATES.isTagged(type)) {
            return;
        }
        if (SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE) {
            return;
        }
        list.add(block);
    }

    public Block outerFenceGateSpot(SoulGolemData data, int radius) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        int fr = r + 1;
        int cx = (int) Math.floor(data.chestX());
        int cz = (int) Math.floor(data.chestZ());
        int gateX;
        int gateZ;
        if (cz <= homeZ - r) {
            gateZ = homeZ + fr;
            gateX = Math.max(homeX - fr, Math.min(homeX + fr, cx));
        } else if (cz >= homeZ + r) {
            gateZ = homeZ - fr;
            gateX = Math.max(homeX - fr, Math.min(homeX + fr, cx));
        } else if (cx <= homeX - r) {
            gateX = homeX + fr;
            gateZ = Math.max(homeZ - fr, Math.min(homeZ + fr, cz));
        } else if (cx >= homeX + r) {
            gateX = homeX - fr;
            gateZ = Math.max(homeZ - fr, Math.min(homeZ + fr, cz));
        } else {
            gateZ = homeZ + fr;
            gateX = homeX;
        }
        if (Math.abs(gateX - homeX) != fr && Math.abs(gateZ - homeZ) != fr) {
            gateZ = homeZ + fr;
            gateX = homeX;
        }
        return world.getBlockAt(gateX, homeY + 1, gateZ);
    }

    public boolean isOuterGateOpen(SoulGolemData data) {
        int radius = this.chestService.effectiveRadius(data);
        Block gate = outerFenceGateSpot(data, radius);
        if (gate == null) {
            return false;
        }
        BlockData blockData = gate.getBlockData();
        return blockData instanceof Gate gateData && gateData.isOpen();
    }

    public void closeOuterGate(Block gate) {
        if (gate == null) {
            return;
        }
        BlockData blockData = gate.getBlockData();
        if (!(blockData instanceof Gate gateData) || !gateData.isOpen()) {
            return;
        }
        gateData.setOpen(false);
        gate.setBlockData(gateData, true);
        refreshFenceConnections(gate);
    }

    public List<Block> outerFenceObstructions(SoulGolemData data, int radius) {
        List<Block> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return list;
        }
        int homeY = (int) Math.floor(data.homeY());
        int fenceY = homeY + 1;
        List<Block> ring = new ArrayList<>(outerFenceSlots(data, radius));
        Block gate = outerFenceGateSpot(data, radius);
        if (gate != null && needsOuterFenceGate(data, radius)) {
            ring.add(gate);
        }
        for (Block slot : ring) {
            int x = slot.getX();
            int z = slot.getZ();
            Block misplaced = world.getBlockAt(x, homeY, z);
            Material ground = misplaced.getType();
            if (Tag.FENCES.isTagged(ground) || Tag.FENCE_GATES.isTagged(ground)) {
                if (!containsBlock(list, misplaced)) {
                    list.add(misplaced);
                }
            }
            Block atFence = world.getBlockAt(x, fenceY, z);
            if (isClearableObstruction(atFence, data)
                    && !Tag.FENCES.isTagged(atFence.getType())
                    && !Tag.FENCE_GATES.isTagged(atFence.getType())) {
                if (!containsBlock(list, atFence)) {
                    list.add(atFence);
                }
            }
            Block above = world.getBlockAt(x, fenceY + 1, z);
            if (isClearableObstruction(above, data)) {
                if (!containsBlock(list, above)) {
                    list.add(above);
                }
            }
        }
        list.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        return list;
    }

    public Location standOnBorderForFence(SoulGolemData data, Block outerCell) {
        if (outerCell == null || outerCell.getWorld() == null) {
            return null;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = this.chestService.effectiveRadius(data);
        int ox = outerCell.getX();
        int oz = outerCell.getZ();
        int inX = ox;
        int inZ = oz;
        int fr = r + 1;
        if (ox <= homeX - fr) {
            inX = homeX - r;
        } else if (ox >= homeX + fr) {
            inX = homeX + r;
        }
        if (oz <= homeZ - fr) {
            inZ = homeZ - r;
        } else if (oz >= homeZ + fr) {
            inZ = homeZ + r;
        }
        int[][] candidates = {
                {inX, inZ},
                {inX + 1, inZ}, {inX - 1, inZ}, {inX, inZ + 1}, {inX, inZ - 1},
                {inX + 1, inZ + 1}, {inX + 1, inZ - 1}, {inX - 1, inZ + 1}, {inX - 1, inZ - 1}
        };
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] c : candidates) {
            if (Math.abs(c[0] - homeX) > r || Math.abs(c[1] - homeZ) > r) {
                continue;
            }
            Block border = outerCell.getWorld().getBlockAt(c[0], homeY, c[1]);
            Location stand = standOn(border);
            if (this.chestService.collidesWithStation(data, stand)) {
                continue;
            }
            double dx = stand.getX() - (ox + 0.5D);
            double dz = stand.getZ() - (oz + 0.5D);
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = stand;
            }
        }
        if (best != null) {
            return best;
        }
        Block border = outerCell.getWorld().getBlockAt(inX, homeY, inZ);
        return standOn(border);
    }

    public void placeOuterFence(Block spot, Material fence, UUID golemId) {
        if (spot == null) {
            return;
        }
        ensureSolidFenceSupport(spot);
        Block above = spot.getRelative(BlockFace.UP);
        Material aboveType = above.getType();
        Material wool = resolveBorderWool();
        boolean shelterAbove = aboveType == wool || isBorderMaterial(aboveType);
        if (!shelterAbove && (isVegetation(aboveType) || aboveType == Material.SNOW
                || (aboveType.isSolid()
                && !Tag.FENCES.isTagged(aboveType)
                && !Tag.FENCE_GATES.isTagged(aboveType)
                && !SoulChestService.isChestLike(aboveType)
                && aboveType != Material.CRAFTING_TABLE))) {
            above.setType(Material.AIR, false);
        }
        if (SoulChestService.isChestLike(spot.getType()) || spot.getType() == Material.CRAFTING_TABLE) {
            return;
        }
        if (spot.getType() == fence || Tag.FENCES.isTagged(spot.getType())) {
            protect(spot, golemId);
            refreshFenceConnections(spot);
            return;
        }
        // Нижняя шерсть навеса → забор.
        BlockPos pos = BlockPos.of(spot);
        this.originals.putIfAbsent(pos, spot.getType());
        spot.setType(fence, true);
        protect(spot, golemId);
        refreshFenceConnections(spot);
    }

    public void placeOuterGate(SoulGolemData data, Block spot, Material gate, UUID golemId) {
        if (spot == null) {
            return;
        }
        ensureSolidFenceSupport(spot);
        Block above = spot.getRelative(BlockFace.UP);
        Material wool = resolveBorderWool();
        Material aboveType = above.getType();
        boolean shelterAbove = aboveType == wool || isBorderMaterial(aboveType);
        if (!shelterAbove && (isVegetation(aboveType) || aboveType == Material.SNOW
                || (aboveType.isSolid() && !Tag.FENCES.isTagged(aboveType) && !Tag.FENCE_GATES.isTagged(aboveType)
                && !SoulChestService.isChestLike(aboveType)))) {
            if (isClearableObstruction(above, data) || isVegetation(aboveType) || aboveType == Material.SNOW) {
                above.setType(Material.AIR, false);
            }
        }
        if (SoulChestService.isChestLike(spot.getType()) || spot.getType() == Material.CRAFTING_TABLE) {
            return;
        }
        BlockFace facing = faceTowardHome(data, spot.getX(), spot.getZ());
        BlockPos pos = BlockPos.of(spot);
        this.originals.putIfAbsent(pos, spot.getType());
        BlockData blockData = gate.createBlockData();
        if (blockData instanceof org.bukkit.block.data.type.Gate gateData) {
            gateData.setFacing(facing);
            gateData.setOpen(false);
            spot.setBlockData(gateData, true);
        } else {
            spot.setType(gate, true);
        }
        protect(spot, golemId);
        refreshFenceConnections(spot);
    }

    public void clearOuterFenceObstruction(Block block, SoulGolemData data) {
        if (block == null) {
            return;
        }
        int homeY = (int) Math.floor(data.homeY());
        Material type = block.getType();
        if (block.getY() == homeY && (Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type))) {
            block.setType(Material.DIRT, false);
            return;
        }
        if (isVegetation(type)) {
            clearVegetation(block);
            return;
        }
        if (!type.isAir()) {
            block.setType(Material.AIR, false);
        }
    }

    private static void ensureSolidFenceSupport(Block fenceSpot) {
        Block below = fenceSpot.getRelative(BlockFace.DOWN);
        Material type = below.getType();
        if (type.isSolid() && !Tag.FENCES.isTagged(type) && !Tag.FENCE_GATES.isTagged(type)) {
            return;
        }
        below.setType(Material.DIRT, false);
    }

    private static void refreshFenceConnections(Block spot) {
        if (spot == null) {
            return;
        }
        spot.getState().update(true, true);
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block neighbor = spot.getRelative(face);
            Material type = neighbor.getType();
            if (Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type)) {
                neighbor.getState().update(true, true);
            }
        }
    }

    private Settings settings() {
        return this.configurationLoader.config().settings();
    }

    public Location standForClear(Block junk) {
        if (junk == null || junk.getWorld() == null) {
            return null;
        }
        Block ground = junk.getRelative(BlockFace.DOWN);
        if (isVegetation(junk.getType()) || junk.getType() == Material.SNOW) {
            if (ground.getType().isSolid() || ground.getType() == Material.FARMLAND) {
                return junk.getLocation().add(0.5D, 0.0D, 0.5D);
            }
            return junk.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {0, 0}};
        for (int[] o : offsets) {
            Block at = junk.getWorld().getBlockAt(junk.getX() + o[0], junk.getY(), junk.getZ() + o[1]);
            Block below = at.getRelative(BlockFace.DOWN);
            if ((below.getType().isSolid() || below.getType() == Material.FARMLAND)
                    && !SoulChestService.isChestLike(at.getType())
                    && at.getType() != Material.CRAFTING_TABLE
                    && (!at.getType().isSolid() || isVegetation(at.getType()) || at.equals(junk))) {
                return at.getLocation().add(0.5D, 0.0D, 0.5D);
            }
        }
        return junk.getLocation().add(0.5D, 1.0D, 0.5D);
    }

    public Block findSeatSpot(SoulGolemData data, int radius) {
        if (hasValidSeat(data)) {
            return seatBlock(data);
        }
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        int cx = (int) Math.floor(data.chestX());
        int cz = (int) Math.floor(data.chestZ());
        Block best = null;
        double bestDist = -1.0D;
        for (int x = homeX - r; x <= homeX + r; x++) {
            best = fartherSeat(best, bestDist, seatSpotAt(world, data, x, homeY, homeZ - r), cx, cz);
            if (best != null) {
                bestDist = horizontalDist(best, cx, cz);
            }
            best = fartherSeat(best, bestDist, seatSpotAt(world, data, x, homeY, homeZ + r), cx, cz);
            if (best != null) {
                bestDist = horizontalDist(best, cx, cz);
            }
        }
        for (int z = homeZ - r + 1; z <= homeZ + r - 1; z++) {
            best = fartherSeat(best, bestDist, seatSpotAt(world, data, homeX - r, homeY, z), cx, cz);
            if (best != null) {
                bestDist = horizontalDist(best, cx, cz);
            }
            best = fartherSeat(best, bestDist, seatSpotAt(world, data, homeX + r, homeY, z), cx, cz);
            if (best != null) {
                bestDist = horizontalDist(best, cx, cz);
            }
        }
        return best;
    }

    private static Block fartherSeat(Block current, double currentDist, Block candidate, int cx, int cz) {
        if (candidate == null) {
            return current;
        }
        double dist = horizontalDist(candidate, cx, cz);
        if (current == null || dist > currentDist) {
            return candidate;
        }
        return current;
    }

    private static double horizontalDist(Block block, int cx, int cz) {
        double dx = block.getX() - cx;
        double dz = block.getZ() - cz;
        return dx * dx + dz * dz;
    }

    private Block seatSpotAt(World world, SoulGolemData data, int x, int homeY, int z) {
        if (isWaterColumn(data, x, z)) {
            return null;
        }
        if (this.chestService.isChestColumn(data, world.getBlockAt(x, homeY, z))) {
            return null;
        }
        for (int y = homeY + 2; y >= homeY - 4; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid() || SoulChestService.isChestLike(ground.getType())
                    || ground.getType() == Material.CRAFTING_TABLE
                    || Tag.STAIRS.isTagged(ground.getType())) {
                continue;
            }
            Block above = ground.getRelative(BlockFace.UP);
            if (above.getType().isAir()
                    || isVegetation(above.getType())
                    || isTorchMaterial(above.getType())
                    || Tag.STAIRS.isTagged(above.getType())) {
                return above;
            }
            if (!above.getType().isSolid()) {
                continue;
            }
            return null;
        }
        return null;
    }

    public Block findTorchBlockingSeat(Block seatSpot) {
        if (seatSpot == null || seatSpot.getWorld() == null) {
            return null;
        }
        if (isTorchMaterial(seatSpot.getType())) {
            return seatSpot;
        }
        World world = seatSpot.getWorld();
        for (int dy = -1; dy <= 2; dy++) {
            Block block = world.getBlockAt(seatSpot.getX(), seatSpot.getY() + dy, seatSpot.getZ());
            if (isTorchMaterial(block.getType())) {
                return block;
            }
        }
        return null;
    }

    public boolean relocateTorchForSeat(SoulGolemData data, Block seatSpot, int radius, UUID golemId) {
        Block torch = findTorchBlockingSeat(seatSpot);
        if (torch == null) {
            return true;
        }
        Block destination = findAdjacentBorderTorchSpot(data, radius, torch, seatSpot);
        Material torchType = standingTorchType(torch.getType());
        torch.setType(Material.AIR, false);
        if (destination == null) {
            return true;
        }
        placeTorch(destination, torchType, golemId);
        return true;
    }

    private Material standingTorchType(Material type) {
        if (type == Material.SOUL_TORCH || type == Material.SOUL_WALL_TORCH) {
            return Material.SOUL_TORCH;
        }
        return Material.TORCH;
    }

    private Block findAdjacentBorderTorchSpot(SoulGolemData data, int radius, Block torch, Block seatSpot) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        int homeY = (int) Math.floor(data.homeY());
        List<int[]> ring = borderRing(data, radius);
        int index = -1;
        for (int i = 0; i < ring.size(); i++) {
            int[] pos = ring.get(i);
            if (pos[0] == torch.getX() && pos[1] == torch.getZ()) {
                index = i;
                break;
            }
            if (seatSpot != null && pos[0] == seatSpot.getX() && pos[1] == seatSpot.getZ()) {
                index = i;
            }
        }
        if (index < 0 && seatSpot != null) {
            for (int i = 0; i < ring.size(); i++) {
                int[] pos = ring.get(i);
                if (pos[0] == seatSpot.getX() && pos[1] == seatSpot.getZ()) {
                    index = i;
                    break;
                }
            }
        }
        if (index < 0) {
            index = 0;
        }
        int size = ring.size();
        for (int step = 1; step < size; step++) {
            for (int dir : new int[]{1, -1}) {
                int[] pos = ring.get(Math.floorMod(index + dir * step, size));
                if (seatSpot != null && pos[0] == seatSpot.getX() && pos[1] == seatSpot.getZ()) {
                    continue;
                }
                if (pos[0] == torch.getX() && pos[1] == torch.getZ()) {
                    continue;
                }
                Block spot = resolveTorchSpot(world, data, pos[0], homeY, pos[1], seatSpot);
                if (spot != null && spot.getType().isAir()) {
                    return spot;
                }
            }
        }
        return null;
    }

    private List<int[]> borderRing(SoulGolemData data, int radius) {
        List<int[]> ring = new ArrayList<>();
        int homeX = (int) Math.floor(data.homeX());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        for (int x = homeX - r; x <= homeX + r; x++) {
            ring.add(new int[]{x, homeZ - r});
        }
        for (int z = homeZ - r + 1; z <= homeZ + r; z++) {
            ring.add(new int[]{homeX + r, z});
        }
        for (int x = homeX + r - 1; x >= homeX - r; x--) {
            ring.add(new int[]{x, homeZ + r});
        }
        for (int z = homeZ + r - 1; z >= homeZ - r + 1; z--) {
            ring.add(new int[]{homeX - r, z});
        }
        return ring;
    }

    public Location standBeside(Block spot) {
        if (spot == null || spot.getWorld() == null) {
            return null;
        }
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] o : offsets) {
            Block at = spot.getRelative(o[0], 0, o[1]);
            Block below = at.getRelative(BlockFace.DOWN);
            if (SoulChestService.isChestLike(at.getType()) || at.getType() == Material.CRAFTING_TABLE) {
                continue;
            }
            if ((below.getType().isSolid() || below.getType() == Material.FARMLAND)
                    && (!at.getType().isSolid() || isVegetation(at.getType()) || isAnyCrop(at.getType()))) {
                return at.getLocation().add(0.5D, 0.0D, 0.5D);
            }
        }
        return spot.getLocation().add(0.5D, 0.0D, 0.5D);
    }

    public boolean hasValidSeat(SoulGolemData data) {
        if (!data.hasSeat()) {
            return false;
        }
        Block seat = seatBlock(data);
        if (seat != null && Tag.STAIRS.isTagged(seat.getType())) {
            return true;
        }
        data.clearSeatPosition();
        return false;
    }

    public Block seatBlock(SoulGolemData data) {
        if (!data.hasSeat()) {
            return null;
        }
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        return world.getBlockAt(
                (int) Math.floor(data.seatX()),
                (int) Math.floor(data.seatY()),
                (int) Math.floor(data.seatZ())
        );
    }

    public Location seatStandLocation(SoulGolemData data) {
        Block seat = seatBlock(data);
        if (seat == null) {
            return null;
        }
        return seat.getLocation().add(0.5D, 0.55D, 0.5D);
    }

    public boolean isSeatedOnOwnBench(Location location, SoulGolemData data) {
        if (!hasValidSeat(data) || location.getWorld() == null) {
            return false;
        }
        Block seat = seatBlock(data);
        if (seat == null) {
            return false;
        }
        if (location.getBlockX() != seat.getX() || location.getBlockZ() != seat.getZ()) {
            return false;
        }
        double y = location.getY();
        return y >= seat.getY() + 0.4D && y < seat.getY() + 1.6D;
    }

    public boolean placeSeat(SoulGolemData data, Block spot, Material stairs, UUID golemId) {
        if (spot == null) {
            return false;
        }
        if (isVegetation(spot.getType())) {
            clearVegetation(spot);
        }
        if (isTorchMaterial(spot.getType())) {
            spot.setType(Material.AIR, false);
        }
        if (!spot.getType().isAir() && !Tag.STAIRS.isTagged(spot.getType())) {
            return false;
        }
        Block below = spot.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid() && below.getType() != Material.FARMLAND) {
            return false;
        }
        BlockFace facing = faceTowardHome(data, spot.getX(), spot.getZ()).getOppositeFace();
        BlockData blockData = stairs.createBlockData();
        if (blockData instanceof org.bukkit.block.data.type.Stairs stairData) {
            stairData.setFacing(facing);
            stairData.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
            spot.setBlockData(stairData, false);
        } else {
            spot.setType(stairs, false);
        }
        if (!Tag.STAIRS.isTagged(spot.getType())) {
            return false;
        }
        data.seatPosition(spot.getX(), spot.getY(), spot.getZ());
        protect(spot, golemId);
        return true;
    }

    private static BlockFace faceTowardHome(SoulGolemData data, int x, int z) {
        int hx = (int) Math.floor(data.homeX());
        int hz = (int) Math.floor(data.homeZ());
        int dx = hx - x;
        int dz = hz - z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    public Material resolveBorderWool() {
        Material border = Material.matchMaterial(settings().borderMaterial);
        return border != null && border.isBlock() ? border : Material.PURPLE_WOOL;
    }

    public boolean isWorldStorming(World world) {
        return world != null
                && world.getEnvironment() == World.Environment.NORMAL
                && (world.hasStorm() || world.isThundering());
    }

    public boolean isRainedOn(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        if (!isWorldStorming(world)) {
            return false;
        }
        if (isDryBiome(world.getBiome(location))) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int highest = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING);
        return highest <= y;
    }

    private static boolean isDryBiome(org.bukkit.block.Biome biome) {
        if (biome == null) {
            return false;
        }
        String key = biome.getKey().getKey();
        return key.contains("desert")
                || key.contains("badlands")
                || key.contains("savanna")
                || key.contains("nether")
                || key.equals("the_void");
    }

    public boolean isRainedOn(CopperGolem copper) {
        if (copper == null || !copper.isValid()) {
            return false;
        }
        Location eye = copper.getLocation().add(0.0D, 1.0D, 0.0D);
        return isRainedOn(eye) || isRainedOn(copper.getLocation());
    }

    public int[] chestOutward(SoulGolemData data) {
        int homeX = (int) Math.floor(data.homeX());
        int homeZ = (int) Math.floor(data.homeZ());
        int cx = (int) Math.floor(data.chestX());
        int cz = (int) Math.floor(data.chestZ());
        int dx = cx - homeX;
        int dz = cz - homeZ;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[]{Integer.compare(dx, 0), 0};
        }
        return new int[]{0, Integer.compare(dz, 0)};
    }

    public Block rainShelterAnchor(SoulGolemData data) {
        if (hasValidSeat(data)) {
            return seatBlock(data);
        }
        return findSeatSpot(data, this.chestService.effectiveRadius(data));
    }

    public Location rainShelterStand(SoulGolemData data) {
        Block anchor = rainShelterAnchor(data);
        if (anchor == null || anchor.getWorld() == null) {
            return null;
        }
        double yOff = Tag.STAIRS.isTagged(anchor.getType()) ? 0.55D : 0.0D;
        return anchor.getLocation().add(0.5D, yOff, 0.5D);
    }

    public Location rainOpenSkyProbe(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        // Центр поля под открытым небом — не колонка навеса над ступенькой.
        return new Location(world, data.homeX(), Math.floor(data.homeY()) + 2.0D, data.homeZ());
    }

    public List<Block> rainShelterBlocks(SoulGolemData data) {
        List<Block> list = new ArrayList<>();
        Block anchor = rainShelterAnchor(data);
        if (anchor == null || anchor.getWorld() == null) {
            return list;
        }
        World world = anchor.getWorld();
        int ax = anchor.getX();
        int ay = anchor.getY();
        int az = anchor.getZ();
        // 1 шерсть над NPC на +3
        list.add(world.getBlockAt(ax, ay + 3, az));

        int[] corner = rainShelterFenceCorner(data, anchor);
        if (corner == null) {
            return list;
        }
        int homeY = (int) Math.floor(data.homeY());
        int baseY = homeY + 1;
        int cx = corner[0];
        int cz = corner[1];
        Block base = world.getBlockAt(cx, baseY, cz);
        boolean fenceAtBase = isFenceOrGate(base.getType(), data);

        // В углу всего 4 «этажа»: забор + 3 шерсти, либо 4 шерсти (низ потом станет забором).
        int from = fenceAtBase ? 1 : 0;
        int to = 3;
        for (int dy = from; dy <= to; dy++) {
            list.add(world.getBlockAt(cx, baseY + dy, cz));
        }
        return list;
    }

    /**
     * Угол внешнего заборного кольца (radius+1), ближайший к ступеньке / якорю навеса.
     */
    private int[] rainShelterFenceCorner(SoulGolemData data, Block anchor) {
        int radius = this.chestService.effectiveRadius(data);
        int fr = Math.max(1, radius) + 1;
        int homeX = (int) Math.floor(data.homeX());
        int homeZ = (int) Math.floor(data.homeZ());
        int[][] corners = {
                {homeX - fr, homeZ - fr},
                {homeX + fr, homeZ - fr},
                {homeX - fr, homeZ + fr},
                {homeX + fr, homeZ + fr}
        };
        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] corner : corners) {
            double dist = horizontalDistSq(corner[0] + 0.5D, corner[1] + 0.5D, anchor.getX() + 0.5D, anchor.getZ() + 0.5D);
            if (dist < bestDist) {
                bestDist = dist;
                best = corner;
            }
        }
        return best;
    }

    private boolean isFenceOrGate(Material type, SoulGolemData data) {
        if (type == null) {
            return false;
        }
        if (Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type)) {
            return true;
        }
        return type == resolveFenceMaterial(data) || type == resolveGateMaterial(data);
    }

    private static double horizontalDistSq(double x, double z, double ox, double oz) {
        double dx = x - ox;
        double dz = z - oz;
        return dx * dx + dz * dz;
    }

    public List<Block> missingRainShelterBlocks(SoulGolemData data) {
        Material wool = resolveBorderWool();
        List<Block> missing = new ArrayList<>();
        for (Block block : rainShelterBlocks(data)) {
            if (block.getType() != wool && !isBorderMaterial(block.getType())) {
                missing.add(block);
            }
        }
        return missing;
    }

    public boolean hasRainShelter(SoulGolemData data) {
        return missingRainShelterBlocks(data).isEmpty() && !rainShelterBlocks(data).isEmpty();
    }

    public boolean isUnderRainShelter(SoulGolemData data, Location location) {
        Location stand = rainShelterStand(data);
        if (stand == null || location == null || location.getWorld() == null || stand.getWorld() == null) {
            return false;
        }
        if (!stand.getWorld().equals(location.getWorld())) {
            return false;
        }
        if (GolemMovement.horizontalDistanceSquared(location, stand) > 1.0D) {
            return false;
        }
        Block roof = stand.getWorld().getBlockAt(stand.getBlockX(), stand.getBlockY() + 3, stand.getBlockZ());
        return roof.getType().isSolid();
    }

    public void placeRainShelterBlock(Block spot, UUID golemId) {
        if (spot == null) {
            return;
        }
        Material wool = resolveBorderWool();
        if (spot.getType() == wool || isBorderMaterial(spot.getType())) {
            protect(spot, golemId);
            return;
        }
        if (SoulChestService.isChestLike(spot.getType()) || spot.getType() == Material.CRAFTING_TABLE
                || Tag.STAIRS.isTagged(spot.getType())
                || Tag.FENCES.isTagged(spot.getType())
                || Tag.FENCE_GATES.isTagged(spot.getType())
                || isTorchMaterial(spot.getType())) {
            return;
        }
        if (isVegetation(spot.getType()) || spot.getType() == Material.SNOW || isClearableObstruction(spot, null)
                || spot.getType().isAir()) {
            BlockPos pos = BlockPos.of(spot);
            this.originals.putIfAbsent(pos, spot.getType());
            spot.setType(wool, false);
            protect(spot, golemId);
        }
    }

    public void removeSeat(SoulGolemData data) {
        Block seat = seatBlock(data);
        if (seat != null && Tag.STAIRS.isTagged(seat.getType())) {
            seat.setType(Material.AIR, false);
        }
        data.clearSeatPosition();
    }

    public void removeGolemArea(SoulGolemData data, int radius, OreTableService oreTable) {
        clear(data.id());
        removeSeat(data);
        removeBorderTorches(data, radius);

        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }

        Material border = resolveBorderMaterial();
        Material fence = resolveFenceMaterial(data);
        Material gate = resolveGateMaterial(data);
        Material wool = Material.matchMaterial(this.configurationLoader.config().settings().borderMaterial);
        if (wool == null || !wool.isBlock()) {
            wool = Material.PURPLE_WOOL;
        }

        for (Block block : rainShelterBlocks(data)) {
            revertPlacedBlock(block, border, fence, gate, wool);
        }
        for (Block block : outerFenceSlots(data, radius)) {
            if (block.getType() == fence) {
                block.setType(Material.AIR, false);
            }
        }
        Block gateSpot = outerFenceGateSpot(data, radius);
        if (gateSpot != null && (gateSpot.getType() == gate || Tag.FENCE_GATES.isTagged(gateSpot.getType()))) {
            gateSpot.setType(Material.AIR, false);
        }

        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        Material surface = Material.GRASS_BLOCK;

        for (int x = homeX - r; x <= homeX + r; x++) {
            for (int z = homeZ - r; z <= homeZ + r; z++) {
                if (this.chestService.isChestColumn(data, world.getBlockAt(x, homeY, z))) {
                    continue;
                }
                if (data.hasCraftStation()
                        && x == (int) Math.floor(data.craftX())
                        && z == (int) Math.floor(data.craftZ())) {
                    continue;
                }
                if (isWaterColumn(data, x, z)) {
                    Block water = world.getBlockAt(x, homeY, z);
                    if (water.getType() == Material.WATER) {
                        water.setType(Material.DIRT, false);
                    }
                }
                for (int y = homeY; y <= homeY + 4; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.isAir()) {
                        continue;
                    }
                    if (y == homeY) {
                        if (oreTable != null && oreTable.isOre(type)) {
                            block.setType(surface, false);
                        } else if (isBorderMaterial(type)) {
                            block.setType(surface, false);
                        } else if (type == Material.FARMLAND) {
                            block.setType(Material.DIRT, false);
                        }
                        continue;
                    }
                    if (isAnyCrop(type) || isTorchMaterial(type) || Tag.STAIRS.isTagged(type)) {
                        block.setType(Material.AIR, false);
                        continue;
                    }
                    if (Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type)) {
                        block.setType(Material.AIR, false);
                        continue;
                    }
                    if (isVegetation(type) || type == Material.SNOW) {
                        block.setType(Material.AIR, false);
                        continue;
                    }
                    if (type == wool || type == border) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private Material resolveBorderMaterial() {
        Material border = Material.matchMaterial(this.configurationLoader.config().settings().borderMaterial);
        return border != null && border.isBlock() ? border : Material.PURPLE_WOOL;
    }

    private static void revertPlacedBlock(Block block, Material border, Material fence, Material gate, Material wool) {
        Material type = block.getType();
        if (type == wool || type == border || type == fence || type == gate || Tag.FENCE_GATES.isTagged(type)) {
            block.setType(Material.AIR, false);
        }
    }

    public boolean isWaterColumn(SoulGolemData data, int x, int z) {
        return x == (int) Math.floor(data.homeX()) && z == (int) Math.floor(data.homeZ());
    }

    public boolean isWaterBlock(SoulGolemData data, Location location) {
        return isWaterColumn(data, location.getBlockX(), location.getBlockZ())
                && Math.abs(location.getBlockY() - (int) Math.floor(data.homeY())) <= 1;
    }

    public Location randomWanderPoint(SoulGolemData data, int radius) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        int max = Math.max(1, radius - 1);
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = homeX + random.nextInt(-max, max + 1);
            int z = homeZ + random.nextInt(-max, max + 1);
            if (isWaterColumn(data, x, z)) {
                continue;
            }
            if (this.chestService.isChestColumn(data, world.getBlockAt(x, homeY, z))) {
                continue;
            }
            Block ground = world.getBlockAt(x, homeY, z);
            if (!ground.getType().isSolid() && ground.getType() != Material.FARMLAND) {
                continue;
            }
            Block stand = ground.getRelative(BlockFace.UP);
            if (stand.getType().isSolid() && !isAnyCrop(stand.getType())) {
                continue;
            }
            return stand.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        return null;
    }

    public boolean hasAnyWheat(SoulGolemData data, int radius) {
        return hasAnyCrop(data, radius, List.of(CropType.WHEAT));
    }

    public boolean hasAnyCrop(SoulGolemData data, int radius, Collection<CropType> crops) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null || crops == null || crops.isEmpty()) {
            return false;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (isEnabledCrop(world.getBlockAt(homeX + x, homeY + 1, homeZ + z).getType(), crops)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void plantWheat(Block farmland, UUID golemId) {
        plantCrop(farmland, CropType.WHEAT, golemId);
    }

    public void plantCrop(Block farmland, CropType cropType, UUID golemId) {
        if (farmland == null || cropType == null) {
            return;
        }
        Block above = farmland.getRelative(BlockFace.UP);
        above.setType(cropType.crop(), false);
        if (golemId != null) {
            protect(above, golemId);
        }
    }

    public static boolean isEnabledCrop(Material material, Collection<CropType> crops) {
        CropType type = CropType.byCrop(material);
        return type != null && crops != null && crops.contains(type);
    }

    public static boolean isAnyCrop(Material material) {
        return CropType.byCrop(material) != null;
    }

    public boolean isFarmProtected(Block block) {
        return this.farmBlocks.containsKey(BlockPos.of(block)) || this.workAreaService.isProtected(block);
    }

    private void protect(Block block, UUID golemId) {
        if (golemId == null) {
            return;
        }
        BlockPos pos = BlockPos.of(block);
        this.farmBlocks.put(pos, golemId);
        this.byGolem.computeIfAbsent(golemId, id -> ConcurrentHashMap.newKeySet()).add(pos);
        this.workAreaService.protect(block, golemId);
    }

    public void clear(UUID golemId) {
        Set<BlockPos> keys = this.byGolem.remove(golemId);
        if (keys == null) {
            return;
        }
        for (BlockPos pos : keys) {
            this.farmBlocks.remove(pos);
            World world = Bukkit.getWorld(pos.worldId());
            Material original = this.originals.remove(pos);
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            Material type = block.getType();
            if (isAnyCrop(type) || isTorchMaterial(type) || Tag.STAIRS.isTagged(type)
                    || Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type)) {
                block.setType(Material.AIR, false);
                continue;
            }
            if (original != null) {
                block.setType(original, false);
            } else if (type == Material.WATER) {
                block.setType(Material.DIRT, false);
            } else if (type == Material.FARMLAND) {
                block.setType(Material.DIRT, false);
            }
        }
    }

    public void removeBorderTorches(SoulGolemData data, int radius) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        for (int x = homeX - r; x <= homeX + r; x++) {
            clearTorchColumn(world, x, homeY, homeZ - r);
            clearTorchColumn(world, x, homeY, homeZ + r);
        }
        for (int z = homeZ - r + 1; z <= homeZ + r - 1; z++) {
            clearTorchColumn(world, homeX - r, homeY, z);
            clearTorchColumn(world, homeX + r, homeY, z);
        }
    }

    private void clearTorchColumn(World world, int x, int homeY, int z) {
        for (int y = homeY - 1; y <= homeY + 3; y++) {
            Block block = world.getBlockAt(x, y, z);
            if (isTorchMaterial(block.getType())) {
                block.setType(Material.AIR, false);
            }
        }
    }

    public Location standOn(Block soilOrCrop) {
        Material type = soilOrCrop.getType();
        Block feet = isAnyCrop(type) || type.isAir()
                ? soilOrCrop
                : soilOrCrop.getRelative(BlockFace.UP);
        return feet.getLocation().add(0.5D, 0.0D, 0.5D);
    }

    public Location safeStandNearHome(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        int hx = (int) Math.floor(data.homeX());
        int hy = (int) Math.floor(data.homeY());
        int hz = (int) Math.floor(data.homeZ());
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] o : offsets) {
            int x = hx + o[0];
            int z = hz + o[1];
            if (isWaterColumn(data, x, z)) {
                continue;
            }
            Block ground = world.getBlockAt(x, hy, z);
            if (!ground.getType().isSolid() && ground.getType() != Material.FARMLAND) {
                continue;
            }
            if (SoulChestService.isChestLike(ground.getType()) || ground.getType() == Material.CRAFTING_TABLE) {
                continue;
            }
            Block above = ground.getRelative(BlockFace.UP);
            if (above.getType().isSolid() && !isAnyCrop(above.getType())) {
                continue;
            }
            return above.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        return new Location(world, hx + 1.5D, hy + 1.0D, hz + 0.5D);
    }

    public Location farmerSpawnStand(Block clicked) {
        int hx = clicked.getX();
        int hy = clicked.getY();
        int hz = clicked.getZ();
        World world = clicked.getWorld();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] o : offsets) {
            Block ground = world.getBlockAt(hx + o[0], hy, hz + o[1]);
            if (!ground.getType().isSolid() && ground.getType() != Material.FARMLAND) {
                continue;
            }
            Block above = ground.getRelative(BlockFace.UP);
            if (!above.getType().isAir()
                    && above.getType() != Material.SHORT_GRASS
                    && above.getType() != Material.TALL_GRASS) {
                continue;
            }
            return above.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        return clicked.getLocation().add(0.5D, 1.0D, 0.5D);
    }

    public boolean bodyClipped(Location location) {
        return bodyClipped(location, null);
    }

    public boolean bodyClipped(Location location, SoulGolemData data) {
        if (location.getWorld() == null) {
            return false;
        }
        for (double dy : new double[] {0.05D, 0.55D, 0.95D}) {
            Block block = location.getWorld().getBlockAt(
                    location.getBlockX(),
                    (int) Math.floor(location.getY() + dy),
                    location.getBlockZ()
            );
            Material type = block.getType();
            if (type.isAir() || isAnyCrop(type) || isVegetation(type)) {
                continue;
            }
            if (block.isLiquid() || type == Material.WATER || type == Material.LAVA) {
                return true;
            }
            if (type == Material.FARMLAND) {
                if (location.getY() < block.getY() + 0.95D) {
                    return true;
                }
                continue;
            }
            if (SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE) {
                return true;
            }
            if (Tag.STAIRS.isTagged(type)) {
                if (data != null && isOwnSeatBlock(block, data) && location.getY() >= block.getY() + 0.4D) {
                    continue;
                }
                if (location.getY() >= block.getY() + 0.45D) {
                    continue;
                }
                return true;
            }
            if (type.isSolid()) {
                return true;
            }
        }
        return false;
    }

    public boolean isOwnSeatBlock(Block block, SoulGolemData data) {
        if (block == null || data == null || !data.hasSeat()) {
            return false;
        }
        return block.getX() == (int) Math.floor(data.seatX())
                && block.getY() == (int) Math.floor(data.seatY())
                && block.getZ() == (int) Math.floor(data.seatZ())
                && block.getWorld() != null
                && block.getWorld().getName().equals(data.worldName());
    }

    public void reprotectSeat(SoulGolemData data) {
        Block seat = seatBlock(data);
        if (seat == null) {
            return;
        }
        protect(seat, data.id());
    }

    public boolean needsRescue(Location location, SoulGolemData data) {
        if (location.getWorld() == null) {
            return false;
        }
        if (isSeatedOnOwnBench(location, data)) {
            return false;
        }
        if (bodyClipped(location, data)) {
            return true;
        }
        if (isWaterColumn(data, location.getBlockX(), location.getBlockZ())) {
            return true;
        }
        Block at = location.getBlock();
        Block below = at.getRelative(BlockFace.DOWN);
        if (at.isLiquid() || below.isLiquid()) {
            return true;
        }
        int homeY = (int) Math.floor(data.homeY());
        return location.getY() < homeY - 0.2D;
    }

    private record BlockPos(UUID worldId, int x, int y, int z) {
        static BlockPos of(Block block) {
            return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
