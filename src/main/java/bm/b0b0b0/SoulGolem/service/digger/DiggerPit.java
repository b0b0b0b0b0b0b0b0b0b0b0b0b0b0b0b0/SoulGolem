package bm.b0b0b0.SoulGolem.service.digger;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;

public final class DiggerPit {

    private DiggerPit() {
    }

    public static int radius(GolemSettings.Digger digger) {
        return Math.max(2, digger.pitSize / 2);
    }

    public static int digMinX(SoulGolemData data, int radius) {
        return (int) Math.floor(data.homeX()) - (radius - 1);
    }

    public static int digMaxX(SoulGolemData data, int radius) {
        return (int) Math.floor(data.homeX()) + (radius - 1);
    }

    public static int digMinZ(SoulGolemData data, int radius) {
        return (int) Math.floor(data.homeZ()) - (radius - 1);
    }

    public static int digMaxZ(SoulGolemData data, int radius) {
        return (int) Math.floor(data.homeZ()) + (radius - 1);
    }

    public static int minX(SoulGolemData data, int half) {
        return digMinX(data, half);
    }

    public static int maxX(SoulGolemData data, int half) {
        return digMaxX(data, half);
    }

    public static int minZ(SoulGolemData data, int half) {
        return digMinZ(data, half);
    }

    public static int maxZ(SoulGolemData data, int half) {
        return digMaxZ(data, half);
    }

    public static boolean isBorderColumn(SoulGolemData data, int x, int z, int radius) {
        int hx = (int) Math.floor(data.homeX());
        int hz = (int) Math.floor(data.homeZ());
        int minX = hx - radius;
        int maxX = hx + radius;
        int minZ = hz - radius;
        int maxZ = hz + radius;
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        return x == minX || x == maxX || z == minZ || z == maxZ;
    }

    public static boolean isInsidePit(SoulGolemData data, int x, int z, int radius) {
        return x >= digMinX(data, radius) && x <= digMaxX(data, radius)
                && z >= digMinZ(data, radius) && z <= digMaxZ(data, radius);
    }

    public static boolean isStationColumn(SoulGolemData data, int x, int z) {
        int cx = (int) Math.floor(data.chestX());
        int cz = (int) Math.floor(data.chestZ());
        if (x == cx && z == cz) {
            return true;
        }
        if (!data.hasCraftStation()) {
            return false;
        }
        return x == (int) Math.floor(data.craftX()) && z == (int) Math.floor(data.craftZ());
    }

    public static List<int[]> stairRingClockwise(SoulGolemData data, int radius) {
        int minX = digMinX(data, radius);
        int maxX = digMaxX(data, radius);
        int minZ = digMinZ(data, radius);
        int maxZ = digMaxZ(data, radius);
        List<int[]> ring = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            ring.add(new int[]{x, minZ});
        }
        for (int z = minZ + 1; z <= maxZ; z++) {
            ring.add(new int[]{maxX, z});
        }
        for (int x = maxX - 1; x >= minX; x--) {
            ring.add(new int[]{x, maxZ});
        }
        for (int z = maxZ - 1; z > minZ; z--) {
            ring.add(new int[]{minX, z});
        }
        return ring;
    }

    public static List<int[]> perimeterClockwise(SoulGolemData data, int half) {
        return stairRingClockwise(data, half);
    }

    public static int stairStartIndex(SoulGolemData data, int radius) {
        List<int[]> ring = stairRingClockwise(data, radius);
        if (ring.isEmpty()) {
            return 0;
        }
        int chestX = (int) Math.floor(data.chestX());
        int chestZ = (int) Math.floor(data.chestZ());
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < ring.size(); i++) {
            int[] p = ring.get(i);
            if (isStationColumn(data, p[0], p[1]) || isBorderColumn(data, p[0], p[1], radius)) {
                continue;
            }
            double dx = p[0] + 0.5D - (chestX + 0.5D);
            double dz = p[1] + 0.5D - (chestZ + 0.5D);
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    public static int[] stairCell(SoulGolemData data, int radius, int stairIndex) {
        List<int[]> ring = stairRingClockwise(data, radius);
        if (ring.isEmpty()) {
            return new int[]{(int) Math.floor(data.homeX()), (int) Math.floor(data.homeZ())};
        }
        int start = stairStartIndex(data, radius);
        int size = ring.size();
        for (int offset = 0; offset < size; offset++) {
            int idx = Math.floorMod(start + stairIndex + offset, size);
            int[] cell = ring.get(idx);
            if (!isStationColumn(data, cell[0], cell[1]) && !isBorderColumn(data, cell[0], cell[1], radius)) {
                return cell;
            }
        }
        int idx = Math.floorMod(start + stairIndex, size);
        return ring.get(idx);
    }

    public static BlockFace stairFacing(SoulGolemData data, int radius, int stairIndex) {
        int[] cell = stairCell(data, radius, stairIndex);
        int[] prev = stairCell(data, radius, stairIndex - 1);
        int dx = prev[0] - cell[0];
        int dz = prev[1] - cell[1];
        if (Math.abs(dx) >= Math.abs(dz)) {
            if (dx > 0) {
                return BlockFace.EAST;
            }
            if (dx < 0) {
                return BlockFace.WEST;
            }
        }
        if (dz > 0) {
            return BlockFace.SOUTH;
        }
        if (dz < 0) {
            return BlockFace.NORTH;
        }
        double hx = data.homeX() - (cell[0] + 0.5D);
        double hz = data.homeZ() - (cell[1] + 0.5D);
        if (Math.abs(hx) >= Math.abs(hz)) {
            return hx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return hz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    public static Material stairSupportMaterial(GolemSettings.Digger digger) {
        Material support = Material.matchMaterial(digger.stairSupportMaterial);
        if (support == null || !support.isBlock() || !support.isSolid() || Tag.STAIRS.isTagged(support)) {
            return Material.STONE;
        }
        if (support == Material.LAVA || support == Material.WATER || support == Material.MAGMA_BLOCK) {
            return Material.STONE;
        }
        return support;
    }

    public static boolean isCornerStairIndex(SoulGolemData data, int radius, int stairIndex) {
        return stairShape(data, radius, stairIndex) != Stairs.Shape.STRAIGHT;
    }

    public static int[] stairWalkCell(SoulGolemData data, int radius, int stairIndex) {
        if (isCornerStairIndex(data, radius, stairIndex)) {
            return stairCell(data, radius, stairIndex + 1);
        }
        return stairCell(data, radius, stairIndex);
    }

    public static int stairSteps(SoulGolemData data, int radius, int stairIndex) {
        return isCornerStairIndex(data, radius, stairIndex) ? 2 : 1;
    }

    public static int stairIndexForY(SoulGolemData data, int radius, int y) {
        if (!data.hasDigProgress()) {
            return 0;
        }
        int startY = Math.min(data.digStartY(), (int) Math.floor(data.homeY()));
        if (y >= startY) {
            return 0;
        }
        int index = 0;
        for (int sy = startY; sy > y; sy--) {
            index += stairSteps(data, radius, index);
        }
        return index;
    }

    private static boolean canPlaceStairStructure(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return !SoulChestService.isChestLike(type)
                && type != Material.CRAFTING_TABLE
                && type != Material.COMPOSTER
                && type != Material.BEDROCK
                && type != Material.BARRIER
                && type != Material.COMMAND_BLOCK;
    }

    private static void placeSupportBlock(World world, int x, int y, int z, Material support) {
        Block block = world.getBlockAt(x, y, z);
        if (!canPlaceStairStructure(block)) {
            return;
        }
        if (Tag.STAIRS.isTagged(block.getType())) {
            return;
        }
        block.setType(support, false);
    }

    private static void placeLandingBlock(World world, int x, int y, int z, Material support) {
        Block block = world.getBlockAt(x, y, z);
        if (!canPlaceStairStructure(block)) {
            return;
        }
        block.setType(support, false);
    }

    public static int placeStair(World world, SoulGolemData data, GolemSettings.Digger digger, int y, int stairIndex) {
        if (y > (int) Math.floor(data.homeY())) {
            return 1;
        }
        Material stairMat = Material.matchMaterial(digger.stairMaterial);
        if (stairMat == null || !Tag.STAIRS.isTagged(stairMat)) {
            stairMat = Material.STONE_STAIRS;
        }
        Material support = stairSupportMaterial(digger);
        int radius = radius(digger);
        int[] cell = stairCell(data, radius, stairIndex);
        if (isStationColumn(data, cell[0], cell[1]) || isBorderColumn(data, cell[0], cell[1], radius)) {
            return 0;
        }
        Block block = world.getBlockAt(cell[0], y, cell[1]);
        if (!canPlaceStairStructure(block) && !Tag.STAIRS.isTagged(block.getType())) {
            return 0;
        }

        if (isCornerStairIndex(data, radius, stairIndex)) {
            placeSupportBlock(world, cell[0], y - 1, cell[1], support);
            placeLandingBlock(world, cell[0], y, cell[1], support);
            int[] next = stairCell(data, radius, stairIndex + 1);
            if (next[0] == cell[0] && next[1] == cell[1]) {
                return world.getBlockAt(cell[0], y, cell[1]).getType() == support ? 1 : 0;
            }
            Block nextBlock = world.getBlockAt(next[0], y, next[1]);
            if (!canPlaceStairStructure(nextBlock) && !Tag.STAIRS.isTagged(nextBlock.getType())) {
                return world.getBlockAt(cell[0], y, cell[1]).getType() == support ? 1 : 0;
            }
            placeSupportBlock(world, next[0], y - 1, next[1], support);
            nextBlock.setType(stairMat, false);
            applyStairData(nextBlock, data, radius, stairIndex + 1);
            boolean landingOk = world.getBlockAt(cell[0], y, cell[1]).getType() == support;
            boolean stairOk = Tag.STAIRS.isTagged(nextBlock.getType());
            if (landingOk && stairOk) {
                return 2;
            }
            return landingOk || stairOk ? 1 : 0;
        }

        placeSupportBlock(world, cell[0], y - 1, cell[1], support);
        block.setType(stairMat, false);
        applyStairData(block, data, radius, stairIndex);
        return Tag.STAIRS.isTagged(block.getType()) ? 1 : 0;
    }

    public static boolean isStairStructureBlock(Block block, SoulGolemData data, GolemSettings.Digger digger) {
        if (block == null || data == null || digger == null || !data.hasDigProgress()) {
            return false;
        }
        int radius = radius(digger);
        int x = block.getX();
        int z = block.getZ();
        int y = block.getY();
        Material support = stairSupportMaterial(digger);
        int homeFloor = (int) Math.floor(data.homeY());
        int top = Math.min(data.digStartY(), homeFloor);
        int bottom = Math.min(data.digLayerY(), top) - 1;
        int index = 0;
        for (int sy = top; sy >= bottom; sy--) {
            boolean corner = isCornerStairIndex(data, radius, index);
            int[] cell = stairCell(data, radius, index);
            if (x == cell[0] && z == cell[1]) {
                if (y == sy - 1) {
                    return true;
                }
                if (corner && y == sy && block.getType() == support) {
                    return true;
                }
                if (!corner && y == sy && Tag.STAIRS.isTagged(block.getType())) {
                    return true;
                }
            }
            if (corner) {
                int[] next = stairCell(data, radius, index + 1);
                if (x == next[0] && z == next[1]) {
                    if (y == sy - 1) {
                        return true;
                    }
                    if (y == sy && (Tag.STAIRS.isTagged(block.getType()) || block.getType() == support)) {
                        return true;
                    }
                }
                index += 2;
            } else {
                index += 1;
            }
        }
        return false;
    }

    private static void applyStairData(Block block, SoulGolemData data, int radius, int stairIndex) {
        if (!(block.getBlockData() instanceof Stairs stairs)) {
            return;
        }
        stairs.setFacing(stairFacing(data, radius, stairIndex));
        stairs.setHalf(Bisected.Half.BOTTOM);
        stairs.setShape(stairShape(data, radius, stairIndex));
        block.setBlockData(stairs, false);
    }

    public static Stairs.Shape stairShape(SoulGolemData data, int radius, int stairIndex) {
        int[] cell = stairCell(data, radius, stairIndex);
        List<int[]> ring = stairRingClockwise(data, radius);
        int at = ringIndex(ring, cell);
        if (at < 0 || ring.size() < 3) {
            return Stairs.Shape.STRAIGHT;
        }
        int[] prev = ring.get(Math.floorMod(at - 1, ring.size()));
        int[] next = ring.get(Math.floorMod(at + 1, ring.size()));
        int inDx = cell[0] - prev[0];
        int inDz = cell[1] - prev[1];
        int outDx = next[0] - cell[0];
        int outDz = next[1] - cell[1];
        if (inDx == outDx && inDz == outDz) {
            return Stairs.Shape.STRAIGHT;
        }
        int turn = inDx * outDz - inDz * outDx;
        if (turn == 0) {
            return Stairs.Shape.STRAIGHT;
        }
        BlockFace facing = stairFacing(data, radius, stairIndex);
        int fx = facing.getModX();
        int fz = facing.getModZ();
        int leftOfFacingDx = -fz;
        int leftOfFacingDz = fx;
        boolean left = outDx * leftOfFacingDx + outDz * leftOfFacingDz > 0
                || (outDx * leftOfFacingDx + outDz * leftOfFacingDz == 0 && turn > 0);
        double toHomeX = data.homeX() - (cell[0] + 0.5D);
        double toHomeZ = data.homeZ() - (cell[1] + 0.5D);
        double bisectX = inDx + outDx;
        double bisectZ = inDz + outDz;
        boolean towardPit = bisectX * toHomeX + bisectZ * toHomeZ > 0;
        if (towardPit) {
            return left ? Stairs.Shape.OUTER_LEFT : Stairs.Shape.OUTER_RIGHT;
        }
        return left ? Stairs.Shape.INNER_LEFT : Stairs.Shape.INNER_RIGHT;
    }

    private static int ringIndex(List<int[]> ring, int[] cell) {
        for (int i = 0; i < ring.size(); i++) {
            int[] p = ring.get(i);
            if (p[0] == cell[0] && p[1] == cell[1]) {
                return i;
            }
        }
        return -1;
    }

    public static Location stairStandInward(World world, SoulGolemData data, int[] cell, int stairY) {
        double cx = cell[0] + 0.5D;
        double cz = cell[1] + 0.5D;
        double dx = data.homeX() - cx;
        double dz = data.homeZ() - cz;
        double len = Math.hypot(dx, dz);
        if (len < 0.05D) {
            return new Location(world, cx, stairY + 1.0D, cz);
        }
        dx /= len;
        dz /= len;
        return new Location(world, cx + dx * 0.35D, stairY + 1.0D, cz + dz * 0.35D);
    }

    public static void ensureSpiralStairs(
            World world,
            SoulGolemData data,
            GolemSettings.Digger digger,
            int bottomY
    ) {
        if (!data.hasDigProgress()) {
            return;
        }
        int radius = radius(digger);
        int startY = Math.min(data.digStartY(), (int) Math.floor(data.homeY()));
        int lowest = Math.min(bottomY, data.digLayerY());
        for (int y = startY; y >= lowest; y--) {
            placeStair(world, data, digger, y, stairIndexForY(data, radius, y));
        }
        data.digStairIndex(stairIndexForY(data, radius, data.digLayerY()));
    }

    public static boolean isStairBlock(Block block) {
        return block != null && Tag.STAIRS.isTagged(block.getType());
    }

    public static boolean isOnStairRing(SoulGolemData data, int radius, int x, int z) {
        if (!isInsidePit(data, x, z, radius)) {
            return false;
        }
        return x == digMinX(data, radius) || x == digMaxX(data, radius)
                || z == digMinZ(data, radius) || z == digMaxZ(data, radius);
    }

    public static boolean isBedrockFloorLayer(SoulGolemData data, World world, int radius) {
        if (data == null || world == null || !data.hasDigProgress()) {
            return false;
        }
        int y = data.digLayerY();
        if (y <= world.getMinHeight()) {
            return true;
        }
        int r = Math.max(2, radius);
        int bedrock = 0;
        int otherSolid = 0;
        for (int z = digMinZ(data, r); z <= digMaxZ(data, r); z++) {
            for (int x = digMinX(data, r); x <= digMaxX(data, r); x++) {
                if (isStationColumn(data, x, z)) {
                    continue;
                }
                Material type = world.getBlockAt(x, y, z).getType();
                if (type.isAir() || Tag.STAIRS.isTagged(type) || Tag.CLIMBABLE.isTagged(type)) {
                    continue;
                }
                if (type == Material.BEDROCK || type == Material.BARRIER) {
                    bedrock++;
                } else if (type.isSolid()) {
                    otherSolid++;
                }
            }
        }
        return bedrock > 0 && otherSolid == 0;
    }

    public static boolean isCurrentStairSlot(SoulGolemData data, GolemSettings.Digger digger, int x, int y, int z) {
        if (!data.hasDigProgress() || y != data.digLayerY()) {
            return false;
        }
        int radius = radius(digger);
        int index = stairIndexForY(data, radius, y);
        int[] cell = stairCell(data, radius, index);
        if (cell[0] == x && cell[1] == z) {
            return true;
        }
        if (isCornerStairIndex(data, radius, index)) {
            int[] next = stairCell(data, radius, index + 1);
            return next[0] == x && next[1] == z;
        }
        return false;
    }

    public static boolean isProtected(Block block, SoulGolemData data, SoulChestService chestService) {
        Material type = block.getType();
        if (type.isAir() || type == Material.BEDROCK || type == Material.BARRIER
                || type == Material.COMMAND_BLOCK || type == Material.CHAIN_COMMAND_BLOCK
                || type == Material.REPEATING_COMMAND_BLOCK) {
            return true;
        }
        if (SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE || type == Material.COMPOSTER) {
            return true;
        }
        if (Tag.CLIMBABLE.isTagged(type) || type == Material.LADDER) {
            return true;
        }
        if (chestService != null && chestService.isChestColumn(data, block)) {
            return true;
        }
        return false;
    }

    public static boolean isDiggable(
            Block block,
            SoulGolemData data,
            SoulChestService chestService,
            FarmAreaService farmArea,
            GolemSettings.Digger digger
    ) {
        if (isProtected(block, data, chestService)) {
            return false;
        }
        if (isStairStructureBlock(block, data, digger)) {
            return false;
        }
        int radius = radius(digger);
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        if (!isInsidePit(data, x, z, radius)) {
            return false;
        }
        if (data.hasDigProgress()) {
            if (y != data.digLayerY()) {
                return false;
            }
            if (y > data.digStartY()) {
                return false;
            }
        }
        if (isBorderColumn(data, x, z, radius) || isStationColumn(data, x, z)) {
            return false;
        }
        Material type = block.getType();
        if (farmArea != null && farmArea.isBorderMaterial(type)) {
            return false;
        }
        if (FarmAreaService.isFoliage(type) || FarmAreaService.isVegetation(type) || type == Material.SNOW) {
            return true;
        }
        if (!type.isSolid()) {
            return false;
        }
        return true;
    }

    public static boolean isDiggable(Block block, SoulGolemData data, SoulChestService chestService, FarmAreaService farmArea) {
        GolemSettings.Digger digger = new GolemSettings.Digger();
        return isDiggable(block, data, chestService, farmArea, digger);
    }

    public static int findHighestPitFillY(SoulGolemData data, GolemSettings.Digger digger, FarmAreaService farmArea) {
        World world = org.bukkit.Bukkit.getWorld(data.worldName());
        int homeY = (int) Math.floor(data.homeY());
        if (world == null) {
            return homeY;
        }
        int radius = radius(digger);
        int scanTop = Math.min(world.getMaxHeight() - 1, homeY + 16);
        int scanBottom = Math.max(world.getMinHeight(), homeY - 2);
        int highest = Integer.MIN_VALUE;
        for (int z = digMinZ(data, radius); z <= digMaxZ(data, radius); z++) {
            for (int x = digMinX(data, radius); x <= digMaxX(data, radius); x++) {
                if (isStationColumn(data, x, z)) {
                    continue;
                }
                for (int y = scanTop; y >= scanBottom; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isPitFillBlock(block, farmArea)) {
                        continue;
                    }
                    if (isOnStairRing(data, radius, x, z)
                            && (block.getType() == stairSupportMaterial(digger) || Tag.STAIRS.isTagged(block.getType()))) {
                        continue;
                    }
                    if (isStairStructureBlock(block, data, digger)) {
                        continue;
                    }
                    if (highest < y) {
                        highest = y;
                    }
                    break;
                }
            }
        }
        return highest == Integer.MIN_VALUE ? homeY : highest;
    }

    public static int findHighestPitFillAbove(
            SoulGolemData data,
            GolemSettings.Digger digger,
            FarmAreaService farmArea,
            int aboveY
    ) {
        World world = org.bukkit.Bukkit.getWorld(data.worldName());
        if (world == null) {
            return Integer.MIN_VALUE;
        }
        int homeY = (int) Math.floor(data.homeY());
        int radius = radius(digger);
        int scanTop = Math.min(world.getMaxHeight() - 1, Math.max(homeY + 16, aboveY + 16));
        int highest = Integer.MIN_VALUE;
        for (int z = digMinZ(data, radius); z <= digMaxZ(data, radius); z++) {
            for (int x = digMinX(data, radius); x <= digMaxX(data, radius); x++) {
                if (isStationColumn(data, x, z)) {
                    continue;
                }
                for (int y = scanTop; y > aboveY; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isPitFillBlock(block, farmArea)) {
                        continue;
                    }
                    if (isOnStairRing(data, radius, x, z)
                            && (block.getType() == stairSupportMaterial(digger) || Tag.STAIRS.isTagged(block.getType()))) {
                        continue;
                    }
                    if (isStairStructureBlock(block, data, digger)) {
                        continue;
                    }
                    if (highest < y) {
                        highest = y;
                    }
                    break;
                }
            }
        }
        return highest;
    }

    private static boolean isPitFillBlock(Block block, FarmAreaService farmArea) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (type.isAir() || type == Material.BEDROCK || type == Material.BARRIER
                || type == Material.COMMAND_BLOCK || type == Material.CHAIN_COMMAND_BLOCK
                || type == Material.REPEATING_COMMAND_BLOCK) {
            return false;
        }
        if (SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE || type == Material.COMPOSTER) {
            return false;
        }
        if (Tag.STAIRS.isTagged(type) || Tag.CLIMBABLE.isTagged(type) || type == Material.LADDER) {
            return false;
        }
        if (farmArea != null && farmArea.isBorderMaterial(type)) {
            return false;
        }
        if (FarmAreaService.isFoliage(type) || FarmAreaService.isVegetation(type) || type == Material.SNOW) {
            return false;
        }
        if (Tag.LOGS.isTagged(type)) {
            return false;
        }
        return type.isSolid();
    }

    public static int clearStairStructureAboveGround(SoulGolemData data, GolemSettings.Digger digger) {
        World world = org.bukkit.Bukkit.getWorld(data.worldName());
        if (world == null) {
            return 0;
        }
        Material stairMat = Material.matchMaterial(digger.stairMaterial);
        if (stairMat == null || !Tag.STAIRS.isTagged(stairMat)) {
            stairMat = Material.STONE_STAIRS;
        }
        Material support = stairSupportMaterial(digger);
        int radius = radius(digger);
        int homeFloor = (int) Math.floor(data.homeY());
        int top = Math.min(world.getMaxHeight() - 1, homeFloor + 20);
        int cleared = 0;
        for (int[] cell : stairRingClockwise(data, radius)) {
            if (isStationColumn(data, cell[0], cell[1])) {
                continue;
            }
            for (int y = homeFloor + 1; y <= top; y++) {
                Block at = world.getBlockAt(cell[0], y, cell[1]);
                Material type = at.getType();
                if (type == stairMat || type == support) {
                    at.setType(Material.AIR, false);
                    cleared++;
                }
            }
        }
        return cleared;
    }

    public static boolean isSoft(Material type) {
        return FarmAreaService.isFoliage(type)
                || FarmAreaService.isVegetation(type)
                || type == Material.DIRT || type == Material.GRASS_BLOCK || type == Material.DIRT_PATH
                || type == Material.COARSE_DIRT || type == Material.ROOTED_DIRT || type == Material.PODZOL
                || type == Material.MYCELIUM || type == Material.MUD || type == Material.CLAY
                || type == Material.SAND || type == Material.RED_SAND || type == Material.GRAVEL
                || type == Material.SOUL_SAND || type == Material.SOUL_SOIL
                || type == Material.SNOW_BLOCK || type == Material.POWDER_SNOW || type == Material.SNOW
                || Tag.SAND.isTagged(type);
    }

    public static void clearFoliageAround(Block origin, int range) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        int r = Math.max(0, range);
        World world = origin.getWorld();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block at = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    if (FarmAreaService.isFoliage(at.getType()) || FarmAreaService.isVegetation(at.getType())) {
                        at.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    public static boolean isFluidHazard(Block block) {
        if (block == null) {
            return false;
        }
        if (block.isLiquid()) {
            return true;
        }
        Material type = block.getType();
        return type == Material.LAVA
                || type == Material.WATER
                || type == Material.BUBBLE_COLUMN
                || type == Material.FIRE
                || type == Material.SOUL_FIRE
                || type == Material.MAGMA_BLOCK;
    }

    public static Material hazardSealMaterial(GolemSettings.Digger digger) {
        Material seal = Material.matchMaterial(digger.hazardSealMaterial);
        if (seal == null || !seal.isBlock() || !seal.isSolid()) {
            return Material.COBBLESTONE;
        }
        if (seal == Material.LAVA
                || seal == Material.WATER
                || seal == Material.MAGMA_BLOCK
                || seal == Material.FIRE
                || seal == Material.SOUL_FIRE) {
            return Material.COBBLESTONE;
        }
        return seal;
    }

    public static void sealHazardsNear(Block origin, int caveSafeDepth, Material seal) {
        if (origin == null) {
            return;
        }
        sealAround(origin.getLocation().add(0.5D, 0.0D, 0.5D), 1, 0, Math.max(1, caveSafeDepth), seal);
    }

    public static int sealFluidsBelowFeet(
            Location feet,
            SoulGolemData data,
            GolemSettings.Digger digger,
            int range
    ) {
        if (feet == null || feet.getWorld() == null || data == null || digger == null) {
            return 0;
        }
        Material seal = hazardSealMaterial(digger);
        int maxY = data.hasDigProgress()
                ? Math.min(data.digLayerY(), data.digStartY())
                : (int) Math.floor(data.homeY());
        maxY = Math.min(maxY, feet.getBlockY() - 1);
        int r = Math.max(0, range);
        int down = Math.max(2, digger.caveSafeDepth);
        World world = feet.getWorld();
        int ox = feet.getBlockX();
        int oy = feet.getBlockY();
        int oz = feet.getBlockZ();
        int sealed = 0;
        for (int dy = -down; dy <= 0; dy++) {
            int y = oy + dy;
            if (y > maxY) {
                continue;
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block at = world.getBlockAt(ox + dx, y, oz + dz);
                    if (isFluidHazard(at)) {
                        at.setType(seal, false);
                        sealed++;
                    }
                }
            }
        }
        clearRaisedSealPad(data, digger);
        return sealed;
    }

    public static int clearRaisedSealPad(SoulGolemData data, GolemSettings.Digger digger) {
        if (data == null || digger == null || !data.hasDigProgress()) {
            return 0;
        }
        World world = org.bukkit.Bukkit.getWorld(data.worldName());
        if (world == null) {
            return 0;
        }
        Material fluidSeal = hazardSealMaterial(digger);
        Material borderSeal = borderSealMaterial(digger);
        int radius = radius(digger);
        int top = data.digStartY();
        int cleared = 0;
        for (int z = digMinZ(data, radius); z <= digMaxZ(data, radius); z++) {
            for (int x = digMinX(data, radius); x <= digMaxX(data, radius); x++) {
                if (isBorderColumn(data, x, z, radius) || isStationColumn(data, x, z)) {
                    continue;
                }
                for (int y = top + 1; y <= top + 3; y++) {
                    Block at = world.getBlockAt(x, y, z);
                    Material type = at.getType();
                    if (type == fluidSeal || type == borderSeal) {
                        at.setType(Material.AIR, false);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }

    public static boolean hasFluidNear(Location location, int range) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        int r = Math.max(1, range);
        World world = location.getWorld();
        int ox = location.getBlockX();
        int oy = location.getBlockY();
        int oz = location.getBlockZ();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (isFluidHazard(world.getBlockAt(ox + dx, oy + dy, oz + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int sealAround(Location location, int range, int up, int down, Material seal) {
        if (location == null || location.getWorld() == null || seal == null) {
            return 0;
        }
        int r = Math.max(0, range);
        int upY = Math.max(0, up);
        int downY = Math.max(0, down);
        World world = location.getWorld();
        int ox = location.getBlockX();
        int oy = location.getBlockY();
        int oz = location.getBlockZ();
        int sealed = 0;
        for (int dy = -downY; dy <= upY; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block at = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    if (isFluidHazard(at)) {
                        at.setType(seal, false);
                        sealed++;
                    }
                }
            }
        }
        return sealed;
    }

    public static Material borderSealMaterial(GolemSettings.Digger digger) {
        Material seal = Material.matchMaterial(digger.borderSealMaterial);
        if (seal == null || !seal.isBlock() || !seal.isSolid()) {
            return Material.STONE;
        }
        if (seal == Material.LAVA
                || seal == Material.WATER
                || seal == Material.MAGMA_BLOCK
                || seal == Material.FIRE
                || seal == Material.SOUL_FIRE) {
            return Material.STONE;
        }
        return seal;
    }

    public static boolean isBorderSealColumn(SoulGolemData data, int x, int z, int radius) {
        int minX = digMinX(data, radius);
        int maxX = digMaxX(data, radius);
        int minZ = digMinZ(data, radius);
        int maxZ = digMaxZ(data, radius);
        int outerMinX = minX - 1;
        int outerMaxX = maxX + 1;
        int outerMinZ = minZ - 1;
        int outerMaxZ = maxZ + 1;
        if (x < outerMinX || x > outerMaxX || z < outerMinZ || z > outerMaxZ) {
            return false;
        }
        return x == outerMinX || x == outerMaxX || z == outerMinZ || z == outerMaxZ;
    }

    private static boolean isSealableGap(Block block) {
        if (block == null) {
            return false;
        }
        if (isFluidHazard(block)) {
            return true;
        }
        Material type = block.getType();
        return type.isAir() || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    private static boolean canOverwriteForBorderSeal(Block block) {
        if (block == null || !isSealableGap(block)) {
            return false;
        }
        Material type = block.getType();
        return type != Material.BEDROCK
                && type != Material.BARRIER
                && type != Material.COMMAND_BLOCK
                && !SoulChestService.isChestLike(type)
                && type != Material.CRAFTING_TABLE
                && type != Material.COMPOSTER
                && !Tag.STAIRS.isTagged(type);
    }

    public static int sealPitFluids(SoulGolemData data, GolemSettings.Digger digger, FarmAreaService farmArea) {
        if (data == null || digger == null) {
            return 0;
        }
        World world = org.bukkit.Bukkit.getWorld(data.worldName());
        if (world == null) {
            return 0;
        }
        Material fluidSeal = hazardSealMaterial(digger);
        Material borderSeal = borderSealMaterial(digger);
        int radius = radius(digger);
        int minX = digMinX(data, radius) - 1;
        int maxX = digMaxX(data, radius) + 1;
        int minZ = digMinZ(data, radius) - 1;
        int maxZ = digMaxZ(data, radius) + 1;
        int surfaceY = data.hasDigProgress()
                ? Math.max(data.digStartY(), (int) Math.floor(data.homeY()))
                : (int) Math.floor(data.homeY());
        int floorY = data.hasDigProgress() ? data.digLayerY() : surfaceY;
        int plannedBottom;
        if (digger.maxDepth > 0) {
            plannedBottom = data.hasDigProgress()
                    ? data.digStartY() - digger.maxDepth
                    : floorY - digger.maxDepth;
        } else {
            plannedBottom = world.getMinHeight();
        }
        int bottomY = Math.max(world.getMinHeight(), Math.min(floorY - Math.max(1, digger.caveSafeDepth), plannedBottom));
        int topY = Math.min(world.getMaxHeight() - 1, surfaceY);
        int probe = Math.max(1, digger.borderGapProbe);
        int sealed = 0;

        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (farmArea != null && farmArea.isWaterColumn(data, x, z)) {
                    continue;
                }
                boolean border = isBorderSealColumn(data, x, z, radius);
                boolean gapBelow = false;
                if (border) {
                    for (int i = 1; i <= probe; i++) {
                        Block below = world.getBlockAt(x, floorY - i, z);
                        if (isSealableGap(below) || isFluidHazard(below)) {
                            gapBelow = true;
                            break;
                        }
                    }
                }
                boolean fillColumn = border && gapBelow && !isInsidePit(data, x, z, radius);
                int columnBottom = fillColumn ? Math.max(world.getMinHeight(), plannedBottom) : bottomY;
                for (int y = columnBottom; y <= topY; y++) {
                    Block at = world.getBlockAt(x, y, z);
                    if (isFluidHazard(at) && (border || y <= floorY)) {
                        at.setType(border ? borderSeal : fluidSeal, false);
                        sealed++;
                        continue;
                    }
                    if (fillColumn && y <= floorY && canOverwriteForBorderSeal(at)) {
                        at.setType(borderSeal, false);
                        sealed++;
                    }
                }
            }
        }
        sealed += clearRaisedSealPad(data, digger);
        return sealed;
    }

    public static boolean hasCaveBelow(Block block, int caveSafeDepth) {
        World world = block.getWorld();
        int depth = Math.max(1, caveSafeDepth);
        for (int i = 1; i <= depth; i++) {
            Block below = world.getBlockAt(block.getX(), block.getY() - i, block.getZ());
            if (isFluidHazard(below)) {
                return true;
            }
            Material type = below.getType();
            if (FarmAreaService.isFoliage(type) || FarmAreaService.isVegetation(type) || type == Material.SNOW) {
                continue;
            }
            if (!type.isAir() && type.isSolid()) {
                return false;
            }
        }
        return true;
    }

    public static boolean prepareDigFloor(Block block, GolemSettings.Digger digger) {
        Material seal = hazardSealMaterial(digger);
        int depth = Math.max(1, digger.caveSafeDepth);
        World world = block.getWorld();
        for (int i = 1; i <= depth; i++) {
            Block below = world.getBlockAt(block.getX(), block.getY() - i, block.getZ());
            if (FarmAreaService.isFoliage(below.getType()) || FarmAreaService.isVegetation(below.getType())) {
                below.setType(Material.AIR, false);
            }
        }
        sealHazardsNear(block, depth, seal);
        if (!hasCaveBelow(block, depth)) {
            return true;
        }
        for (int i = 1; i <= depth; i++) {
            Block below = world.getBlockAt(block.getX(), block.getY() - i, block.getZ());
            if (isSealableGap(below) || FarmAreaService.isFoliage(below.getType())) {
                below.setType(seal, false);
            } else if (below.getType().isSolid()) {
                break;
            }
        }
        return !hasCaveBelow(block, depth);
    }
}
