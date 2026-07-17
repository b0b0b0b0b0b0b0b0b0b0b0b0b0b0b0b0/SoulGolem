package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.util.ArrayList;
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
                        && above.getType() != Material.WHEAT) {
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
                || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.PINK_PETALS
                || type == Material.LEAF_LITTER) {
            return true;
        }
        String name = type.name();
        return name.endsWith("_SAPLING") || name.equals("GRASS") || name.equals("DOUBLE_PLANT");
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
                if (!above.getType().isAir() && above.getType() != Material.WHEAT && !isVegetation(above.getType())) {
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
            list.addAll(perimeterObstructions(data, radius));
        }
        list.addAll(fieldWeeds(data, radius));
        return list;
    }

    public List<Block> minerJunkToClear(SoulGolemData data, int radius, OreTableService oreTable) {
        List<Block> list = new ArrayList<>();
        if (!this.configurationLoader.config().settings().miner.clearArea) {
            return list;
        }
        for (Block block : perimeterObstructions(data, radius)) {
            if (oreTable != null && oreTable.isOre(block.getType())) {
                continue;
            }
            list.add(block);
        }
        list.addAll(areaSurfaceVegetation(data, radius));
        return list;
    }

    private List<Block> areaSurfaceVegetation(SoulGolemData data, int radius) {
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
                Block ground = world.getBlockAt(homeX + x, homeY, homeZ + z);
                if (this.chestService.isChestColumn(data, ground)
                        || SoulChestService.isChestLike(ground.getType())
                        || ground.getType() == Material.CRAFTING_TABLE
                        || isBorderWool(ground.getType())) {
                    continue;
                }
                Block above = ground.getRelative(BlockFace.UP);
                if (isVegetation(above.getType()) || above.getType() == Material.SNOW) {
                    list.add(above);
                }
            }
        }
        return list;
    }

    public List<Block> fieldWeeds(SoulGolemData data, int radius) {
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
                if (this.chestService.isChestColumn(data, soil)
                        || soil.getType() == Material.CRAFTING_TABLE
                        || SoulChestService.isChestLike(soil.getType())
                        || isBorderWool(soil.getType())) {
                    continue;
                }
                Block above = soil.getRelative(BlockFace.UP);
                if (isVegetation(above.getType())) {
                    list.add(above);
                }
                Block upper = above.getRelative(BlockFace.UP);
                if (isVegetation(upper.getType()) && !containsBlock(list, upper)) {
                    list.add(upper);
                }
            }
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
                Block crop = world.getBlockAt(homeX + x, homeY + 1, homeZ + z);
                if (crop.getType() != Material.WHEAT) {
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
                Block crop = world.getBlockAt(homeX + x, homeY + 1, homeZ + z);
                if (crop.getType() != Material.WHEAT) {
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

    private int countBorderTorches(SoulGolemData data, int radius) {
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
            addObstructions(list, world, data, x, homeY, homeZ - r);
            addObstructions(list, world, data, x, homeY, homeZ + r);
        }
        for (int z = homeZ - r + 1; z <= homeZ + r - 1; z++) {
            addObstructions(list, world, data, homeX - r, homeY, z);
            addObstructions(list, world, data, homeX + r, homeY, z);
        }
        return list;
    }

    private void addObstructions(List<Block> list, World world, SoulGolemData data, int x, int homeY, int z) {
        if (isWaterColumn(data, x, z)) {
            return;
        }
        if (this.chestService.isChestColumn(data, world.getBlockAt(x, homeY, z))) {
            return;
        }
        Block ground = null;
        for (int y = homeY + 2; y >= homeY - 4; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid()
                    && !SoulChestService.isChestLike(block.getType())
                    && block.getType() != Material.CRAFTING_TABLE
                    && !Tag.STAIRS.isTagged(block.getType())) {
                ground = block;
                break;
            }
        }
        if (ground == null) {
            return;
        }
        for (int y = ground.getY() + 1; y <= ground.getY() + 4; y++) {
            Block above = world.getBlockAt(x, y, z);
            if (above.getType().isAir()) {
                break;
            }
            if (isBorderObstruction(above, data)) {
                list.add(above);
            }
        }
    }

    private boolean isBorderObstruction(Block block, SoulGolemData data) {
        Material type = block.getType();
        if (type.isAir() || type == Material.WATER || type == Material.LAVA) {
            return false;
        }
        if (SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE) {
            return false;
        }
        if (Tag.STAIRS.isTagged(type)) {
            return false;
        }
        if (isTorchMaterial(type)) {
            return false;
        }
        if (type == Material.WHEAT) {
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
        String configured = this.configurationLoader.config().settings().farmer.torchMaterial;
        Material match = Material.matchMaterial(configured == null ? "TORCH" : configured);
        return match != null ? match : Material.TORCH;
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
                    && (!at.getType().isSolid() || isVegetation(at.getType()) || at.getType() == Material.WHEAT)) {
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
        BlockFace facing = faceTowardHome(data, spot.getX(), spot.getZ());
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

    public void removeSeat(SoulGolemData data) {
        Block seat = seatBlock(data);
        if (seat != null && Tag.STAIRS.isTagged(seat.getType())) {
            seat.setType(Material.AIR, false);
        }
        data.clearSeatPosition();
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
            if (stand.getType().isSolid() && stand.getType() != Material.WHEAT) {
                continue;
            }
            return stand.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        return null;
    }

    public boolean hasAnyWheat(SoulGolemData data, int radius) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return false;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (world.getBlockAt(homeX + x, homeY + 1, homeZ + z).getType() == Material.WHEAT) {
                    return true;
                }
            }
        }
        return false;
    }

    public void plantWheat(Block farmland, UUID golemId) {
        Block above = farmland.getRelative(BlockFace.UP);
        above.setType(Material.WHEAT, false);
        if (golemId != null) {
            protect(above, golemId);
        }
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
            if (type == Material.WHEAT || isTorchMaterial(type) || Tag.STAIRS.isTagged(type)) {
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
        Block feet = type == Material.WHEAT || type.isAir()
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
            if (above.getType().isSolid() && above.getType() != Material.WHEAT) {
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
            if (type.isAir() || type == Material.WHEAT || isVegetation(type)) {
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

    private boolean isOwnSeatBlock(Block block, SoulGolemData data) {
        if (!hasValidSeat(data)) {
            return false;
        }
        Block seat = seatBlock(data);
        return seat != null && seat.getX() == block.getX() && seat.getY() == block.getY() && seat.getZ() == block.getZ();
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
