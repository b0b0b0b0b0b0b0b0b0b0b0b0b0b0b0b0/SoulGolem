package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;

public final class GolemSupportWork {

    private final GolemWorkContext ctx;
    private final SupportStateBridge bridge;

    public GolemSupportWork(GolemWorkContext ctx, SupportStateBridge bridge) {
        this.ctx = ctx;
        this.bridge = bridge;
    }

    public SupportStateBridge bridge() {
        return this.bridge;
    }

    public void continueTorch(ActiveGolem golem, CopperGolem copper) {
        applyTorchPhase(golem, this.ctx.torchWork().tick(
                golem,
                copper,
                this.ctx.resolveTorch(),
                this.ctx.settings().yard.maxTorches
        ));
    }

    public void takeTorchesFromChest(ActiveGolem golem) {
        GolemSettings.Yard yard = this.ctx.settings().yard;
        applyTorchPhase(golem, this.ctx.torchWork().takeFromChest(
                golem,
                this.ctx.resolveTorch(),
                yard.torchesPerTrip,
                yard.maxTorches
        ));
    }

    private void applyTorchPhase(ActiveGolem golem, GolemTorchWork.Phase phase) {
        switch (phase) {
            case MOVING -> this.bridge.toTorch(golem);
            case PLACING -> this.bridge.placingTorch(golem);
            case FETCH -> this.bridge.toChest(golem);
            case DONE -> this.bridge.afterSupportIdle(golem);
        }
    }

    public void continueFence(ActiveGolem golem, CopperGolem copper, Consumer<ActiveGolem> energyDrain) {
        GolemSettings.Yard yard = this.ctx.settings().yard;
        applyFencePhase(golem, this.ctx.fenceWork().tick(
                golem,
                copper,
                yard.placeFence,
                yard.fencesPerTrip,
                energyDrain
        ));
    }

    public void takeFenceFromChest(ActiveGolem golem) {
        applyFencePhase(golem, this.ctx.fenceWork().takeFromChest(
                golem,
                this.ctx.settings().yard.fencesPerTrip
        ));
    }

    public boolean tryStartFenceJob(ActiveGolem golem) {
        GolemFenceWork.Phase phase = this.ctx.fenceWork().tryStart(golem, this.ctx.settings().yard.placeFence);
        if (phase == GolemFenceWork.Phase.DISABLED || phase == GolemFenceWork.Phase.PAUSE) {
            return false;
        }
        applyFencePhase(golem, phase);
        return true;
    }

    private void applyFencePhase(ActiveGolem golem, GolemFenceWork.Phase phase) {
        switch (phase) {
            case MOVING_CLEAR -> this.bridge.toFenceClear(golem);
            case CLEARING -> this.bridge.clearingFence(golem);
            case MOVING_FENCE -> this.bridge.toFence(golem);
            case PLACING_FENCE -> this.bridge.placingFence(golem);
            case MOVING_GATE -> this.bridge.toGate(golem);
            case PLACING_GATE -> this.bridge.placingGate(golem);
            case FETCH_FENCE, FETCH_GATE -> this.bridge.toChest(golem);
            case PAUSE, DISABLED, DONE -> this.bridge.afterSupportIdle(golem);
        }
    }

    public void continueShelter(ActiveGolem golem, CopperGolem copper) {
        applyShelterPhase(golem, copper, this.ctx.rainShelter().tick(
                golem,
                copper,
                this.ctx.settings().yard.rainShelter
        ));
    }

    private void applyShelterPhase(ActiveGolem golem, CopperGolem copper, GolemRainShelterWork.Phase phase) {
        switch (phase) {
            case MOVING -> this.bridge.toShelter(golem);
            case BUILDING -> this.bridge.buildingShelter(golem);
            case SHELTERING -> this.bridge.sheltering(golem);
            case DISABLED, UNAVAILABLE, DONE -> this.bridge.afterShelterDone(golem, copper);
        }
    }

    public void continueCloseGate(ActiveGolem golem, CopperGolem copper) {
        GolemGateWatch.Phase phase = this.ctx.gateWatch().tickClose(golem, copper);
        switch (phase) {
            case MOVING -> this.bridge.toCloseGate(golem);
            case IDLE -> {
                if (this.bridge.tryResumePausedSeat(golem)) {
                    return;
                }
                this.bridge.afterCloseGateIdle(golem);
            }
            case CLOSING, DONE -> {
                if (this.bridge.tryResumePausedSeat(golem)) {
                    return;
                }
                if (this.ctx.settings().yard.rainShelter
                        && this.ctx.rainShelter().shouldSeekShelter(golem, copper, true)) {
                    golem.clearFetchFlags();
                    this.bridge.toShelter(golem);
                    continueShelter(golem, copper);
                } else {
                    this.bridge.afterSupportIdle(golem);
                }
            }
        }
    }

    public boolean tryStartSeatJob(ActiveGolem golem, int radius) {
        if (!this.ctx.settings().yard.placeSeat || this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            return false;
        }
        if (this.ctx.farmAreaService().findSeatSpot(golem.data(), radius) == null) {
            return false;
        }
        Material stairs = GolemCarried.carriedStairs(golem);
        if (stairs != null) {
            golem.clearFetchFlags();
            Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
            golem.targetCrop(spot.getLocation());
            this.bridge.toSeat(golem);
            return true;
        }
        if (this.ctx.chestService().findStairsInChest(golem.data()) == null) {
            return false;
        }
        golem.clearFetchFlags();
        golem.fetchingSeat(true);
        this.bridge.toChest(golem);
        return true;
    }

    public boolean tryGoToSeat(ActiveGolem golem, int radius) {
        if (this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.clearFetchFlags();
            golem.targetCrop(null);
            golem.data().lastActionAt(System.currentTimeMillis());
            this.bridge.toSeat(golem);
            return true;
        }
        return tryStartSeatJob(golem, radius);
    }

    public void takeSeatFromChest(ActiveGolem golem, Runnable onNoSeat) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.clearFetchFlags();
            this.bridge.toSeat(golem);
            return;
        }
        Material already = GolemCarried.carriedStairs(golem);
        if (already != null) {
            Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
            if (spot == null) {
                GolemCarried.returnToChest(this.ctx.chestService(), golem, already);
                golem.clearFetchFlags();
                onNoSeat.run();
                return;
            }
            golem.clearFetchFlags();
            golem.targetCrop(spot.getLocation());
            this.bridge.toSeat(golem);
            return;
        }
        Material stairs = this.ctx.chestService().findStairsInChest(golem.data());
        Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
        if (stairs == null || spot == null) {
            golem.clearFetchFlags();
            onNoSeat.run();
            return;
        }
        if (!this.ctx.chestService().takeItem(golem.data(), stairs, 1)) {
            golem.clearFetchFlags();
            onNoSeat.run();
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(stairs, 1));
        golem.clearFetchFlags();
        golem.targetCrop(spot.getLocation());
        this.bridge.toSeat(golem);
        golem.markDirty();
    }

    public void placeCarriedSeat(ActiveGolem golem, CopperGolem copper, Material carriedStairs, int radius) {
        applySeatPhase(golem, this.ctx.seatWork().placeCarriedSeat(golem, copper, carriedStairs, radius));
    }

    public void applySeatPhase(ActiveGolem golem, GolemSeatWork.Phase phase) {
        switch (phase) {
            case MOVING, PLACING -> this.bridge.toSeat(golem);
            case SITTING -> this.bridge.sitting(golem);
            case UNAVAILABLE, DONE -> this.bridge.afterSupportIdle(golem);
        }
    }

    public static Block pickNearest(Location from, List<Block> list) {
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
        if (type.isAir() || SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE || type == Material.COMPOSTER
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
