package bm.b0b0b0.SoulGolem.service.setup;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.model.SetupPhase;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.OreTableService;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class GolemSetupWork {

    private final SoulChestService chestService;
    private final WorkAreaService workAreaService;
    private final FarmAreaService farmAreaService;
    private final OreTableService oreTable;
    private final GolemMovement movement;

    public GolemSetupWork(
            SoulChestService chestService,
            WorkAreaService workAreaService,
            FarmAreaService farmAreaService,
            OreTableService oreTable,
            GolemMovement movement
    ) {
        this.chestService = chestService;
        this.workAreaService = workAreaService;
        this.farmAreaService = farmAreaService;
        this.oreTable = oreTable;
        this.movement = movement;
    }

    public static boolean isSetupState(ActiveGolem golem) {
        return golem != null && !golem.setupComplete();
    }

    public static boolean isClearingSetup(ActiveGolem golem) {
        if (golem.data().type() == GolemType.FARMER) {
            FarmerState state = golem.farmerState();
            return state == FarmerState.MOVING_TO_SETUP_CLEAR || state == FarmerState.SETUP_CLEAR;
        }
        MinerState state = golem.state();
        return state == MinerState.MOVING_TO_SETUP_CLEAR || state == MinerState.SETUP_CLEAR;
    }

    public void startSetup(ActiveGolem golem) {
        golem.setupComplete(false);
        golem.setupPhase(SetupPhase.CLEAR);
        golem.clearSetupQueue();
        enterPhase(golem, SetupPhase.CLEAR);
    }

    public void tick(ActiveGolem golem, CopperGolem copper) {
        if (golem.setupComplete()) {
            finishSetup(golem);
            return;
        }
        SetupPhase phase = golem.setupPhase();
        if (phase == null || phase == SetupPhase.DONE) {
            golem.setupPhase(SetupPhase.CLEAR);
            phase = SetupPhase.CLEAR;
        }
        if (!matchesSetupPhaseState(golem, phase)) {
            setMovingState(golem, phase);
        }
        if (phase == SetupPhase.CLEAR) {
            tickClear(golem, copper);
            return;
        }
        if (golem.setupQueue().isEmpty()) {
            fillQueueForPhase(golem, phase);
        }
        if (golem.setupQueue().isEmpty() || golem.setupQueueIndex() >= golem.setupQueue().size()) {
            if (phase == SetupPhase.BORDER && refillBorderQueue(golem)) {
                setMovingState(golem, SetupPhase.BORDER);
                return;
            }
            advancePhase(golem);
            return;
        }
        Location target = golem.currentSetupTarget();
        if (target == null || target.getWorld() == null) {
            golem.advanceSetupTarget();
            golem.clearPathWaypoint();
            return;
        }
        if (phase == SetupPhase.BORDER && isBorderAlreadyPlaced(target)) {
            golem.advanceSetupTarget();
            golem.clearPathWaypoint();
            return;
        }
        Location stand = standFor(golem, phase, target);
        if (stand == null) {
            golem.advanceSetupTarget();
            golem.clearPathWaypoint();
            return;
        }
        setMovingState(golem, phase);
        double reach = phase == SetupPhase.BORDER ? 1.2D : 1.69D;
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > reach) {
            this.movement.walkTowards(copper, stand, golem);
            return;
        }
        this.movement.stop(copper);
        copper.setVelocity(new Vector(0, 0, 0));
        golem.clearPathWaypoint();
        setActingState(golem, phase);
        performAction(golem, copper, phase, target);
        golem.advanceSetupTarget();
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();
        if (golem.setupQueueIndex() >= golem.setupQueue().size()) {
            if (phase == SetupPhase.BORDER && refillBorderQueue(golem)) {
                setMovingState(golem, SetupPhase.BORDER);
                return;
            }
            advancePhase(golem);
        }
    }

    private void tickClear(ActiveGolem golem, CopperGolem copper) {
        SoulGolemData data = golem.data();
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            advancePhase(golem);
            return;
        }
        int homeY = (int) Math.floor(data.homeY());
        int radius = this.chestService.effectiveRadius(data);

        Location column = stickyClearColumn(golem, copper, world, homeY, radius);
        if (column == null) {
            advancePhase(golem);
            return;
        }

        int x = column.getBlockX();
        int z = column.getBlockZ();
        Location approach = approachForClearColumn(data, world, x, homeY, z);
        Block feet = approach.getBlock();
        if (isSetupJunkBlock(feet, data)) {
            x = feet.getX();
            z = feet.getZ();
            column = new Location(world, x, homeY, z);
            golem.setSetupQueue(List.of(column));
            approach = approachForClearColumn(data, world, x, homeY, z);
        }

        Location reachPoint = new Location(world, x + 0.5D, homeY + 1.0D, z + 0.5D);
        setMovingState(golem, SetupPhase.CLEAR);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), reachPoint) > 2.25D) {
            this.movement.walkTowards(copper, approach, golem);
            return;
        }

        this.movement.stop(copper);
        copper.setVelocity(new Vector(0, 0, 0));
        golem.clearPathWaypoint();
        setActingState(golem, SetupPhase.CLEAR);
        clearInFront(data, world, copper.getLocation(), x, homeY, z, radius);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();

        if (columnHasSetupJunk(data, world, x, homeY, z, radius)) {
            golem.setSetupQueue(List.of(new Location(world, x, homeY, z)));
            return;
        }

        Location next = nearestClearColumn(copper.getLocation(), data, world, homeY, radius);
        if (next == null) {
            golem.clearSetupQueue();
            advancePhase(golem);
            return;
        }
        golem.setSetupQueue(List.of(next));
    }

    private Location stickyClearColumn(
            ActiveGolem golem,
            CopperGolem copper,
            World world,
            int homeY,
            int radius
    ) {
        SoulGolemData data = golem.data();
        Location sticky = golem.currentSetupTarget();
        if (sticky != null
                && sticky.getWorld() != null
                && sticky.getWorld().equals(world)
                && columnHasSetupJunk(data, world, sticky.getBlockX(), homeY, sticky.getBlockZ(), radius)) {
            return new Location(world, sticky.getBlockX(), homeY, sticky.getBlockZ());
        }
        Location next = nearestClearColumn(copper.getLocation(), data, world, homeY, radius);
        if (next == null) {
            golem.clearSetupQueue();
            return null;
        }
        golem.setSetupQueue(List.of(next));
        return next;
    }

    private Location nearestClearColumn(
            Location from,
            SoulGolemData data,
            World world,
            int homeY,
            int radius
    ) {
        List<Block> junk = this.farmAreaService.areaObstructionsForSetup(data, radius);
        if (junk.isEmpty()) {
            return null;
        }
        Block best = null;
        double bestDist = Double.MAX_VALUE;
        for (Block block : junk) {
            double dx = block.getX() + 0.5D - from.getX();
            double dz = block.getZ() + 0.5D - from.getZ();
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = block;
            }
        }
        if (best == null) {
            return null;
        }
        return new Location(world, best.getX(), homeY, best.getZ());
    }

    private Location approachForClearColumn(SoulGolemData data, World world, int x, int homeY, int z) {
        int homeX = (int) Math.floor(data.homeX());
        int homeZ = (int) Math.floor(data.homeZ());
        int[][] offsets = {
                {0, 0},
                {Integer.compare(homeX, x), 0},
                {0, Integer.compare(homeZ, z)},
                {Integer.compare(homeX, x), Integer.compare(homeZ, z)},
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        for (int[] o : offsets) {
            int ax = x + o[0];
            int az = z + o[1];
            if (this.farmAreaService.isWaterColumn(data, ax, az)) {
                continue;
            }
            Block ground = world.getBlockAt(ax, homeY, az);
            Block feet = world.getBlockAt(ax, homeY + 1, az);
            if (!ground.getType().isSolid() && ground.getType() != Material.FARMLAND) {
                continue;
            }
            if (SoulChestService.isChestLike(feet.getType()) || feet.getType() == Material.CRAFTING_TABLE) {
                continue;
            }
            if (feet.getType().isSolid()
                    && !FarmAreaService.isVegetation(feet.getType())
                    && feet.getType() != Material.SNOW
                    && !(ax == x && az == z)) {
                continue;
            }
            return feet.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        return new Location(world, x + 0.5D, homeY + 1.0D, z + 0.5D);
    }

    private void clearInFront(
            SoulGolemData data,
            World world,
            Location from,
            int targetX,
            int homeY,
            int targetZ,
            int radius
    ) {
        clearSetupColumn(data, world, targetX, homeY, targetZ, radius);
        int fx = from.getBlockX();
        int fz = from.getBlockZ();
        int stepX = Integer.compare(targetX, fx);
        int stepZ = Integer.compare(targetZ, fz);
        if (stepX != 0 || stepZ != 0) {
            clearSetupColumn(data, world, fx + stepX, homeY, fz + stepZ, radius);
            clearSetupColumn(data, world, fx + stepX, homeY, fz, radius);
            clearSetupColumn(data, world, fx, homeY, fz + stepZ, radius);
        }
        clearSetupColumn(data, world, fx, homeY, fz, radius);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = targetX + dx;
                int z = targetZ + dz;
                double dist = (x + 0.5D - from.getX()) * (x + 0.5D - from.getX())
                        + (z + 0.5D - from.getZ()) * (z + 0.5D - from.getZ());
                if (dist <= 2.25D) {
                    clearSetupColumn(data, world, x, homeY, z, radius);
                }
            }
        }
    }

    private void clearSetupColumn(
            SoulGolemData data,
            World world,
            int x,
            int homeY,
            int z,
            int radius
    ) {
        if (this.farmAreaService.isWaterColumn(data, x, z)) {
            return;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        if (x < homeX - r || x > homeX + r || z < homeZ - r || z > homeZ + r) {
            return;
        }
        boolean perimeter = x == homeX - r || x == homeX + r || z == homeZ - r || z == homeZ + r;
        int maxY = homeY + (perimeter ? 2 : 6);
        for (int y = maxY; y >= homeY + 1; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!isSetupJunkBlock(block, data)) {
                continue;
            }
            block.setType(Material.AIR, false);
        }
    }

    private boolean columnHasSetupJunk(
            SoulGolemData data,
            World world,
            int x,
            int homeY,
            int z,
            int radius
    ) {
        if (this.farmAreaService.isWaterColumn(data, x, z)) {
            return false;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = Math.max(1, radius);
        boolean perimeter = x == homeX - r || x == homeX + r || z == homeZ - r || z == homeZ + r;
        int maxY = homeY + (perimeter ? 2 : 6);
        for (int y = homeY + 1; y <= maxY; y++) {
            if (isSetupJunkBlock(world.getBlockAt(x, y, z), data)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSetupJunkBlock(Block block, SoulGolemData data) {
        return block != null && this.farmAreaService.isClearableObstruction(block, data);
    }

    private boolean refillBorderQueue(ActiveGolem golem) {
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Location> left = this.workAreaService.borderFloorSlots(golem.data(), radius);
        if (left.isEmpty()) {
            return false;
        }
        golem.setSetupQueue(left);
        golem.clearPathWaypoint();
        return true;
    }

    private boolean stillHasSetupJunk(ActiveGolem golem) {
        int radius = this.chestService.effectiveRadius(golem.data());
        return !this.farmAreaService.areaObstructionsForSetup(golem.data(), radius).isEmpty();
    }

    private void enterPhase(ActiveGolem golem, SetupPhase phase) {
        golem.setupPhase(phase);
        golem.clearSetupQueue();
        golem.clearPathWaypoint();
        if (phase == SetupPhase.CLEAR) {
            setMovingState(golem, phase);
            return;
        }
        fillQueueForPhase(golem, phase);
        if (golem.setupQueue().isEmpty()) {
            if (phase == SetupPhase.BORDER && refillBorderQueue(golem)) {
                setMovingState(golem, phase);
                return;
            }
            if (golem.setupQueue().isEmpty()) {
                advancePhase(golem);
                return;
            }
        }
        setMovingState(golem, phase);
    }

    private void advancePhase(ActiveGolem golem) {
        if (golem.setupPhase() == SetupPhase.CLEAR && stillHasSetupJunk(golem)) {
            golem.setupPhase(SetupPhase.CLEAR);
            setMovingState(golem, SetupPhase.CLEAR);
            return;
        }
        if (golem.setupPhase() == SetupPhase.BORDER && refillBorderQueue(golem)) {
            golem.setupPhase(SetupPhase.BORDER);
            setMovingState(golem, SetupPhase.BORDER);
            return;
        }
        SetupPhase next = switch (golem.setupPhase()) {
            case CLEAR -> SetupPhase.BORDER;
            case BORDER -> SetupPhase.CHEST;
            case CHEST -> golem.data().type() == GolemType.FARMER ? SetupPhase.CRAFT : SetupPhase.DONE;
            case CRAFT -> SetupPhase.DONE;
            case DONE -> SetupPhase.DONE;
        };
        if (next == SetupPhase.DONE) {
            finishSetup(golem);
            return;
        }
        enterPhase(golem, next);
    }

    private void finishSetup(ActiveGolem golem) {
        golem.setupComplete(true);
        golem.setupPhase(SetupPhase.DONE);
        golem.clearSetupQueue();
        SoulGolemData data = golem.data();
        int radius = this.chestService.effectiveRadius(data);
        this.workAreaService.reclaimTerritory(data, radius);
        this.farmAreaService.reprotectSeat(data);
        if (data.type() == GolemType.MINER && this.oreTable != null) {
            this.workAreaService.seedOres(data, radius, this.oreTable);
            golem.state(MinerState.IDLE);
        } else if (data.type() == GolemType.FARMER) {
            this.farmAreaService.ensureWater(data);
            golem.fieldReady(false);
            golem.farmerState(FarmerState.WAITING_SEEDS);
        }
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();
    }

    private void fillQueueForPhase(ActiveGolem golem, SetupPhase phase) {
        SoulGolemData data = golem.data();
        int radius = this.chestService.effectiveRadius(data);
        List<Location> queue = new ArrayList<>();
        switch (phase) {
            case CLEAR -> {
            }
            case BORDER -> queue.addAll(this.workAreaService.borderFloorSlots(data, radius));
            case CHEST -> {
                World world = Bukkit.getWorld(data.worldName());
                if (world != null) {
                    queue.add(new Location(world, data.chestX(), data.chestY(), data.chestZ()));
                }
            }
            case CRAFT -> {
                if (data.hasCraftStation()) {
                    World world = Bukkit.getWorld(data.worldName());
                    if (world != null) {
                        queue.add(new Location(world, data.craftX(), data.craftY(), data.craftZ()));
                    }
                }
            }
            case DONE -> {
            }
        }
        golem.setSetupQueue(queue);
    }

    private Location standFor(ActiveGolem golem, SetupPhase phase, Location target) {
        return switch (phase) {
            case CLEAR -> null;
            case BORDER -> standInsideForBorder(golem.data(), target);
            case CHEST -> {
                Location stand = this.chestService.chestStandLocation(golem.data());
                yield stand != null ? stand : target.clone().add(0.5D, 0.0D, 0.5D);
            }
            case CRAFT -> {
                Location stand = this.chestService.craftStandLocation(golem.data());
                yield stand != null ? stand : target.clone().add(0.5D, 0.0D, 0.5D);
            }
            case DONE -> null;
        };
    }

    private Location standInsideForBorder(SoulGolemData data, Location floor) {
        if (floor == null || floor.getWorld() == null) {
            return null;
        }
        World world = floor.getWorld();
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int r = this.chestService.effectiveRadius(data);
        int x = floor.getBlockX();
        int z = floor.getBlockZ();
        int inX = x;
        int inZ = z;
        if (x <= homeX - r) {
            inX = homeX - r + 1;
        } else if (x >= homeX + r) {
            inX = homeX + r - 1;
        }
        if (z <= homeZ - r) {
            inZ = homeZ - r + 1;
        } else if (z >= homeZ + r) {
            inZ = homeZ + r - 1;
        }
        if (inX == x && inZ == z) {
            inX = homeX;
            inZ = homeZ;
            if (isWaterOrStation(data, world, inX, homeY, inZ)) {
                return this.farmAreaService.standOn(floor.getBlock());
            }
        }
        if (isWaterOrStation(data, world, inX, homeY, inZ)) {
            int[][] fallbacks = {
                    {homeX - r + 1, z}, {homeX + r - 1, z},
                    {x, homeZ - r + 1}, {x, homeZ + r - 1},
                    {homeX - r + 1, homeZ - r + 1}, {homeX + r - 1, homeZ - r + 1},
                    {homeX - r + 1, homeZ + r - 1}, {homeX + r - 1, homeZ + r - 1}
            };
            for (int[] f : fallbacks) {
                if (!isWaterOrStation(data, world, f[0], homeY, f[1])) {
                    return this.farmAreaService.standOn(world.getBlockAt(f[0], homeY, f[1]));
                }
            }
            return this.farmAreaService.standOn(floor.getBlock());
        }
        return this.farmAreaService.standOn(world.getBlockAt(inX, homeY, inZ));
    }

    private boolean isWaterOrStation(SoulGolemData data, World world, int x, int homeY, int z) {
        if (this.farmAreaService.isWaterColumn(data, x, z)) {
            return true;
        }
        Location probe = new Location(world, x + 0.5D, homeY + 1.0D, z + 0.5D);
        return this.chestService.collidesWithStation(data, probe);
    }

    private void clearAboveBorderSlot(Location floor) {
        if (floor == null || floor.getWorld() == null) {
            return;
        }
        World world = floor.getWorld();
        int x = floor.getBlockX();
        int z = floor.getBlockZ();
        int baseY = floor.getBlockY();
        for (int y = baseY + 1; y <= baseY + 2; y++) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type.isAir()) {
                continue;
            }
            if (SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE) {
                continue;
            }
            if (type.isSolid() || type == Material.SNOW || FarmAreaService.isVegetation(type)) {
                block.setType(Material.AIR, false);
            }
        }
    }

    private boolean isBorderAlreadyPlaced(Location floor) {
        if (floor == null || floor.getWorld() == null) {
            return true;
        }
        Material type = floor.getBlock().getType();
        if (SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE) {
            return true;
        }
        return this.farmAreaService.isBorderMaterial(type);
    }

    private void performAction(ActiveGolem golem, CopperGolem copper, SetupPhase phase, Location target) {
        SoulGolemData data = golem.data();
        switch (phase) {
            case CLEAR -> {
            }
            case BORDER -> {
                clearAboveBorderSlot(target);
                this.workAreaService.placeBorderBlock(
                        data,
                        target.getBlockX(),
                        target.getBlockY(),
                        target.getBlockZ()
                );
            }
            case CHEST -> {
                this.chestService.clearStationColumn(target);
                Location home = this.workAreaService.homeLocation(data);
                this.chestService.placeChest(target, data.id(), data.ownerUuid(), home);
                this.workAreaService.protect(target.getBlock(), data.id());
                depositCarried(golem);
            }
            case CRAFT -> {
                this.chestService.clearStationColumn(target);
                this.chestService.placeCraftingTable(target, data.id(), data.ownerUuid());
                this.workAreaService.protect(target.getBlock(), data.id());
            }
            case DONE -> {
            }
        }
    }

    private void depositCarried(ActiveGolem golem) {
        if (golem.carried().isEmpty()) {
            return;
        }
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (!this.chestService.deposit(golem.data(), stack.clone())) {
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    private void setMovingState(ActiveGolem golem, SetupPhase phase) {
        if (golem.data().type() == GolemType.FARMER) {
            golem.farmerState(switch (phase) {
                case CLEAR -> FarmerState.MOVING_TO_SETUP_CLEAR;
                case BORDER -> FarmerState.MOVING_TO_SETUP_BORDER;
                case CHEST -> FarmerState.MOVING_TO_SETUP_CHEST;
                case CRAFT -> FarmerState.MOVING_TO_SETUP_CRAFT;
                case DONE -> FarmerState.WAITING_SEEDS;
            });
            return;
        }
        golem.state(switch (phase) {
            case CLEAR -> MinerState.MOVING_TO_SETUP_CLEAR;
            case BORDER -> MinerState.MOVING_TO_SETUP_BORDER;
            case CHEST -> MinerState.MOVING_TO_SETUP_CHEST;
            case CRAFT, DONE -> MinerState.IDLE;
        });
    }

    private static boolean matchesSetupPhaseState(ActiveGolem golem, SetupPhase phase) {
        if (golem.data().type() == GolemType.FARMER) {
            FarmerState state = golem.farmerState();
            return switch (phase) {
                case CLEAR -> state == FarmerState.MOVING_TO_SETUP_CLEAR || state == FarmerState.SETUP_CLEAR;
                case BORDER -> state == FarmerState.MOVING_TO_SETUP_BORDER || state == FarmerState.SETUP_BORDER;
                case CHEST -> state == FarmerState.MOVING_TO_SETUP_CHEST || state == FarmerState.SETUP_CHEST;
                case CRAFT -> state == FarmerState.MOVING_TO_SETUP_CRAFT || state == FarmerState.SETUP_CRAFT;
                case DONE -> true;
            };
        }
        MinerState state = golem.state();
        return switch (phase) {
            case CLEAR -> state == MinerState.MOVING_TO_SETUP_CLEAR || state == MinerState.SETUP_CLEAR;
            case BORDER -> state == MinerState.MOVING_TO_SETUP_BORDER || state == MinerState.SETUP_BORDER;
            case CHEST -> state == MinerState.MOVING_TO_SETUP_CHEST || state == MinerState.SETUP_CHEST;
            case CRAFT, DONE -> true;
        };
    }

    private void setActingState(ActiveGolem golem, SetupPhase phase) {
        if (golem.data().type() == GolemType.FARMER) {
            golem.farmerState(switch (phase) {
                case CLEAR -> FarmerState.SETUP_CLEAR;
                case BORDER -> FarmerState.SETUP_BORDER;
                case CHEST -> FarmerState.SETUP_CHEST;
                case CRAFT -> FarmerState.SETUP_CRAFT;
                case DONE -> FarmerState.WAITING_SEEDS;
            });
            return;
        }
        golem.state(switch (phase) {
            case CLEAR -> MinerState.SETUP_CLEAR;
            case BORDER -> MinerState.SETUP_BORDER;
            case CHEST -> MinerState.SETUP_CHEST;
            case CRAFT, DONE -> MinerState.IDLE;
        });
    }
}
