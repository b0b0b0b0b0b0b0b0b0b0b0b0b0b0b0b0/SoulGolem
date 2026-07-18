package bm.b0b0b0.SoulGolem.service.digger;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class DiggerSafety {

    private DiggerSafety() {
    }

    public static boolean hasSolidSupport(Location stand) {
        if (stand == null || stand.getWorld() == null) {
            return false;
        }
        Block feet = stand.getBlock();
        Block below = feet.getRelative(BlockFace.DOWN);
        Material under = below.getType();
        if (under.isSolid() || under == Material.FARMLAND || Tag.STAIRS.isTagged(under) || Tag.SLABS.isTagged(under)) {
            return true;
        }
        Material at = feet.getType();
        return Tag.STAIRS.isTagged(at) || Tag.SLABS.isTagged(at) || at.isSolid();
    }

    public static boolean needsRescue(
            Location location,
            SoulGolemData data,
            FarmAreaService farmArea,
            GolemSettings.Digger digger
    ) {
        if (location.getWorld() == null) {
            return false;
        }
        Block at = location.getBlock();
        Block below = at.getRelative(BlockFace.DOWN);
        if (at.isLiquid() || below.isLiquid()) {
            return true;
        }
        if (farmArea.isWaterColumn(data, location.getBlockX(), location.getBlockZ())) {
            return true;
        }
        if (data.hasDigProgress()) {
            int radius = DiggerPit.radius(digger);
            int x = location.getBlockX();
            int z = location.getBlockZ();
            if (DiggerPit.isInsidePit(data, x, z, radius)) {
                int layerY = data.digLayerY();
                double y = location.getY();
                if (y >= layerY - 0.5D && y <= data.digStartY() + 2.5D) {
                    return !hasSolidSupport(location);
                }
            }
        }
        if (!farmArea.insideWoolBorder(data, location)) {
            return true;
        }
        if (hasSolidSupport(location)) {
            int layerY = data.hasDigProgress() ? data.digLayerY() : (int) Math.floor(data.homeY());
            int caveSafe = Math.max(1, digger.caveSafeDepth);
            return location.getY() < layerY - caveSafe - 2.0D;
        }
        return true;
    }

    public static Location rescueStand(
            SoulGolemData data,
            FarmAreaService farmArea,
            SoulChestService chestService,
            GolemSettings.Digger digger
    ) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        if (data.hasDigProgress()) {
            int radius = DiggerPit.radius(digger);
            int stairIndex = Math.max(0, data.digStairIndex() - 1);
            int y = data.digLayerY();
            for (int i = 0; i < 12; i++) {
                int idx = Math.max(0, stairIndex - i);
                int[] walk = DiggerPit.stairWalkCell(data, radius, idx);
                for (int dy = 1; dy >= 0; dy--) {
                    Block block = world.getBlockAt(walk[0], y + dy, walk[1]);
                    if (Tag.STAIRS.isTagged(block.getType()) || block.getType().isSolid()) {
                        Location stand = DiggerPit.stairStandInward(world, data, walk, y + dy);
                        if (hasSolidSupport(stand) || Tag.STAIRS.isTagged(block.getType())) {
                            return stand;
                        }
                    }
                }
            }
        }
        Location chest = chestService.chestStandLocation(data);
        if (chest != null && hasSolidSupport(chest)) {
            return chest;
        }
        return farmArea.safeStandNearHome(data);
    }
}
