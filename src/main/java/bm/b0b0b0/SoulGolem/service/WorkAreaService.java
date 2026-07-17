package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class WorkAreaService {

    private final ConfigurationLoader configurationLoader;
    private final Map<BlockPos, UUID> protectedBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<BlockPos>> byGolem = new ConcurrentHashMap<>();
    private final Map<BlockPos, Material> originals = new ConcurrentHashMap<>();

    public WorkAreaService(ConfigurationLoader configurationLoader) {
        this.configurationLoader = configurationLoader;
    }

    private Settings settings() {
        return this.configurationLoader.config().settings();
    }

    public void setupArea(SoulGolemData data, int radius) {
        clear(data.id());
        if (!settings().borderEnabled) {
            return;
        }
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }
        Material border = resolveBorder();
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int minX = homeX - radius;
        int maxX = homeX + radius;
        int minZ = homeZ - radius;
        int maxZ = homeZ + radius;

        for (int x = minX; x <= maxX; x++) {
            placeBorderFloor(world, x, homeY, minZ, border, data.id());
            placeBorderFloor(world, x, homeY, maxZ, border, data.id());
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            placeBorderFloor(world, minX, homeY, z, border, data.id());
            placeBorderFloor(world, maxX, homeY, z, border, data.id());
        }
    }

    public void seedOres(SoulGolemData data, int radius, OreTableService oreTable) {
        int max = Math.max(1, settings().maxActiveOres);
        int current = countOres(data, radius, oreTable);
        if (current >= max) {
            return;
        }
        List<Block> candidates = collectTransformable(data, radius, oreTable);
        if (candidates.isEmpty()) {
            return;
        }
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        int need = max - current;
        for (int i = 0; i < need && i < candidates.size(); i++) {
            placeOre(candidates.get(i), data.id(), oreTable.rollOre());
        }
    }

    public int countOres(SoulGolemData data, int radius, OreTableService oreTable) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return 0;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int count = 0;
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Block block = world.getBlockAt(homeX + x, homeY, homeZ + z);
                if (SoulChestService.isChestLike(block.getType())) {
                    continue;
                }
                if (isUnderOrChest(data, block)) {
                    continue;
                }
                if (oreTable.isOre(block.getType())) {
                    count++;
                }
            }
        }
        return count;
    }

    private List<Block> collectTransformable(SoulGolemData data, int radius, OreTableService oreTable) {
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
                Block block = world.getBlockAt(homeX + x, homeY, homeZ + z);
                if (SoulChestService.isChestLike(block.getType())) {
                    continue;
                }
                if (isUnderOrChest(data, block)) {
                    continue;
                }
                if (oreTable.isOre(block.getType())) {
                    continue;
                }
                if (!oreTable.isTransformable(block.getType())) {
                    continue;
                }
                list.add(block);
            }
        }
        return list;
    }

    private boolean isUnderOrChest(SoulGolemData data, Block block) {
        int cx = (int) Math.floor(data.chestX());
        int cy = (int) Math.floor(data.chestY());
        int cz = (int) Math.floor(data.chestZ());
        if (block.getX() != cx || block.getZ() != cz) {
            return false;
        }
        return block.getY() == cy || block.getY() == cy - 1;
    }

    public void placeOre(Block block, UUID golemId, Material ore) {
        BlockPos pos = BlockPos.of(block);
        this.originals.putIfAbsent(pos, block.getType());
        block.setType(ore, false);
        protect(block, golemId);
    }

    public void restoreBlock(Block block) {
        BlockPos pos = BlockPos.of(block);
        Material original = this.originals.remove(pos);
        UUID golemId = this.protectedBlocks.remove(pos);
        if (golemId != null) {
            Set<BlockPos> set = this.byGolem.get(golemId);
            if (set != null) {
                set.remove(pos);
            }
        }
        if (original != null) {
            block.setType(original, false);
        }
    }

    public void placeBorderBlock(SoulGolemData data, int x, int homeY, int z) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null || !settings().borderEnabled) {
            return;
        }
        placeBorderFloorExact(world, x, homeY, z, resolveBorder(), data.id());
    }

    private void placeBorderFloorExact(World world, int x, int homeY, int z, Material border, UUID golemId) {
        Block atHome = world.getBlockAt(x, homeY, z);
        Block aboveHome = world.getBlockAt(x, homeY + 1, z);
        if (SoulChestService.isChestLike(atHome.getType()) || atHome.getType() == Material.CRAFTING_TABLE) {
            ensureSupportUnder(atHome, border, golemId);
            return;
        }
        if (SoulChestService.isChestLike(aboveHome.getType()) || aboveHome.getType() == Material.CRAFTING_TABLE) {
            ensureSupportUnder(aboveHome, border, golemId);
            return;
        }
        if (atHome.getType() == border) {
            return;
        }
        if (FarmAreaService.isVegetation(aboveHome.getType()) || aboveHome.getType() == Material.SNOW) {
            aboveHome.setType(Material.AIR, false);
        }
        Block upper = world.getBlockAt(x, homeY + 2, z);
        if (FarmAreaService.isVegetation(upper.getType()) || upper.getType() == Material.SNOW) {
            upper.setType(Material.AIR, false);
        }
        BlockPos pos = BlockPos.of(atHome);
        this.originals.putIfAbsent(pos, atHome.getType());
        atHome.setType(border, false);
        protect(atHome, golemId);
    }

    public List<Location> borderFloorSlots(SoulGolemData data, int radius) {
        List<Location> list = new ArrayList<>();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null || !settings().borderEnabled) {
            return list;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        int minX = homeX - r;
        int maxX = homeX + r;
        int minZ = homeZ - r;
        int maxZ = homeZ + r;
        Material border = resolveBorder();
        for (int x = minX; x <= maxX; x++) {
            addBorderSlotIfNeeded(list, world, x, homeY, minZ, border);
        }
        for (int z = minZ + 1; z <= maxZ; z++) {
            addBorderSlotIfNeeded(list, world, maxX, homeY, z, border);
        }
        for (int x = maxX - 1; x >= minX; x--) {
            addBorderSlotIfNeeded(list, world, x, homeY, maxZ, border);
        }
        for (int z = maxZ - 1; z > minZ; z--) {
            addBorderSlotIfNeeded(list, world, minX, homeY, z, border);
        }
        return list;
    }

    private static void addBorderSlotIfNeeded(List<Location> list, World world, int x, int homeY, int z, Material border) {
        Block ground = world.getBlockAt(x, homeY, z);
        if (ground.getType() == border) {
            return;
        }
        if (SoulChestService.isChestLike(ground.getType()) || ground.getType() == Material.CRAFTING_TABLE) {
            return;
        }
        list.add(ground.getLocation());
    }

    private void placeBorderFloor(World world, int x, int homeY, int z, Material border, UUID golemId) {
        Block atHome = world.getBlockAt(x, homeY, z);
        Block aboveHome = world.getBlockAt(x, homeY + 1, z);
        if (SoulChestService.isChestLike(atHome.getType()) || atHome.getType() == Material.CRAFTING_TABLE) {
            ensureSupportUnder(atHome, border, golemId);
            return;
        }
        if (SoulChestService.isChestLike(aboveHome.getType()) || aboveHome.getType() == Material.CRAFTING_TABLE) {
            Block support = aboveHome.getRelative(0, -1, 0);
            if (needsStationSupport(support.getType())) {
                BlockPos pos = BlockPos.of(support);
                this.originals.putIfAbsent(pos, support.getType());
                support.setType(border, false);
                protect(support, golemId);
            }
            return;
        }
        Block ground = findTerrainGround(world, x, homeY, z);
        if (ground == null) {
            ground = world.getBlockAt(x, homeY, z);
            if (ground.getType().isAir() || !ground.getType().isSolid()) {
                BlockPos pos = BlockPos.of(ground);
                this.originals.putIfAbsent(pos, ground.getType());
                ground.setType(border, false);
                protect(ground, golemId);
            }
            return;
        }
        if (SoulChestService.isChestLike(ground.getType()) || ground.getType() == Material.CRAFTING_TABLE) {
            ensureSupportUnder(ground, border, golemId);
            return;
        }
        Block above = ground.getRelative(0, 1, 0);
        if (SoulChestService.isChestLike(above.getType()) || above.getType() == Material.CRAFTING_TABLE) {
            ensureSupportUnder(above, border, golemId);
            return;
        }
        if (FarmAreaService.isVegetation(above.getType())) {
            above.setType(Material.AIR, false);
            Block upper = above.getRelative(0, 1, 0);
            if (FarmAreaService.isVegetation(upper.getType())) {
                upper.setType(Material.AIR, false);
            }
        }
        BlockPos pos = BlockPos.of(ground);
        this.originals.putIfAbsent(pos, ground.getType());
        ground.setType(border, false);
        protect(ground, golemId);
    }

    private void ensureSupportUnder(Block station, Material border, UUID golemId) {
        Block support = station.getRelative(0, -1, 0);
        if (!needsStationSupport(support.getType())) {
            return;
        }
        BlockPos pos = BlockPos.of(support);
        this.originals.putIfAbsent(pos, support.getType());
        support.setType(border, false);
        protect(support, golemId);
    }

    private static boolean needsStationSupport(Material type) {
        if (type.isAir() || type == Material.WATER || type == Material.LAVA || type == Material.FARMLAND) {
            return true;
        }
        if (FarmAreaService.isVegetation(type) || type == Material.SNOW) {
            return true;
        }
        return !type.isSolid();
    }

    private Block findTerrainGround(World world, int x, int homeY, int z) {
        for (int y = homeY; y >= homeY - 12; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (isTerrainBlock(block.getType())) {
                return block;
            }
        }
        return null;
    }

    private boolean isTerrainBlock(Material type) {
        if (!type.isSolid() || type.isAir()) {
            return false;
        }
        if (SoulChestService.isChestLike(type)) {
            return false;
        }
        if (Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) {
            return false;
        }
        if (Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type) || Tag.WALLS.isTagged(type)) {
            return false;
        }
        if (Tag.WOOL.isTagged(type)) {
            return false;
        }
        String name = type.name();
        if (name.contains("LEAVES") || name.contains("LOG") || name.contains("STEM") || name.contains("HYPHAE")) {
            return false;
        }
        if (name.contains("GLASS") || name.contains("ICE")) {
            return false;
        }
        return true;
    }

    public void protect(Block block, UUID golemId) {
        BlockPos pos = BlockPos.of(block);
        UUID previous = this.protectedBlocks.put(pos, golemId);
        if (previous != null && !previous.equals(golemId)) {
            Set<BlockPos> oldSet = this.byGolem.get(previous);
            if (oldSet != null) {
                oldSet.remove(pos);
            }
        }
        this.byGolem.computeIfAbsent(golemId, id -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    public boolean isProtected(Block block) {
        return this.protectedBlocks.containsKey(BlockPos.of(block));
    }

    public void clear(UUID golemId) {
        Set<BlockPos> keys = this.byGolem.remove(golemId);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (BlockPos pos : keys) {
            this.protectedBlocks.remove(pos);
            World world = Bukkit.getWorld(pos.worldId());
            if (world == null) {
                this.originals.remove(pos);
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            Material original = this.originals.remove(pos);
            if (original != null) {
                block.setType(original, false);
            }
        }
    }

    public void removeGolemArea(SoulGolemData data, int radius, OreTableService oreTable) {
        clear(data.id());
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        Material border = resolveBorder();
        Material surface = Material.GRASS_BLOCK;

        for (int x = homeX - r; x <= homeX + r; x++) {
            for (int z = homeZ - r; z <= homeZ + r; z++) {
                if (isStationColumn(data, world, x, homeZ, z)) {
                    continue;
                }
                Block ground = world.getBlockAt(x, homeY, z);
                Material type = ground.getType();
                if (oreTable != null && oreTable.isOre(type)) {
                    ground.setType(surface, false);
                } else if (type == border) {
                    ground.setType(surface, false);
                }
            }
        }
    }

    private static boolean isStationColumn(SoulGolemData data, World world, int x, int homeZ, int z) {
        if (data.hasCraftStation()
                && x == (int) Math.floor(data.craftX())
                && z == (int) Math.floor(data.craftZ())) {
            return true;
        }
        int cx = (int) Math.floor(data.chestX());
        int cz = (int) Math.floor(data.chestZ());
        return x == cx && z == cz;
    }

    public void removeGolemArea(SoulGolemData data, int radius) {
        removeGolemArea(data, radius, null);
    }

    public Location homeLocation(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        return new Location(world, data.homeX() + 0.5D, data.homeY(), data.homeZ() + 0.5D);
    }

    public boolean containsBlock(SoulGolemData data, int radius, Block block) {
        if (block == null || data == null || block.getWorld() == null) {
            return false;
        }
        if (!block.getWorld().getName().equals(data.worldName())) {
            return false;
        }
        int r = Math.max(1, radius);
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        if (Math.abs(x - homeX) > r || Math.abs(z - homeZ) > r) {
            return false;
        }
        return y >= homeY - 2 && y <= homeY + 8;
    }

    public java.util.Optional<UUID> findTerritoryGolemId(
            Block block,
            Iterable<bm.b0b0b0.SoulGolem.model.ActiveGolem> golems,
            java.util.function.ToIntFunction<SoulGolemData> radiusOf
    ) {
        if (block == null) {
            return java.util.Optional.empty();
        }
        UUID mapped = this.protectedBlocks.get(BlockPos.of(block));
        if (mapped != null) {
            return java.util.Optional.of(mapped);
        }
        for (bm.b0b0b0.SoulGolem.model.ActiveGolem golem : golems) {
            SoulGolemData data = golem.data();
            if (containsBlock(data, radiusOf.applyAsInt(data), block)) {
                return java.util.Optional.of(data.id());
            }
        }
        return java.util.Optional.empty();
    }

    public boolean isTerritoryBlock(
            Block block,
            Iterable<bm.b0b0b0.SoulGolem.model.ActiveGolem> golems,
            java.util.function.ToIntFunction<SoulGolemData> radiusOf
    ) {
        return findTerritoryGolemId(block, golems, radiusOf).isPresent();
    }

    public void reclaimTerritory(SoulGolemData data, int radius) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }
        int r = Math.max(1, radius);
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        UUID golemId = data.id();
        for (int x = homeX - r; x <= homeX + r; x++) {
            for (int z = homeZ - r; z <= homeZ + r; z++) {
                for (int y = homeY - 2; y <= homeY + 8; y++) {
                    protect(world.getBlockAt(x, y, z), golemId);
                }
            }
        }
    }

    private Material resolveBorder() {
        Material border = Material.matchMaterial(settings().borderMaterial);
        if (border == null || !border.isBlock()) {
            return Material.PURPLE_WOOL;
        }
        return border;
    }

    private record BlockPos(UUID worldId, int x, int y, int z) {
        static BlockPos of(Block block) {
            return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
