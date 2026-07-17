package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.PluginConfig;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class SoulChestService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final PluginKeys keys;
    private final PluginConfig config;
    private String chestNameRaw;
    private String craftNameRaw;

    public SoulChestService(PluginKeys keys, PluginConfig config, String chestNameRaw, String craftNameRaw) {
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
            Block ground = findSurface(world, pos[0], homeY, pos[1]);
            if (ground == null) {
                continue;
            }
            Block above = ground.getRelative(0, 1, 0);
            if (!isReplaceable(above.getType())) {
                continue;
            }
            Location candidate = above.getLocation();
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
        int cy = chestBlock.getBlockY();
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
            Location spot = craftSpotAt(world, x, cy, z, homeY);
            if (spot == null) {
                continue;
            }
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
            best = closerCraft(best, bestDist, craftSpotNearChest(world, x, homeY, hz - r, cx, cy, cz), cx, cz);
            if (best != null) {
                bestDist = (best.getBlockX() - cx) * (best.getBlockX() - cx)
                        + (best.getBlockZ() - cz) * (best.getBlockZ() - cz);
            }
            best = closerCraft(best, bestDist, craftSpotNearChest(world, x, homeY, hz + r, cx, cy, cz), cx, cz);
            if (best != null) {
                bestDist = (best.getBlockX() - cx) * (best.getBlockX() - cx)
                        + (best.getBlockZ() - cz) * (best.getBlockZ() - cz);
            }
        }
        for (int z = hz - r + 1; z <= hz + r - 1; z++) {
            best = closerCraft(best, bestDist, craftSpotNearChest(world, hx - r, homeY, z, cx, cy, cz), cx, cz);
            if (best != null) {
                bestDist = (best.getBlockX() - cx) * (best.getBlockX() - cx)
                        + (best.getBlockZ() - cz) * (best.getBlockZ() - cz);
            }
            best = closerCraft(best, bestDist, craftSpotNearChest(world, hx + r, homeY, z, cx, cy, cz), cx, cz);
            if (best != null) {
                bestDist = (best.getBlockX() - cx) * (best.getBlockX() - cx)
                        + (best.getBlockZ() - cz) * (best.getBlockZ() - cz);
            }
        }
        return best;
    }

    private static boolean onPerimeter(int x, int z, int hx, int hz, int r) {
        boolean onEdgeX = Math.abs(x - hx) == r && z >= hz - r && z <= hz + r;
        boolean onEdgeZ = Math.abs(z - hz) == r && x >= hx - r && x <= hx + r;
        return onEdgeX || onEdgeZ;
    }

    private static Location closerCraft(Location current, double currentDist, Location candidate, int cx, int cz) {
        if (candidate == null) {
            return current;
        }
        double dist = (candidate.getBlockX() - cx) * (candidate.getBlockX() - cx)
                + (candidate.getBlockZ() - cz) * (candidate.getBlockZ() - cz);
        if (current == null || dist < currentDist) {
            return candidate;
        }
        return current;
    }

    private Location craftSpotNearChest(World world, int x, int homeY, int z, int cx, int cy, int cz) {
        if (Math.abs(x - cx) + Math.abs(z - cz) > 2) {
            return null;
        }
        if (x == cx && z == cz) {
            return null;
        }
        return craftSpotAt(world, x, cy, z, homeY);
    }

    private Location craftSpotAt(World world, int x, int cy, int z, int homeY) {
        Block atChestY = world.getBlockAt(x, cy, z);
        if (isChestLike(atChestY.getType())) {
            return null;
        }
        if ((isReplaceable(atChestY.getType()) || atChestY.getType() == Material.CRAFTING_TABLE)
                && supportsCraft(atChestY)) {
            return atChestY.getLocation();
        }
        Block ground = findSurface(world, x, homeY, z);
        if (ground == null) {
            return null;
        }
        Block above = ground.getRelative(0, 1, 0);
        if (isChestLike(above.getType())) {
            return null;
        }
        if (isReplaceable(above.getType()) || above.getType() == Material.CRAFTING_TABLE) {
            return above.getLocation();
        }
        return null;
    }

    private static boolean supportsCraft(Block craftBlock) {
        Block below = craftBlock.getRelative(0, -1, 0);
        Material type = below.getType();
        if (type == Material.FARMLAND || type == Material.WATER) {
            return false;
        }
        return type.isSolid();
    }

    private static Block findSurface(World world, int x, int homeY, int z) {
        for (int y = homeY + 2; y >= homeY - 8; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid()
                    && !isChestLike(block.getType())
                    && block.getType() != Material.CRAFTING_TABLE) {
                return block;
            }
        }
        return null;
    }

    private static boolean isReplaceable(Material type) {
        return type.isAir()
                || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.SNOW
                || type == Material.DEAD_BUSH
                || type.name().endsWith("_CARPET");
    }

    public static boolean isChestLike(Material type) {
        return type == Material.CHEST || Tag.COPPER_CHESTS.isTagged(type);
    }

    public void placeChest(Location location, UUID golemId, UUID ownerUuid) {
        Block block = location.getBlock();
        ensureStationSupport(block);
        block.setType(Material.COPPER_CHEST, false);
        applyChestMeta(block, golemId, ownerUuid);
    }

    public void tagExistingChest(Location location, UUID golemId, UUID ownerUuid) {
        Block block = location.getBlock();
        if (!isChestLike(block.getType())) {
            placeChest(location, golemId, ownerUuid);
            return;
        }
        ensureStationSupport(block);
        applyChestMeta(block, golemId, ownerUuid);
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

    public int countItem(SoulGolemData data, Material material) {
        Chest chest = chestAt(data);
        if (chest == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : chest.getBlockInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
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
