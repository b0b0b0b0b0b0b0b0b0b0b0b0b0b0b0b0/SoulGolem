package bm.b0b0b0.SoulGolem.service.miner;

import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemAiMode;
import bm.b0b0b0.SoulGolem.service.GolemControlService;
import bm.b0b0b0.SoulGolem.service.GolemFenceWork;
import bm.b0b0b0.SoulGolem.service.GolemGateWatch;
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

public final class MinerSupportWork {

    private final MinerContext ctx;
    private final MinerCarried carried;

    public MinerSupportWork(MinerContext ctx, MinerCarried carried) {
        this.ctx = ctx;
        this.carried = carried;
    }

    public void continueShelter(ActiveGolem golem, CopperGolem copper) {
        applyShelterPhase(golem, copper, this.ctx.rainShelter().tick(
                golem,
                copper,
                this.ctx.settings().miner.rainShelter
        ));
    }

    public void continueCloseGate(ActiveGolem golem, CopperGolem copper) {
        GolemGateWatch.Phase phase = this.ctx.gateWatch().tickClose(golem, copper);
        switch (phase) {
            case MOVING -> golem.state(MinerState.MOVING_TO_CLOSE_GATE);
            case CLOSING, DONE, IDLE -> {
                if (tryResumePausedSeatRest(golem)) {
                    return;
                }
                if (phase != GolemGateWatch.Phase.IDLE
                        && this.ctx.settings().miner.rainShelter
                        && this.ctx.rainShelter().shouldSeekShelter(golem, copper, true)) {
                    golem.clearFetchFlags();
                    golem.state(MinerState.MOVING_TO_SHELTER);
                    continueShelter(golem, copper);
                } else {
                    golem.state(MinerState.IDLE);
                }
            }
        }
    }

    private boolean tryResumePausedSeatRest(ActiveGolem golem) {
        if (!golem.resumeSeatRest()) {
            return false;
        }
        if (golem.restTicksLeft() <= 0L || !this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.resumeSeatRest(false);
            return false;
        }
        golem.clearFetchFlags();
        golem.targetCrop(null);
        golem.state(MinerState.MOVING_TO_SEAT);
        return true;
    }

    private void applyShelterPhase(ActiveGolem golem, CopperGolem copper, GolemRainShelterWork.Phase phase) {
        switch (phase) {
            case MOVING -> golem.state(MinerState.MOVING_TO_SHELTER);
            case BUILDING -> golem.state(MinerState.BUILDING_SHELTER);
            case SHELTERING -> golem.state(MinerState.SHELTERING);
            case DISABLED, UNAVAILABLE, DONE -> {
                GolemAiMode.enable(
                        this.ctx.plugin(),
                        copper,
                        this.ctx.registry(),
                        this.ctx.keys()
                );
                golem.state(MinerState.IDLE);
            }
        }
    }

    public boolean tryGoToSeat(ActiveGolem golem, int radius, Settings.Miner miner) {
        if (this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.clearFetchFlags();
            golem.targetCrop(null);
            golem.data().lastActionAt(System.currentTimeMillis());
            golem.state(MinerState.MOVING_TO_SEAT);
            return true;
        }
        return tryStartSeatJob(golem, radius, miner);
    }

    public boolean tryStartSeatJob(ActiveGolem golem, int radius, Settings.Miner miner) {
        if (!miner.placeSeat || this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            return false;
        }
        if (this.ctx.farmAreaService().findSeatSpot(golem.data(), radius) == null) {
            return false;
        }
        Material stairs = MinerCarried.carriedStairs(golem);
        if (stairs != null) {
            golem.clearFetchFlags();
            Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
            golem.targetCrop(spot.getLocation());
            golem.state(MinerState.MOVING_TO_SEAT);
            return true;
        }
        if (this.ctx.chestService().findStairsInChest(golem.data()) == null) {
            return false;
        }
        golem.clearFetchFlags();
        golem.fetchingSeat(true);
        golem.state(MinerState.MOVING_TO_CHEST);
        return true;
    }

    public void continueClear(ActiveGolem golem, CopperGolem copper) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (!golem.carried().isEmpty() && !this.ctx.chestService().hasSpace(golem.data())) {
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        Location target = golem.targetCrop();
        Block junk = target == null ? null : target.getBlock();
        if (junk == null || junk.getType().isAir() || !isClearableJunk(junk)) {
            List<Block> list = this.ctx.farmAreaService().minerJunkToClear(golem.data(), radius, this.ctx.oreTable());
            if (list.isEmpty()) {
                golem.targetCrop(null);
                if (!golem.carried().isEmpty()) {
                    golem.state(MinerState.MOVING_TO_CHEST);
                    return;
                }
                golem.state(MinerState.IDLE);
                return;
            }
            junk = pickNearest(copper.getLocation(), list);
            golem.targetCrop(junk.getLocation());
        }
        Location stand = this.ctx.farmAreaService().standForClear(junk);
        if (stand == null) {
            golem.targetCrop(null);
            return;
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.state(MinerState.MOVING_TO_CLEAR);
            this.ctx.walkTowards(copper, stand, golem.data());
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.state(MinerState.CLEARING);
        if (FarmAreaService.isVegetation(junk.getType()) || junk.getType() == Material.SNOW) {
            FarmAreaService.clearVegetation(junk);
            if (junk.getType() == Material.SNOW) {
                junk.setType(Material.AIR, false);
            }
        } else {
            Material shovel = this.ctx.resolveShovel();
            Collection<ItemStack> drops = junk.getDrops(new ItemStack(shovel));
            junk.setType(Material.AIR, false);
            for (ItemStack drop : drops) {
                golem.carry(drop);
            }
        }
        golem.targetCrop(null);
        golem.markDirty();
        List<Block> left = this.ctx.farmAreaService().minerJunkToClear(golem.data(), radius, this.ctx.oreTable());
        if (!left.isEmpty() && (golem.carried().isEmpty() || this.ctx.chestService().hasSpace(golem.data()))) {
            golem.targetCrop(pickNearest(copper.getLocation(), left).getLocation());
            golem.state(MinerState.MOVING_TO_CLEAR);
            return;
        }
        if (!golem.carried().isEmpty()) {
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        golem.state(MinerState.IDLE);
    }

    public void continueTorch(ActiveGolem golem, CopperGolem copper) {
        applyTorchPhase(golem, this.ctx.torchWork().tick(
                golem,
                copper,
                this.ctx.resolveTorch(),
                this.ctx.settings().miner.maxTorches
        ));
    }

    private void applyTorchPhase(ActiveGolem golem, GolemTorchWork.Phase phase) {
        switch (phase) {
            case MOVING -> golem.state(MinerState.MOVING_TO_TORCH);
            case PLACING -> golem.state(MinerState.PLACING_TORCH);
            case FETCH -> golem.state(MinerState.MOVING_TO_CHEST);
            case DONE -> golem.state(MinerState.IDLE);
        }
    }

    public void continueSeat(ActiveGolem golem, CopperGolem copper) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());

        if (golem.state() == MinerState.SITTING
                && this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.resumeSeatRest(false);
            Settings.Miner miner = this.ctx.settings().miner;
            if (miner.collectGroundLoot && this.ctx.groundLoot().hasLoot(golem.data())) {
                golem.restTicksLeft(0L);
                this.ctx.seatWork().leaveBench(
                        golem,
                        copper,
                        this.ctx.plugin(),
                        this.ctx.registry(),
                        this.ctx.keys()
                );
                golem.state(MinerState.SEEKING);
                return;
            }
            this.ctx.seatWork().holdOnBench(golem, copper);
            if (golem.pauseAfterRest()) {
                GolemControlService.finishPauseAfterRest(golem, this.ctx.repository());
                return;
            }
            long left = golem.restTicksLeft() - Math.max(1L, this.ctx.settings().coordinatorPeriodTicks);
            golem.restTicksLeft(left);
            if (left > 0L) {
                return;
            }
            golem.restTicksLeft(0L);
            golem.data().lastActionAt(System.currentTimeMillis());
            this.ctx.seatWork().leaveBench(
                    golem,
                    copper,
                    this.ctx.plugin(),
                    this.ctx.registry(),
                    this.ctx.keys()
            );
            golem.restTicksLeft(0L);
            golem.state(MinerState.IDLE);
            return;
        }

        Material stairs = MinerCarried.carriedStairs(golem);
        if (stairs != null && !this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            GolemSeatWork.Phase phase = this.ctx.seatWork().placeCarriedSeat(golem, copper, stairs, radius);
            applySeatPhase(golem, phase);
            if (phase == GolemSeatWork.Phase.PLACING) {
                golem.restTicksLeft(Math.max(1L, Math.round(this.ctx.settings().miner.seatRestTicks
                        * this.ctx.settings().moodRestMultiplierAt(this.ctx.moodScore(golem.data())))));
            }
            return;
        }

        if (!this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            if (tryStartSeatJob(golem, radius, this.ctx.settings().miner)) {
                return;
            }
            golem.state(MinerState.IDLE);
            return;
        }

        GolemSeatWork.Phase phase = this.ctx.seatWork().sitOnBench(golem, copper);
        if (phase == GolemSeatWork.Phase.UNAVAILABLE) {
            golem.state(MinerState.IDLE);
            return;
        }
        if (phase == GolemSeatWork.Phase.MOVING) {
            golem.state(MinerState.MOVING_TO_SEAT);
            return;
        }

        if (golem.restTicksLeft() <= 0L) {
            int mood = this.ctx.moodScore(golem.data());
            golem.restTicksLeft(Math.max(1L, Math.round(this.ctx.settings().miner.seatRestTicks
                    * this.ctx.settings().moodRestMultiplierAt(mood))));
        }
        golem.resumeSeatRest(false);
        golem.state(MinerState.SITTING);
        if (golem.pauseAfterRest()) {
            GolemControlService.finishPauseAfterRest(golem, this.ctx.repository());
            return;
        }
        long left = golem.restTicksLeft() - Math.max(1L, this.ctx.settings().coordinatorPeriodTicks);
        golem.restTicksLeft(left);
        if (left > 0L) {
            return;
        }
        golem.restTicksLeft(0L);
        golem.data().lastActionAt(System.currentTimeMillis());
        this.ctx.seatWork().leaveBench(
                golem,
                copper,
                this.ctx.plugin(),
                this.ctx.registry(),
                this.ctx.keys()
        );
        golem.state(MinerState.IDLE);
    }

    private void applySeatPhase(ActiveGolem golem, GolemSeatWork.Phase phase) {
        switch (phase) {
            case MOVING, PLACING -> golem.state(MinerState.MOVING_TO_SEAT);
            case SITTING -> golem.state(MinerState.SITTING);
            case UNAVAILABLE, DONE -> golem.state(MinerState.IDLE);
        }
    }

    public void placeCarriedSeat(ActiveGolem golem, CopperGolem copper, Material carriedStairs, int radius) {
        GolemSeatWork.Phase phase = this.ctx.seatWork().placeCarriedSeat(golem, copper, carriedStairs, radius);
        applySeatPhase(golem, phase);
        if (phase == GolemSeatWork.Phase.PLACING) {
            golem.restTicksLeft(Math.max(1L, Math.round(this.ctx.settings().miner.seatRestTicks
                    * this.ctx.settings().moodRestMultiplierAt(this.ctx.moodScore(golem.data())))));
        }
    }

    public boolean tryStartFenceJob(ActiveGolem golem, int radius, Settings.Miner miner) {
        GolemFenceWork.Phase phase = this.ctx.fenceWork().tryStart(golem, miner.placeFence);
        if (phase == GolemFenceWork.Phase.DISABLED || phase == GolemFenceWork.Phase.PAUSE) {
            return false;
        }
        applyFencePhase(golem, phase);
        return true;
    }

    public void continueFence(ActiveGolem golem, CopperGolem copper) {
        applyFencePhase(golem, this.ctx.fenceWork().tick(
                golem,
                copper,
                this.ctx.settings().miner.placeFence,
                this.ctx.settings().miner.fencesPerTrip,
                null
        ));
    }

    public void takeFenceFromChest(ActiveGolem golem) {
        applyFencePhase(golem, this.ctx.fenceWork().takeFromChest(
                golem,
                this.ctx.settings().miner.fencesPerTrip
        ));
    }

    private void applyFencePhase(ActiveGolem golem, GolemFenceWork.Phase phase) {
        switch (phase) {
            case MOVING_CLEAR -> golem.state(MinerState.MOVING_TO_FENCE_CLEAR);
            case CLEARING -> golem.state(MinerState.CLEARING_FENCE);
            case MOVING_FENCE -> golem.state(MinerState.MOVING_TO_FENCE);
            case PLACING_FENCE -> golem.state(MinerState.PLACING_FENCE);
            case MOVING_GATE -> golem.state(MinerState.MOVING_TO_GATE);
            case PLACING_GATE -> golem.state(MinerState.PLACING_GATE);
            case FETCH_FENCE, FETCH_GATE -> golem.state(MinerState.MOVING_TO_CHEST);
            case DISABLED, DONE, PAUSE -> golem.state(MinerState.IDLE);
        }
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
}
