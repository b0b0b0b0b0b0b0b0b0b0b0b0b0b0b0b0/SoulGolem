package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.PluginConfig;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Lidded;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class SoulChestService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final long LID_CLOSE_DELAY_TICKS = 15L;

    private final Plugin plugin;
    private final PluginKeys keys;
    private final PluginConfig config;
    private final Map<UUID, Integer> lidCloseEpoch = new ConcurrentHashMap<>();
    private String chestNameRaw;
    private String craftNameRaw;

    public SoulChestService(Plugin plugin, PluginKeys keys, PluginConfig config, String chestNameRaw, String craftNameRaw) {
        this.plugin = plugin;
        this.keys = keys;
        this.config = config;
        this.chestNameRaw = chestNameRaw;
        this.craftNameRaw = craftNameRaw;
    }

    public void updateStationNames(String chestNameRaw, String craftNameRaw) {
        this.chestNameRaw = chestNameRaw;
        this.craftNameRaw = craftNameRaw;
    }

    public void updateChestName(String chestNameRaw) {
        this.chestNameRaw = chestNameRaw;
    }

    public Location findChestLocation(Location homeBlockCenter, int radius) {
        World world = homeBlockCenter.getWorld();
        if (world == null) {
            return null;
        }
        int homeX = homeBlockCenter.getBlockX();
        int homeY = homeBlockCenter.getBlockY();
        int homeZ = homeBlockCenter.getBlockZ();
        int r = Math.max(1, radius);
        List<int[]> perimeter = new ArrayList<>();
        for (int x = homeX - r; x <= homeX + r; x++) {
            perimeter.add(new int[]{x, homeZ - r});
            perimeter.add(new int[]{x, homeZ + r});
        }
        for (int z = homeZ - r + 1; z <= homeZ + r - 1; z++) {
            perimeter.add(new int[]{homeX - r, z});
            perimeter.add(new int[]{homeX + r, z});
        }
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] pos : perimeter) {
            if (pos[0] == homeX && pos[1] == homeZ) {
                continue;
            }
            if (!canHostStationOnBorder(world, pos[0], homeY, pos[1])) {
                continue;
            }
            Location candidate = new Location(world, pos[0], homeY + 1, pos[1]);
            double dx = candidate.getX() + 0.5D - homeBlockCenter.getX();
            double dz = candidate.getZ() + 0.5D - homeBlockCenter.getZ();
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    public Location findCraftingTableLocation(Location chestBlock, Location home, int radius) {
        World world = chestBlock.getWorld();
        if (world == null) {
            return null;
        }
        int cx = chestBlock.getBlockX();
        int cz = chestBlock.getBlockZ();
        int hx = home.getBlockX();
        int hz = home.getBlockZ();
        int homeY = home.getBlockY();
        int r = Math.max(1, radius);

        Location best = null;
        double bestDist = Double.MAX_VALUE;
        int[][] neighbors = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        for (int[] n : neighbors) {
            int x = cx + n[0];
            int z = cz + n[1];
            if (!onPerimeter(x, z, hx, hz, r)) {
                continue;
            }
            if (x == cx && z == cz) {
                continue;
            }
            if (!canHostStationOnBorder(world, x, homeY, z)) {
                continue;
            }
            Location spot = new Location(world, x, homeY + 1, z);
            double dist = (x - cx) * (x - cx) + (z - cz) * (z - cz);
            if (dist < bestDist) {
                bestDist = dist;
                best = spot;
            }
        }
        if (best != null) {
            return best;
        }

        for (int x = hx - r; x <= hx + r; x++) {
            best = closerCraftBorder(best, bestDist, world, x, homeY, hz - r, cx, cz, hx, hz, r);
            if (best != null) {
                bestDist = horizontalDistSq(best, cx, cz);
            }
            best = closerCraftBorder(best, bestDist, world, x, homeY, hz + r, cx, cz, hx, hz, r);
            if (best != null) {
                bestDist = horizontalDistSq(best, cx, cz);
            }
        }
        for (int z = hz - r + 1; z <= hz + r - 1; z++) {
            best = closerCraftBorder(best, bestDist, world, hx - r, homeY, z, cx, cz, hx, hz, r);
            if (best != null) {
                bestDist = horizontalDistSq(best, cx, cz);
            }
            best = closerCraftBorder(best, bestDist, world, hx + r, homeY, z, cx, cz, hx, hz, r);
            if (best != null) {
                bestDist = horizontalDistSq(best, cx, cz);
            }
        }
        return best;
    }

    private Location closerCraftBorder(
            Location current,
            double currentDist,
            World world,
            int x,
            int homeY,
            int z,
            int cx,
            int cz,
            int hx,
            int hz,
            int r
    ) {
        if (Math.abs(x - cx) + Math.abs(z - cz) > 2) {
            return current;
        }
        if (x == cx && z == cz) {
            return current;
        }
        if (!onPerimeter(x, z, hx, hz, r) || !canHostStationOnBorder(world, x, homeY, z)) {
            return current;
        }
        Location candidate = new Location(world, x, homeY + 1, z);
        double dist = horizontalDistSq(candidate, cx, cz);
        if (current == null || dist < currentDist) {
            return candidate;
        }
        return current;
    }

    private static double horizontalDistSq(Location loc, int cx, int cz) {
        int dx = loc.getBlockX() - cx;
        int dz = loc.getBlockZ() - cz;
        return dx * dx + dz * dz;
    }

    private static boolean canHostStationOnBorder(World world, int x, int homeY, int z) {
        Block floor = world.getBlockAt(x, homeY, z);
        Material floorType = floor.getType();
        if (floorType == Material.WATER || floorType == Material.LAVA || floorType == Material.BEDROCK) {
            return false;
        }
        Block station = world.getBlockAt(x, homeY + 1, z);
        Material stationType = station.getType();
        if (isChestLike(stationType) || stationType == Material.CRAFTING_TABLE) {
            return false;
        }
        if (stationType == Material.BEDROCK || stationType == Material.OBSIDIAN) {
            return false;
        }
        return true;
    }

    public void clearStationColumn(Location stationLoc) {
        if (stationLoc == null || stationLoc.getWorld() == null) {
            return;
        }
        World world = stationLoc.getWorld();
        int x = stationLoc.getBlockX();
        int baseY = stationLoc.getBlockY();
        int z = stationLoc.getBlockZ();
        for (int y = baseY; y <= baseY + 6; y++) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type.isAir()) {
                continue;
            }
            if (isChestLike(type) || type == Material.CRAFTING_TABLE) {
                continue;
            }
            if (!canClearForStation(type)) {
                continue;
            }
            block.setType(Material.AIR, false);
        }
    }

    private static boolean canClearForStation(Material type) {
        if (type.isAir() || type == Material.WATER || type == Material.LAVA) {
            return false;
        }
        if (type == Material.BEDROCK || type == Material.OBSIDIAN || type == Material.BARRIER) {
            return false;
        }
        if (isChestLike(type) || type == Material.CRAFTING_TABLE) {
            return false;
        }
        return type.isSolid()
                || type == Material.SNOW
                || FarmAreaService.isVegetation(type)
                || isReplaceable(type);
    }

    private static boolean isReplaceable(Material type) {
        return type.isAir()
                || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.SNOW
                || type == Material.DEAD_BUSH
                || type.name().endsWith("_CARPET");
    }

    private static boolean onPerimeter(int x, int z, int hx, int hz, int r) {
        boolean onEdgeX = Math.abs(x - hx) == r && z >= hz - r && z <= hz + r;
        boolean onEdgeZ = Math.abs(z - hz) == r && x >= hx - r && x <= hx + r;
        return onEdgeX || onEdgeZ;
    }

    public static boolean isChestLike(Material type) {
        return type == Material.CHEST || Tag.COPPER_CHESTS.isTagged(type);
    }

    public void placeChest(Location location, UUID golemId, UUID ownerUuid) {
        placeChest(location, golemId, ownerUuid, null);
    }

    public void placeChest(Location location, UUID golemId, UUID ownerUuid, Location faceToward) {
        Block block = location.getBlock();
        ensureStationSupport(block);
        BlockData blockData = Material.COPPER_CHEST.createBlockData();
        if (blockData instanceof Directional directional) {
            directional.setFacing(faceTowardHome(location, faceToward));
            block.setBlockData(directional, false);
        } else {
            block.setType(Material.COPPER_CHEST, false);
        }
        applyChestMeta(block, golemId, ownerUuid);
    }

    public void tagExistingChest(Location location, UUID golemId, UUID ownerUuid) {
        tagExistingChest(location, golemId, ownerUuid, null);
    }

    public void tagExistingChest(Location location, UUID golemId, UUID ownerUuid, Location faceToward) {
        Block block = location.getBlock();
        if (!isChestLike(block.getType())) {
            placeChest(location, golemId, ownerUuid, faceToward);
            return;
        }
        ensureStationSupport(block);
        if (faceToward != null && block.getBlockData() instanceof Directional directional) {
            directional.setFacing(faceTowardHome(location, faceToward));
            block.setBlockData(directional, false);
        }
        applyChestMeta(block, golemId, ownerUuid);
    }

    private static org.bukkit.block.BlockFace faceTowardHome(Location chest, Location home) {
        if (chest == null) {
            return org.bukkit.block.BlockFace.NORTH;
        }
        double hx;
        double hz;
        if (home != null) {
            hx = home.getX();
            hz = home.getZ();
        } else {
            hx = chest.getX();
            hz = chest.getZ() - 1.0D;
        }
        double dx = hx - (chest.getBlockX() + 0.5D);
        double dz = hz - (chest.getBlockZ() + 0.5D);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? org.bukkit.block.BlockFace.EAST : org.bukkit.block.BlockFace.WEST;
        }
        return dz >= 0 ? org.bukkit.block.BlockFace.SOUTH : org.bukkit.block.BlockFace.NORTH;
    }

    public void openLid(SoulGolemData data) {
        if (!(chestAt(data) instanceof Lidded lidded)) {
            return;
        }
        if (!lidded.isOpen()) {
            lidded.open();
        }
    }

    public void closeLid(SoulGolemData data) {
        this.lidCloseEpoch.merge(data.id(), 1, Integer::sum);
        closeLidNow(data);
    }

    public void scheduleCloseLid(SoulGolemData data) {
        Chest chest = chestAt(data);
        if (chest == null) {
            return;
        }
        UUID golemId = data.id();
        int epoch = this.lidCloseEpoch.getOrDefault(golemId, 0);
        Location loc = chest.getLocation();
        PluginSchedulers.runAtLater(this.plugin, loc, () -> {
            if (this.lidCloseEpoch.getOrDefault(golemId, 0) != epoch) {
                return;
            }
            closeLidNow(data);
        }, LID_CLOSE_DELAY_TICKS);
    }

    private void closeLidNow(SoulGolemData data) {
        if (!(chestAt(data) instanceof Lidded lidded)) {
            return;
        }
        if (lidded.isOpen()) {
            lidded.close();
        }
    }

    public void placeCraftingTable(Location location, UUID golemId, UUID ownerUuid) {
        Block block = location.getBlock();
        ensureStationSupport(block);
        block.setType(Material.CRAFTING_TABLE, false);
        spawnOrRefreshCraftHologram(block, golemId);
    }

    public void tagExistingCraftingTable(Location location, UUID golemId, UUID ownerUuid) {
        Block block = location.getBlock();
        if (block.getType() != Material.CRAFTING_TABLE) {
            placeCraftingTable(location, golemId, ownerUuid);
            return;
        }
        ensureStationSupport(block);
        spawnOrRefreshCraftHologram(block, golemId);
    }

    private void ensureStationSupport(Block station) {
        Block below = station.getRelative(0, -1, 0);
        Material type = below.getType();
        if (type.isSolid()
                && type != Material.FARMLAND
                && type != Material.WATER
                && !FarmAreaService.isVegetation(type)
                && type != Material.SNOW) {
            return;
        }
        Material fill = Material.matchMaterial(this.config.settings().borderMaterial);
        if (fill == null || !fill.isBlock() || !fill.isSolid()) {
            fill = Material.DIRT;
        }
        below.setType(fill, false);
    }

    private void applyChestMeta(Block block, UUID golemId, UUID ownerUuid) {
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        Component name = MINI.deserialize(this.chestNameRaw == null ? "<#C084FC>Soul Chest</#C084FC>" : this.chestNameRaw);
        chest.customName(name);
        chest.getPersistentDataContainer().set(this.keys.chestGolemId(), PersistentDataType.STRING, golemId.toString());
        chest.getPersistentDataContainer().set(this.keys.owner(), PersistentDataType.STRING, ownerUuid.toString());
        chest.update(true, false);
        spawnOrRefreshHologram(block, golemId, name);
    }

    private void spawnOrRefreshHologram(Block block, UUID golemId, Component name) {
        Settings.TextDisplays style = this.config.settings().visuals.textDisplays;
        if (!style.enabled) {
            return;
        }
        Location at = block.getLocation().add(0.5D, style.chestOffsetY, 0.5D);
        for (org.bukkit.entity.Entity nearby : block.getWorld().getNearbyEntities(at, 0.6D, 0.6D, 0.6D)) {
            if (nearby instanceof TextDisplay display) {
                String raw = display.getPersistentDataContainer().get(this.keys.chestGolemId(), PersistentDataType.STRING);
                if (golemId.toString().equals(raw)) {
                    display.text(name);
                    TextDisplayStyle.applyChestHologram(display, style);
                    display.teleport(at);
                    return;
                }
            }
        }
        block.getWorld().spawn(at, TextDisplay.class, display -> {
            display.text(name);
            display.getPersistentDataContainer().set(this.keys.chestGolemId(), PersistentDataType.STRING, golemId.toString());
            TextDisplayStyle.applyChestHologram(display, style);
        });
    }

    public void removeHologram(SoulGolemData data) {
        removeChestHologram(data);
        removeCraftHologram(data);
    }

    public void removeChestHologram(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }
        float offsetY = this.config.settings().visuals.textDisplays.chestOffsetY;
        Location at = new Location(world, data.chestX() + 0.5D, data.chestY() + offsetY, data.chestZ() + 0.5D);
        removeDisplaysAt(at, this.keys.chestGolemId(), data.id().toString(), 1.5D);
    }

    public void removeCraftHologram(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null || !data.hasCraftStation()) {
            return;
        }
        float offsetY = this.config.settings().visuals.textDisplays.chestOffsetY;
        Location at = new Location(
                world,
                Math.floor(data.craftX()) + 0.5D,
                data.craftY() + offsetY,
                Math.floor(data.craftZ()) + 0.5D
        );
        removeDisplaysAt(at, this.keys.craftGolemId(), data.id().toString(), 2.0D);
        int radius = effectiveRadius(data);
        Location home = new Location(world, data.homeX(), data.homeY() + offsetY, data.homeZ());
        removeDisplaysAt(home, this.keys.craftGolemId(), data.id().toString(), radius + 2.0D);
    }

    private void removeDisplaysAt(Location center, org.bukkit.NamespacedKey key, String golemId, double range) {
        if (center.getWorld() == null) {
            return;
        }
        for (org.bukkit.entity.Entity nearby : center.getWorld().getNearbyEntities(center, range, range, range)) {
            if (!(nearby instanceof TextDisplay display)) {
                continue;
            }
            String raw = display.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (golemId.equals(raw)) {
                display.remove();
            }
        }
    }

    private void spawnOrRefreshCraftHologram(Block block, UUID golemId) {
        Settings.TextDisplays style = this.config.settings().visuals.textDisplays;
        if (!style.enabled) {
            return;
        }
        Component name = MINI.deserialize(
                this.craftNameRaw == null ? "<#C084FC>Soul Craft</#C084FC>" : this.craftNameRaw
        );
        Location at = block.getLocation().add(0.5D, style.chestOffsetY, 0.5D);
        for (org.bukkit.entity.Entity nearby : block.getWorld().getNearbyEntities(at, 0.8D, 0.8D, 0.8D)) {
            if (nearby instanceof TextDisplay display) {
                String raw = display.getPersistentDataContainer().get(this.keys.craftGolemId(), PersistentDataType.STRING);
                if (golemId.toString().equals(raw)) {
                    display.text(name);
                    TextDisplayStyle.applyChestHologram(display, style);
                    display.teleport(at);
                    return;
                }
            }
        }
        block.getWorld().spawn(at, TextDisplay.class, display -> {
            display.text(name);
            display.getPersistentDataContainer().set(this.keys.craftGolemId(), PersistentDataType.STRING, golemId.toString());
            TextDisplayStyle.applyChestHologram(display, style);
        });
    }

    public void removeCraftingTable(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }
        removeCraftHologram(data);
        if (data.hasCraftStation()) {
            Block craft = world.getBlockAt(
                    (int) Math.floor(data.craftX()),
                    (int) Math.floor(data.craftY()),
                    (int) Math.floor(data.craftZ())
            );
            if (craft.getType() == Material.CRAFTING_TABLE) {
                craft.setType(Material.AIR, false);
            }
        }
        int radius = effectiveRadius(data);
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        for (int x = homeX - radius; x <= homeX + radius; x++) {
            for (int z = homeZ - radius; z <= homeZ + radius; z++) {
                for (int y = homeY - 2; y <= homeY + 3; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.CRAFTING_TABLE) {
                        continue;
                    }
                    if (isSoulCraftingTable(block, data) || craftHologramMatches(block, data.id())) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private boolean craftHologramMatches(Block block, UUID golemId) {
        Settings.TextDisplays style = this.config.settings().visuals.textDisplays;
        Location at = block.getLocation().add(0.5D, style.chestOffsetY, 0.5D);
        for (org.bukkit.entity.Entity nearby : block.getWorld().getNearbyEntities(at, 0.8D, 0.8D, 0.8D)) {
            if (!(nearby instanceof TextDisplay display)) {
                continue;
            }
            String raw = display.getPersistentDataContainer().get(this.keys.craftGolemId(), PersistentDataType.STRING);
            if (golemId.toString().equals(raw)) {
                return true;
            }
        }
        return false;
    }

    public void removeChestBlock(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return;
        }
        removeChestHologram(data);
        Block chest = world.getBlockAt(
                (int) Math.floor(data.chestX()),
                (int) Math.floor(data.chestY()),
                (int) Math.floor(data.chestZ())
        );
        if (isChestLike(chest.getType())) {
            chest.setType(Material.AIR, false);
        }
    }

    public void removeStationsNear(Location center, UUID golemId, int radius) {
        if (center == null || center.getWorld() == null || golemId == null) {
            return;
        }
        World world = center.getWorld();
        String id = golemId.toString();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int r = Math.max(1, radius);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = cy - 3; y <= cy + 5; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isChestLike(block.getType())) {
                        UUID tagged = golemIdFromChest(block);
                        if (golemId.equals(tagged)) {
                            block.setType(Material.AIR, false);
                        }
                    } else if (block.getType() == Material.CRAFTING_TABLE) {
                        UUID tagged = golemIdFromCraft(block);
                        if (golemId.equals(tagged) || craftHologramMatches(block, golemId)) {
                            block.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }
        Location hologramCenter = center.clone().add(0.0D, 1.0D, 0.0D);
        removeDisplaysAt(hologramCenter, this.keys.chestGolemId(), id, r + 4.0D);
        removeDisplaysAt(hologramCenter, this.keys.craftGolemId(), id, r + 4.0D);
        GolemDisplay.removeAllNear(world, center, r + 16.0D, id, this.keys);
    }

    public boolean isChestPresent(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return false;
        }
        Block chest = world.getBlockAt(
                (int) Math.floor(data.chestX()),
                (int) Math.floor(data.chestY()),
                (int) Math.floor(data.chestZ())
        );
        return isChestLike(chest.getType()) && isSoulChest(chest);
    }

    public boolean isCraftPresent(SoulGolemData data) {
        if (!data.hasCraftStation()) {
            return false;
        }
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return false;
        }
        Block craft = world.getBlockAt(
                (int) Math.floor(data.craftX()),
                (int) Math.floor(data.craftY()),
                (int) Math.floor(data.craftZ())
        );
        return craft.getType() == Material.CRAFTING_TABLE;
    }

    public Location chestStandLocation(SoulGolemData data) {
        return standBeside(data, (int) Math.floor(data.chestX()), (int) Math.floor(data.chestY()), (int) Math.floor(data.chestZ()));
    }

    public Location craftStandLocation(SoulGolemData data) {
        if (!data.hasCraftStation()) {
            return null;
        }
        return standBeside(data, (int) Math.floor(data.craftX()), (int) Math.floor(data.craftY()), (int) Math.floor(data.craftZ()));
    }

    private Location standBeside(SoulGolemData data, int cx, int cy, int cz) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        int hx = (int) Math.floor(data.homeX());
        int hz = (int) Math.floor(data.homeZ());
        int preferX = Integer.compare(hx, cx);
        int preferZ = Integer.compare(hz, cz);
        if (preferX == 0 && preferZ == 0) {
            preferX = 1;
        }
        int[][] offsets = {
                {preferX, preferZ},
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        for (int[] offset : offsets) {
            if (offset[0] == 0 && offset[1] == 0) {
                continue;
            }
            Block stand = world.getBlockAt(cx + offset[0], cy, cz + offset[1]);
            if (isStationBlock(stand.getType())) {
                continue;
            }
            Block below = stand.getRelative(0, -1, 0);
            if (!stand.getType().isSolid() && below.getType().isSolid()) {
                return stand.getLocation().add(0.5D, 0.0D, 0.5D);
            }
            if (stand.getType().isSolid() && !stand.getRelative(0, 1, 0).getType().isSolid()) {
                return stand.getLocation().add(0.5D, 1.0D, 0.5D);
            }
        }
        return new Location(world, cx + 1.5D, cy, cz + 0.5D);
    }

    public boolean collidesWithChest(SoulGolemData data, Location location) {
        return collidesWithStation(data, location);
    }

    public boolean collidesWithStation(SoulGolemData data, Location location) {
        if (collidesColumn(data.chestX(), data.chestY(), data.chestZ(), location)) {
            return true;
        }
        return data.hasCraftStation() && collidesColumn(data.craftX(), data.craftY(), data.craftZ(), location);
    }

    private static boolean collidesColumn(double sx, double sy, double sz, Location location) {
        int cx = (int) Math.floor(sx);
        int cy = (int) Math.floor(sy);
        int cz = (int) Math.floor(sz);
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (x == cx && z == cz && (y == cy || y == cy - 1 || y == cy + 1)) {
            return true;
        }
        double dx = location.getX() - (cx + 0.5D);
        double dz = location.getZ() - (cz + 0.5D);
        return dx * dx + dz * dz < 0.55D && Math.abs(location.getY() - cy) < 1.4D;
    }

    public boolean isChestColumn(SoulGolemData data, Block block) {
        int cx = (int) Math.floor(data.chestX());
        int cy = (int) Math.floor(data.chestY());
        int cz = (int) Math.floor(data.chestZ());
        if (block.getX() == cx && block.getZ() == cz && (block.getY() == cy || block.getY() == cy - 1)) {
            return true;
        }
        if (!data.hasCraftStation()) {
            return false;
        }
        int fx = (int) Math.floor(data.craftX());
        int fy = (int) Math.floor(data.craftY());
        int fz = (int) Math.floor(data.craftZ());
        return block.getX() == fx && block.getZ() == fz && (block.getY() == fy || block.getY() == fy - 1);
    }

    public boolean isStationBlock(Material type) {
        return isChestLike(type) || type == Material.CRAFTING_TABLE;
    }

    public boolean isSoulChest(Block block) {
        if (!(block.getState() instanceof TileState state)) {
            return false;
        }
        return state.getPersistentDataContainer().has(this.keys.chestGolemId(), PersistentDataType.STRING);
    }

    public boolean isSoulCraftingTable(Block block, SoulGolemData data) {
        if (block.getType() != Material.CRAFTING_TABLE || !data.hasCraftStation()) {
            return false;
        }
        if (block.getX() == (int) Math.floor(data.craftX())
                && block.getY() == (int) Math.floor(data.craftY())
                && block.getZ() == (int) Math.floor(data.craftZ())) {
            return true;
        }
        return craftHologramMatches(block, data.id());
    }

    public boolean isSoulCraftingTable(Block block) {
        if (block.getType() != Material.CRAFTING_TABLE) {
            return false;
        }
        return golemIdFromCraft(block) != null;
    }

    public UUID golemIdFromCraft(Block block) {
        if (block.getType() != Material.CRAFTING_TABLE) {
            return null;
        }
        Settings.TextDisplays style = this.config.settings().visuals.textDisplays;
        Location at = block.getLocation().add(0.5D, style.chestOffsetY, 0.5D);
        for (org.bukkit.entity.Entity nearby : block.getWorld().getNearbyEntities(at, 0.8D, 0.8D, 0.8D)) {
            if (!(nearby instanceof TextDisplay display)) {
                continue;
            }
            String raw = display.getPersistentDataContainer().get(this.keys.craftGolemId(), PersistentDataType.STRING);
            if (raw != null && !raw.isEmpty()) {
                return UUID.fromString(raw);
            }
        }
        return null;
    }

    public UUID golemIdFromChest(Block block) {
        if (!(block.getState() instanceof TileState state)) {
            return null;
        }
        String raw = state.getPersistentDataContainer().get(this.keys.chestGolemId(), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return UUID.fromString(raw);
    }

    public UUID ownerFromChest(Block block) {
        if (!(block.getState() instanceof TileState state)) {
            return null;
        }
        String raw = state.getPersistentDataContainer().get(this.keys.owner(), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return UUID.fromString(raw);
    }

    public boolean deposit(SoulGolemData data, ItemStack stack) {
        Chest chest = chestAt(data);
        if (chest == null) {
            return false;
        }
        Inventory inventory = chest.getBlockInventory();
        return inventory.addItem(stack).isEmpty();
    }

    public boolean hasSpace(SoulGolemData data) {
        Chest chest = chestAt(data);
        if (chest == null) {
            return false;
        }
        return chest.getBlockInventory().firstEmpty() != -1;
    }

    public Material findBestCombatWeapon(SoulGolemData data) {
        Chest chest = chestAt(data);
        if (chest == null) {
            return null;
        }
        Material best = null;
        int bestScore = -1;
        for (ItemStack stack : chest.getBlockInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Material type = stack.getType();
            if (!GolemCombatWork.isWeapon(type)) {
                continue;
            }
            int score = GolemCombatWork.weaponScore(type);
            if (score > bestScore) {
                bestScore = score;
                best = type;
            }
        }
        return best;
    }

    public int countItem(SoulGolemData data, Material material) {
        Chest chest = chestAt(data);
        if (chest == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : chest.getBlockInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();            }
        }
        return total;
    }

    public Material findStairsInChest(SoulGolemData data) {
        Chest chest = chestAt(data);
        if (chest == null) {
            return null;
        }
        for (ItemStack stack : chest.getBlockInventory().getContents()) {
            if (stack != null && !stack.isEmpty() && Tag.STAIRS.isTagged(stack.getType())) {
                return stack.getType();
            }
        }
        return null;
    }

    public boolean takeItem(SoulGolemData data, Material material, int amount) {
        Chest chest = chestAt(data);
        if (chest == null || amount <= 0) {
            return false;
        }
        Inventory inventory = chest.getBlockInventory();
        if (countItem(data, material) < amount) {
            return false;
        }
        int left = amount;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                inventory.setItem(i, null);
            }
            left -= take;
        }
        return left == 0;
    }

    public boolean craftBread(SoulGolemData data) {
        if (countItem(data, Material.WHEAT) < 3) {
            return false;
        }
        if (!takeItem(data, Material.WHEAT, 3)) {
            return false;
        }
        if (!deposit(data, new ItemStack(Material.BREAD, 1))) {
            deposit(data, new ItemStack(Material.WHEAT, 3));
            return false;
        }
        return true;
    }

    private Chest chestAt(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        Block block = world.getBlockAt(
                (int) Math.floor(data.chestX()),
                (int) Math.floor(data.chestY()),
                (int) Math.floor(data.chestZ())
        );
        if (!(block.getState() instanceof Chest chest)) {
            return null;
        }
        return chest;
    }

    public int effectiveRadius(SoulGolemData data) {
        Settings settings = this.config.settings();
        double multiplier = 1.0D;
        for (Settings.LevelStats level : settings.levels) {
            if (level.level == data.level()) {
                multiplier = level.radiusMultiplier;
                break;
            }
        }
        return Math.max(1, (int) Math.round(settings.workRadius * multiplier));
    }
}
