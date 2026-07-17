package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemAiMode;
import bm.b0b0b0.SoulGolem.service.GolemControlService;
import bm.b0b0b0.SoulGolem.service.GolemFenceWork;
import bm.b0b0b0.SoulGolem.service.GolemGateWatch;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemGroundLootWork;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.GolemRainShelterWork;
import bm.b0b0b0.SoulGolem.service.GolemSeatWork;
import bm.b0b0b0.SoulGolem.service.GolemTorchWork;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class FarmerSupportWork {

    private final FarmerContext ctx;
    private final FarmerCarried carried;
    private FarmerCycle cycle;

    public FarmerSupportWork(FarmerContext ctx, FarmerCarried carried) {
        this.ctx = ctx;
        this.carried = carried;
    }

    public void wire(FarmerCycle cycle) {
        this.cycle = cycle;
    }

    public void continueClear(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        if (this.cycle.tryPrioritizeFence(golem)) {
            return;
        }
        this.ctx.farmAreaService().ensureWater(golem.data());
        if (!golem.carried().isEmpty() && !this.ctx.chestService().hasSpace(golem.data())) {
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        Location target = golem.targetCrop();
        Block junk = target == null ? null : target.getBlock();
        if (junk == null || junk.getType().isAir() || !isClearableJunk(junk) || isBorderBlock(junk)) {
            List<Block> list = this.ctx.farmAreaService().weedsToClear(golem.data(), radius);
            list.removeIf(this::isBorderBlock);
            if (list.isEmpty()) {
                this.cycle.resumeAfterClear(golem);
                return;
            }
            junk = pickNearestJunk(copper.getLocation(), list);
            golem.targetCrop(junk.getLocation());
        }
        Location stand = this.ctx.farmAreaService().standForClear(junk);
        if (stand == null) {
            golem.targetCrop(null);
            return;
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.CLEARING);
        GolemGaze.faceBlock(golem, junk);
        if (FarmAreaService.isVegetation(junk.getType())) {
            FarmAreaService.clearVegetation(junk);
        } else {
            Material shovel = this.ctx.resolveShovel();
            Collection<ItemStack> drops = junk.getDrops(new ItemStack(shovel));
            junk.setType(Material.AIR, false);
            for (ItemStack drop : drops) {
                golem.carry(drop);
            }
        }
        this.ctx.drainFarmEnergy(golem);
        golem.targetCrop(null);
        golem.markDirty();
        List<Block> left = this.ctx.farmAreaService().weedsToClear(golem.data(), radius);
        left.removeIf(this::isBorderBlock);
        if (!left.isEmpty() && (golem.carried().isEmpty() || this.ctx.chestService().hasSpace(golem.data()))) {
            Block next = pickNearestJunk(copper.getLocation(), left);
            golem.targetCrop(next.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            return;
        }
        this.cycle.resumeAfterClear(golem);
    }

    public void continueTorch(ActiveGolem golem, CopperGolem copper) {
        applyTorchPhase(golem, this.ctx.torchWork().tick(
                golem,
                copper,
                this.ctx.resolveTorch(),
                this.ctx.settings().farmer.maxTorches
        ));
    }

    public void takeTorchesFromChest(ActiveGolem golem) {
        applyTorchPhase(golem, this.ctx.torchWork().takeFromChest(
                golem,
                this.ctx.resolveTorch(),
                this.ctx.settings().farmer.torchesPerTrip,
                this.ctx.settings().farmer.maxTorches
        ));
    }

    private void applyTorchPhase(ActiveGolem golem, GolemTorchWork.Phase phase) {
        switch (phase) {
            case MOVING -> golem.farmerState(FarmerState.MOVING_TO_TORCH);
            case PLACING -> golem.farmerState(FarmerState.PLACING_TORCH);
            case FETCH -> golem.farmerState(FarmerState.MOVING_TO_CHEST);
            case DONE -> golem.farmerState(FarmerState.WAIT_GROWTH);
        }
    }

    public void continueFence(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        applyFencePhase(golem, this.ctx.fenceWork().tick(
                golem,
                copper,
                this.ctx.settings().farmer.placeFence,
                this.ctx.settings().farmer.fencesPerTrip,
                this.ctx::drainFarmEnergy
        ));
    }

    public void takeFenceFromChest(ActiveGolem golem) {
        applyFencePhase(golem, this.ctx.fenceWork().takeFromChest(
                golem,
                this.ctx.settings().farmer.fencesPerTrip
        ));
    }

    private void applyFencePhase(ActiveGolem golem, GolemFenceWork.Phase phase) {
        switch (phase) {
            case MOVING_CLEAR -> golem.farmerState(FarmerState.MOVING_TO_FENCE_CLEAR);
            case CLEARING -> golem.farmerState(FarmerState.CLEARING_FENCE);
            case MOVING_FENCE -> golem.farmerState(FarmerState.MOVING_TO_FENCE);
            case PLACING_FENCE -> golem.farmerState(FarmerState.PLACING_FENCE);
            case MOVING_GATE -> golem.farmerState(FarmerState.MOVING_TO_GATE);
            case PLACING_GATE -> golem.farmerState(FarmerState.PLACING_GATE);
            case FETCH_FENCE, FETCH_GATE -> golem.farmerState(FarmerState.MOVING_TO_CHEST);
            case PAUSE, DISABLED, DONE -> {
                golem.wanderTarget(null);
                this.cycle.resumeAfterClear(golem);
            }
        }
    }

    public void continueShelter(ActiveGolem golem, CopperGolem copper) {
        applyShelterPhase(golem, copper, this.ctx.rainShelter().tick(
                golem,
                copper,
                this.ctx.settings().farmer.rainShelter
        ));
    }

    public void continueCloseGate(ActiveGolem golem, CopperGolem copper) {
        GolemGateWatch.Phase phase = this.ctx.gateWatch().tickClose(golem, copper);
        switch (phase) {
            case MOVING -> golem.farmerState(FarmerState.MOVING_TO_CLOSE_GATE);
            case CLOSING, DONE -> {
                golem.wanderTarget(null);
                if (this.ctx.settings().farmer.rainShelter
                        && this.ctx.rainShelter().shouldSeekShelter(golem, copper, true)) {
                    golem.clearFetchFlags();
                    golem.farmerState(FarmerState.MOVING_TO_SHELTER);
                    continueShelter(golem, copper);
                } else {
                    this.cycle.resumeAfterClear(golem);
                }
            }
            case IDLE -> golem.farmerState(FarmerState.WAITING_SEEDS);
        }
    }

    private void applyShelterPhase(ActiveGolem golem, CopperGolem copper, GolemRainShelterWork.Phase phase) {
        switch (phase) {
            case MOVING -> golem.farmerState(FarmerState.MOVING_TO_SHELTER);
            case BUILDING -> golem.farmerState(FarmerState.BUILDING_SHELTER);
            case SHELTERING -> golem.farmerState(FarmerState.SHELTERING);
            case DISABLED, UNAVAILABLE, DONE -> {
                golem.wanderTarget(null);
                if (this.ctx.rainShelter().isStorming(golem.data())) {
                    golem.farmerState(FarmerState.SHELTERING);
                } else {
                    GolemAiMode.enable(
                            this.ctx.plugin(),
                            copper,
                            this.ctx.registry(),
                            this.ctx.keys()
                    );
                    this.cycle.resumeAfterClear(golem);
                }
            }
        }
    }

    public void continueSeat(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }

        if (golem.farmerState() == FarmerState.SITTING
                && this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            if (shouldLeaveSeat(golem)) {
                golem.wanderTarget(null);
                this.ctx.seatWork().leaveBench(
                        golem,
                        copper,
                        this.ctx.plugin(),
                        this.ctx.registry(),
                        this.ctx.keys()
                );
                if (this.cycle.tryPrioritizeFence(golem)) {
                    return;
                }
                if (this.cycle.assignNextJob(golem)) {
                    return;
                }
                golem.farmerState(FarmerState.WAIT_GROWTH);
                return;
            }
            this.ctx.seatWork().holdOnBench(golem, copper);
            return;
        }

        if (!this.ctx.farmAreaService().ensureInsideBorder(golem, copper, this.ctx.movement())) {
            return;
        }

        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        Material carriedStairs = FarmerCarried.carriedStairs(golem);
        if (carriedStairs != null && !this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            applySeatPhase(golem, this.ctx.seatWork().placeCarriedSeat(golem, copper, carriedStairs, radius));
            return;
        }

        if (!this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            if (this.cycle.assignNextJob(golem)) {
                return;
            }
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }

        applySeatPhase(golem, this.ctx.seatWork().sitOnBench(golem, copper));
    }

    private boolean shouldLeaveSeat(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (!this.ctx.farmAreaService().matureCrops(golem.data(), radius, this.ctx.enabledCrops()).isEmpty()) {
            return true;
        }
        if (!this.ctx.farmAreaService().fieldWeeds(golem.data(), radius).isEmpty()) {
            return true;
        }
        if (!this.ctx.farmAreaService().tillableSoil(golem.data(), radius).isEmpty()) {
            return true;
        }
        if (this.ctx.hasPlantWork(golem)) {
            return true;
        }
        Settings.Farmer farmer = this.ctx.settings().farmer;
        if (farmer.useBoneMeal && this.ctx.hasBoneMealWork(golem)) {
            return true;
        }
        if (farmer.craftBread
                && golem.data().hasCraftStation()
                && this.ctx.chestService().countItem(golem.data(), Material.WHEAT) >= 3
                && this.ctx.chestService().hasSpace(golem.data())) {
            return true;
        }
        if (canStartTorchJob(golem, radius, farmer)) {
            return true;
        }
        return farmer.placeFence
                && this.ctx.farmAreaService().needsOuterFenceWork(golem.data(), radius);
    }

    private boolean canStartTorchJob(ActiveGolem golem, int radius, Settings.Farmer farmer) {
        if (!farmer.placeTorches) {
            return false;
        }
        Material torch = this.ctx.resolveTorch();
        return this.ctx.chestService().countItem(golem.data(), torch) > 0
                && !this.ctx.farmAreaService().perimeterTorchSpots(golem.data(), radius).isEmpty();
    }

    private void applySeatPhase(ActiveGolem golem, GolemSeatWork.Phase phase) {
        switch (phase) {
            case MOVING, PLACING -> golem.farmerState(FarmerState.MOVING_TO_SEAT);
            case SITTING -> {
                golem.farmerState(FarmerState.SITTING);
                if (golem.pauseAfterRest()) {
                    GolemControlService.finishPauseAfterRest(golem, this.ctx.repository());
                }
            }
            case UNAVAILABLE, DONE -> golem.farmerState(FarmerState.WAIT_GROWTH);
        }
    }

    public void placeCarriedSeat(ActiveGolem golem, CopperGolem copper, Material carriedStairs, int radius) {
        applySeatPhase(golem, this.ctx.seatWork().placeCarriedSeat(golem, copper, carriedStairs, radius));
    }

    public void takeSeatFromChest(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            return;
        }
        Material already = FarmerCarried.carriedStairs(golem);
        if (already != null) {
            Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
            if (spot == null) {
                this.carried.returnCarriedToChest(golem, already);
                golem.clearFetchFlags();
                golem.farmerState(FarmerState.WAIT_GROWTH);
                return;
            }
            golem.clearFetchFlags();
            golem.targetCrop(spot.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            return;
        }
        Material stairs = this.ctx.chestService().findStairsInChest(golem.data());
        Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
        if (stairs == null || spot == null) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        if (!this.ctx.chestService().takeItem(golem.data(), stairs, 1)) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(stairs, 1));
        golem.clearFetchFlags();
        golem.targetCrop(spot.getLocation());
        golem.farmerState(FarmerState.MOVING_TO_SEAT);
        golem.markDirty();
    }

    public boolean isBorderBlock(Block block) {
        return this.ctx.farmAreaService().isBorderMaterial(block.getType());
    }

    public static Block pickNearestJunk(Location from, List<Block> list) {
        Block best = list.get(0);
        double bestDist = Double.MAX_VALUE;
        for (Block block : list) {
            double dx = block.getX() + 0.5D - from.getX();
            double dz = block.getZ() + 0.5D - from.getZ();
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = block;
            }
        }
        return best;
    }

    public static boolean isClearableJunk(Block block) {
        Material type = block.getType();
        if (type.isAir() || SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE
                || type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER) {
            return false;
        }
        if (org.bukkit.Tag.STAIRS.isTagged(type) || FarmAreaService.isAnyCrop(type)) {
            return false;
        }
        if (org.bukkit.Tag.FENCES.isTagged(type) || org.bukkit.Tag.FENCE_GATES.isTagged(type)) {
            return false;
        }
        if (type == Material.TORCH || type == Material.WALL_TORCH
                || type == Material.SOUL_TORCH || type == Material.SOUL_WALL_TORCH) {
            return false;
        }
        return type.isSolid() || type == Material.SNOW || FarmAreaService.isVegetation(type);
    }
}
