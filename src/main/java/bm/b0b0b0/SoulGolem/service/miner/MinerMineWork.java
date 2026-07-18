package bm.b0b0b0.SoulGolem.service.miner;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemGroundLootWork;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class MinerMineWork {

    private final MinerContext ctx;
    private MinerSupportWork support;

    public MinerMineWork(MinerContext ctx) {
        this.ctx = ctx;
    }

    public void wire(MinerSupportWork support) {
        this.support = support;
    }

    public void beginSeek(ActiveGolem golem, CopperGolem copper) {
        if (!golem.carried().isEmpty()) {
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        if (this.ctx.tryStartFeed(golem)) {
            return;
        }
        if (golem.data().energy() <= 0) {
            golem.state(MinerState.IDLE);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (!this.ctx.chestService().hasSpace(golem.data())) {
            golem.state(MinerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        golem.chestFullNotified(false);

        if (this.ctx.pickaxeWork().tryStartUpgrade(golem)) {
            return;
        }

        if (this.ctx.settings().yard.collectGroundLoot) {
            GolemGroundLootWork.Phase loot = this.ctx.groundLoot().tick(golem, copper, true);
            if (loot == GolemGroundLootWork.Phase.MOVING) {
                golem.state(MinerState.SEEKING);
                return;
            }
            if (loot == GolemGroundLootWork.Phase.PICKED) {
                if (this.ctx.groundLoot().hasLoot(golem.data())) {
                    golem.state(MinerState.SEEKING);
                    return;
                }
                if (!golem.carried().isEmpty()) {
                    golem.state(MinerState.MOVING_TO_CHEST);
                    return;
                }
            }
        }

        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        GolemSettings.Yard yard = this.ctx.settings().yard;
        if (yard.clearBorder) {
            List<Block> junk = this.ctx.farmAreaService().minerJunkToClear(golem.data(), radius, this.ctx.oreTable());
            if (!junk.isEmpty()) {
                golem.clearFetchFlags();
                golem.targetCrop(junk.get(0).getLocation());
                golem.state(MinerState.MOVING_TO_CLEAR);
                return;
            }
        }
        Material torch = this.ctx.resolveTorch();
        if (yard.placeTorches
                && this.ctx.chestService().countItem(golem.data(), torch) > 0
                && !this.ctx.farmAreaService().perimeterTorchSpots(golem.data(), radius, yard.maxTorches).isEmpty()) {
            golem.clearFetchFlags();
            golem.fetchingTorch(true);
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        if (this.support.tryStartFenceJob(golem, radius)) {
            return;
        }
        if (this.support.tryStartSeatJob(golem, radius)) {
            return;
        }

        Location home = this.ctx.workAreaService().homeLocation(golem.data());
        if (home == null) {
            return;
        }

        this.ctx.workAreaService().seedOres(golem.data(), radius, this.ctx.oreTable());

        if (golem.blocksLeftThisTrip() <= 0) {
            golem.blocksLeftThisTrip(this.ctx.pickaxeWork().blocksPerTrip(golem));
        }

        Location ore = findExistingOre(golem, home);
        if (ore == null) {
            int max = Math.max(1, this.ctx.settings().maxActiveOres);
            if (this.ctx.workAreaService().countOres(golem.data(), radius, this.ctx.oreTable()) < max) {
                ore = transformNearest(golem, home);
            }
        }
        if (ore == null) {
            golem.state(MinerState.IDLE);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        golem.targetOre(ore);
        golem.oreMaterial(ore.getBlock().getType());
        golem.state(MinerState.MOVING_TO_ORE);
        this.ctx.walkTowards(copper, ore.clone().add(0.5D, 1.0D, 0.5D), golem);
    }

    public void continueMoveToOre(ActiveGolem golem, CopperGolem copper) {
        Location target = golem.targetOre();
        if (target == null) {
            golem.state(MinerState.IDLE);
            return;
        }
        Block block = target.getBlock();
        if (!this.ctx.oreTable().isOre(block.getType())) {
            golem.targetOre(null);
            golem.state(MinerState.IDLE);
            return;
        }
        Location stand = target.clone().add(0.5D, 1.0D, 0.5D);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.0D) {
            this.ctx.walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.state(MinerState.MINING);
        GolemGaze.faceBlock(golem, block);
        golem.mineTicksLeft(Math.max(1L, (long) (this.ctx.settings().mineDurationTicks
                / this.ctx.stickBoostFactor(golem))));
        this.ctx.playMineFx(block);
    }

    public void continueMine(ActiveGolem golem, CopperGolem copper) {
        Location target = golem.targetOre();
        if (target == null) {
            golem.state(MinerState.IDLE);
            return;
        }
        Block block = target.getBlock();
        if (!this.ctx.oreTable().isOre(block.getType())) {
            golem.targetOre(null);
            golem.state(MinerState.IDLE);
            return;
        }
        long step = Math.max(1L, (long) (this.ctx.settings().coordinatorPeriodTicks * this.ctx.stickBoostFactor(golem)));
        long left = golem.mineTicksLeft() - step;
        golem.mineTicksLeft(left);
        if (left > 0L) {
            GolemGaze.faceBlock(golem, block);
            this.ctx.playMineFx(block);
            return;
        }

        Collection<ItemStack> drops = block.getDrops(this.ctx.pickaxeWork().miningTool(golem));
        this.ctx.playMineBurst(block);
        this.ctx.workAreaService().restoreBlock(block);

        for (ItemStack drop : drops) {
            golem.carry(drop);
        }

        golem.data().incrementBlocksMined();
        int drain = this.ctx.settings().energyPerMine;
        if (drain > 0) {
            golem.data().energy(Math.max(0, golem.data().energy() - drain));
        }
        golem.markDirty();
        golem.targetOre(null);
        golem.oreMaterial(null);

        int blocksLeft = golem.blocksLeftThisTrip() - 1;
        golem.blocksLeftThisTrip(blocksLeft);

        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        this.ctx.workAreaService().seedOres(golem.data(), radius, this.ctx.oreTable());

        if (blocksLeft > 0 && golem.data().energy() > 0 && this.ctx.chestService().hasSpace(golem.data())) {
            Location home = this.ctx.workAreaService().homeLocation(golem.data());
            Location next = home == null ? null : findExistingOre(golem, home);
            if (next == null && home != null) {
                int max = Math.max(1, this.ctx.settings().maxActiveOres);
                if (this.ctx.workAreaService().countOres(golem.data(), radius, this.ctx.oreTable()) < max) {
                    next = transformNearest(golem, home);
                }
            }
            if (next != null) {
                golem.targetOre(next);
                golem.oreMaterial(next.getBlock().getType());
                golem.state(MinerState.MOVING_TO_ORE);
                this.ctx.walkTowards(copper, next.clone().add(0.5D, 1.0D, 0.5D), golem);
                return;
            }
        }

        golem.blocksLeftThisTrip(0);
        golem.state(MinerState.MOVING_TO_CHEST);
        if (!this.ctx.chestLink().isLinked(golem.data())) {
            Location chestStand = this.ctx.chestService().chestStandLocation(golem.data());
            if (chestStand != null) {
                this.ctx.walkTowards(copper, chestStand, golem);
            }
        }
    }

    public Location findExistingOre(ActiveGolem golem, Location home) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        World world = home.getWorld();
        int baseX = (int) Math.floor(golem.data().homeX());
        int baseY = (int) Math.floor(golem.data().homeY());
        int baseZ = (int) Math.floor(golem.data().homeZ());
        Location golemLoc = this.ctx.resolveEntity(golem.data()) instanceof CopperGolem c ? c.getLocation() : home;

        Location bestFar = null;
        double bestFarDist = Double.MAX_VALUE;
        Location bestAny = null;
        double bestAnyDist = Double.MAX_VALUE;

        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Block block = world.getBlockAt(baseX + x, baseY, baseZ + z);
                if (this.ctx.chestService().isChestColumn(golem.data(), block)) {
                    continue;
                }
                if (!this.ctx.oreTable().isOre(block.getType())) {
                    continue;
                }
                Location stand = block.getLocation().add(0.5D, 1.0D, 0.5D);
                double dist = GolemMovement.horizontalDistanceSquared(golemLoc, stand);
                if (dist < bestAnyDist) {
                    bestAnyDist = dist;
                    bestAny = block.getLocation();
                }
                if (dist >= 2.25D && dist < bestFarDist) {
                    bestFarDist = dist;
                    bestFar = block.getLocation();
                }
            }
        }
        return bestFar != null ? bestFar : bestAny;
    }

    public Location transformNearest(ActiveGolem golem, Location home) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        World world = home.getWorld();
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        int baseX = (int) Math.floor(golem.data().homeX());
        int baseY = (int) Math.floor(golem.data().homeY());
        int baseZ = (int) Math.floor(golem.data().homeZ());
        Location golemLoc = this.ctx.resolveEntity(golem.data()) instanceof CopperGolem c ? c.getLocation() : home;
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Block block = world.getBlockAt(baseX + x, baseY, baseZ + z);
                if (SoulChestService.isChestLike(block.getType()) || this.ctx.chestService().isChestColumn(golem.data(), block)) {
                    continue;
                }
                if (!this.ctx.oreTable().isTransformable(block.getType())) {
                    continue;
                }
                Location stand = block.getLocation().add(0.5D, 1.0D, 0.5D);
                double dist = GolemMovement.horizontalDistanceSquared(golemLoc, stand);
                if (dist < 1.0D) {
                    continue;
                }
                if (dist < bestDist) {
                    bestDist = dist;
                    best = block.getLocation();
                }
            }
        }
        if (best == null) {
            return null;
        }
        Material ore = this.ctx.oreTable().rollOre();
        this.ctx.workAreaService().placeOre(best.getBlock(), golem.data().id(), ore);
        return best;
    }
}
